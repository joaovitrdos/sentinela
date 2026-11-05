package br.com.sentinela.service;

import br.com.sentinela.dto.request.PropertyAlertSettingsDto;
import br.com.sentinela.dto.response.PropertyAlertResponseDto;
import br.com.sentinela.dto.response.PropertyMonitoringDto;
import br.com.sentinela.dto.response.RiverLevelResponseDto;
import br.com.sentinela.entity.Property;
import br.com.sentinela.entity.PropertyAlert;
import br.com.sentinela.entity.PropertyMember;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.repository.PropertyAlertRepository;
import br.com.sentinela.repository.PropertyMemberRepository;
import br.com.sentinela.repository.PropertyRepository;
import br.com.sentinela.service.InmetService.InmetWarning;
import br.com.sentinela.util.LogUtil;
import br.com.sentinela.util.WeatherAttribution;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service @RequiredArgsConstructor
public class PropertyAlertService {
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final double EARTH_RADIUS_KM = 6_371;
    private static final double MAX_RIVER_READING_AGE_HOURS = 48;
    private static final int NDVI_CLOUD_LIMIT = 40;

    private final PropertyRepository properties;
    private final PropertyAlertRepository alerts;
    private final PropertyMemberRepository members;
    private final PropertyService propertyService;
    private final PropertyForecastService forecasts;
    private final PlanService plans;
    private final WeatherApiUsageService weatherUsage;
    private final InmetService inmet;
    private final ReferenceLevelService riverLevels;
    private final CopernicusService copernicus;
    private final EmailFactory emails;
    private final EmailSenderService sender;
    @Value("${sentinela.mail.enabled:true}") private boolean mailEnabled;

    record Candidate(String type, String key, String severity, String title, String message) {}

    @Transactional(readOnly = true)
    public List<PropertyAlertResponseDto> list(UUID userId, UUID propertyId) {
        propertyService.requireView(propertyId, userId);
        return alerts.findTop50ByProperty_PropertyIdOrderByCreatedAtDesc(propertyId).stream()
                .map(alert -> new PropertyAlertResponseDto(alert.getAlertId(), alert.getType(), alert.getSeverity(),
                        alert.getTitle(), alert.getMessage(), disclaimer(alert.getType()), alert.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyAlertSettingsDto settings(UUID userId, UUID propertyId) {
        return settings(propertyService.requireView(propertyId, userId));
    }

    @Transactional
    public PropertyAlertSettingsDto updateSettings(UUID userId, UUID propertyId, PropertyAlertSettingsDto request) {
        Property property = propertyService.requireAdmin(propertyId, userId);
        property.setAlertEnabled(request.enabled());
        property.setAlertFrostTemperatureC(request.frostTemperatureC());
        property.setAlertHeavyRainMm(request.heavyRainMm());
        property.setAlertNdviDrop(request.ndviDrop());
        property.setAlertRiverRadiusKm(request.riverRadiusKm());
        return settings(property);
    }

    private PropertyAlertSettingsDto settings(Property property) {
        return new PropertyAlertSettingsDto(Boolean.TRUE.equals(property.getAlertEnabled()), property.getAlertFrostTemperatureC(),
                property.getAlertHeavyRainMm(), property.getAlertNdviDrop(), property.getAlertRiverRadiusKm());
    }

    /** Cada propriedade consome uma chamada da WeatherAPI por ciclo; 6 horas cabem na cota gratuita de 100 mil por mês. */
    @Scheduled(initialDelayString = "${sentinela.alerts.initial-delay:PT5M}", fixedDelayString = "${sentinela.alerts.scan-delay:PT6H}")
    public void scanWeatherAndRivers() {
        List<Property> monitored = monitored(Feature.ALERTS);
        if (monitored.isEmpty()) return;
        List<InmetWarning> warnings = attempt("INMET", inmet::activeWarnings);
        List<RiverLevelResponseDto> levels = attempt("níveis de rio", riverLevels::getRiverLevels);
        boolean forecastAllowed = weatherUsage.automationAllowed();
        if (!forecastAllowed) LogUtil.warn("Alertas de previsão pausados: cota mensal da WeatherAPI quase no fim");
        for (Property property : monitored) {
            List<Candidate> candidates = new ArrayList<>();
            if (forecastAllowed) {
                candidates.addAll(attempt("previsão da propriedade " + property.getPropertyId(),
                        () -> forecastAlerts(property, forecasts.forecast(property).forecast())));
            }
            candidates.addAll(inmetAlerts(property, warnings));
            candidates.addAll(riverAlerts(property, levels));
            candidates.forEach(candidate -> publish(property, candidate));
        }
    }

    /** Roda uma vez por dia porque as imagens de satélite mudam a cada poucos dias e a cota do Copernicus é limitada. */
    @Scheduled(cron = "${sentinela.alerts.ndvi-cron:0 30 6 * * *}", zone = "America/Sao_Paulo")
    public void scanVegetation() {
        for (Property property : monitored(Feature.NDVI)) {
            attempt("NDVI da propriedade " + property.getPropertyId(), () -> ndviAlerts(property, copernicus.monitor(
                    property.getPropertyId(), property.getUser().getUserId(), null, null, NDVI_CLOUD_LIMIT)))
                    .forEach(candidate -> publish(property, candidate));
        }
    }

    private List<Property> monitored(Feature feature) {
        return properties.findAllWithAlertsEnabled().stream().filter(property -> {
            UUID owner = property.getUser().getUserId();
            return plans.allows(owner, property.getPropertyId(), Feature.ALERTS) && plans.allows(owner, property.getPropertyId(), feature);
        }).toList();
    }

    private <T> List<T> attempt(String source, Supplier<List<T>> request) {
        try {
            return request.get();
        } catch (RuntimeException exception) {
            LogUtil.error("Falha ao consultar " + source + " para os alertas", exception);
            return List.of();
        }
    }

    static List<Candidate> forecastAlerts(Property property, JsonNode forecast) {
        List<Candidate> candidates = new ArrayList<>();
        for (JsonNode node : forecast.path("forecastday")) {
            String date = node.path("date").asText();
            String label = LocalDate.parse(date).format(DATE);
            double minimum = node.path("day").path("mintemp_c").asDouble();
            double rain = node.path("day").path("totalprecip_mm").asDouble();
            if (minimum <= property.getAlertFrostTemperatureC()) {
                candidates.add(new Candidate("FROST", "FROST:" + date, "PERIGO", "Risco de geada em " + label,
                        String.format(PT_BR, "Temperatura mínima prevista de %.1f °C em %s. Fonte: " + WeatherAttribution.SOURCE + ".", minimum, label)));
            }
            if (rain >= property.getAlertHeavyRainMm()) {
                candidates.add(new Candidate("HEAVY_RAIN", "HEAVY_RAIN:" + date, "ATENCAO", "Chuva forte em " + label,
                        String.format(PT_BR, "Previsão de %.1f mm de chuva em %s. Fonte: " + WeatherAttribution.SOURCE + ".", rain, label)));
            }
        }
        return candidates;
    }

    static List<Candidate> inmetAlerts(Property property, List<InmetWarning> warnings) {
        return warnings.stream().filter(warning -> warning.covers(property.getLatitude(), property.getLongitude()))
                .map(warning -> new Candidate("INMET", "INMET:" + warning.id(), "PERIGO",
                        limit("INMET: " + warning.event() + " (" + warning.severity() + ")", 160),
                        limit("De " + warning.startsAt() + " até " + warning.endsAt() + ". " + warning.risk(), 1000)))
                .toList();
    }

    /** A ANA informa a situação atual da cota, sem histórico: o alerta indica nível elevado, não tendência de subida. */
    static List<Candidate> riverAlerts(Property property, List<RiverLevelResponseDto> levels) {
        List<Candidate> candidates = new ArrayList<>();
        for (RiverLevelResponseDto level : levels) {
            if (!level.requiresAttention() || level.latitude() == null || level.longitude() == null || level.lastReadingAt() == null) continue;
            if (level.ageHours() != null && level.ageHours() > MAX_RIVER_READING_AGE_HOURS) continue;
            double distance = distanceKm(property.getLatitude(), property.getLongitude(), level.latitude(), level.longitude());
            if (distance > property.getAlertRiverRadiusKm()) continue;
            candidates.add(new Candidate("RIVER", "RIVER:" + level.stationCode() + ":" + level.status() + ":" + level.lastReadingAt().toLocalDate(),
                    "INUNDACAO".equals(level.status()) ? "PERIGO" : "ATENCAO",
                    limit("Nível elevado no rio: " + level.stationName(), 160),
                    limit(String.format(PT_BR, "Estação a %.0f km da propriedade: %s.", distance, level.statusDescription()), 1000)));
        }
        return candidates;
    }

    static List<Candidate> ndviAlerts(Property property, PropertyMonitoringDto monitoring) {
        if (monitoring.changeNdvi() == null || monitoring.latestDate() == null
                || monitoring.changeNdvi() > -property.getAlertNdviDrop()) {
            return List.of();
        }
        return List.of(new Candidate("NDVI", "NDVI:" + monitoring.latestDate(), "ATENCAO", "Queda no vigor da vegetação",
                String.format(PT_BR, "O NDVI caiu %.2f na imagem de %s. Vale inspecionar a área.",
                        Math.abs(monitoring.changeNdvi()), monitoring.latestDate().format(DATE))));
    }

    static double distanceKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double latitude = Math.toRadians(toLatitude - fromLatitude);
        double longitude = Math.toRadians(toLongitude - fromLongitude);
        double a = Math.pow(Math.sin(latitude / 2), 2) + Math.cos(Math.toRadians(fromLatitude))
                * Math.cos(Math.toRadians(toLatitude)) * Math.pow(Math.sin(longitude / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    private static String disclaimer(String type) {
        return "FROST".equals(type) || "HEAVY_RAIN".equals(type) ? WeatherAttribution.DISCLAIMER : null;
    }

    private static String limit(String text, int size) {
        return text.length() <= size ? text : text.substring(0, size - 1) + "…";
    }

    private void publish(Property property, Candidate candidate) {
        if (alerts.existsByProperty_PropertyIdAndAlertKey(property.getPropertyId(), candidate.key())) return;
        try {
            alerts.saveAndFlush(PropertyAlert.builder().property(property).alertKey(candidate.key()).type(candidate.type())
                    .severity(candidate.severity()).title(candidate.title()).message(candidate.message()).build());
        } catch (DataIntegrityViolationException duplicated) {
            return;
        }
        if (!mailEnabled) return;
        for (User user : recipients(property)) {
            try {
                sender.send(emails.createPropertyAlert(user.getEmail(), property.getName(), candidate.title(), candidate.message(),
                        disclaimer(candidate.type())), user);
            } catch (RuntimeException exception) {
                LogUtil.error("Falha ao enviar o alerta " + candidate.key(), exception);
            }
        }
    }

    private List<User> recipients(Property property) {
        Map<UUID, User> users = new LinkedHashMap<>();
        users.put(property.getUser().getUserId(), property.getUser());
        if (Boolean.TRUE.equals(property.getTeamEnabled())) {
            for (PropertyMember member : members.findAllWithUserByPropertyId(property.getPropertyId())) {
                users.putIfAbsent(member.getUser().getUserId(), member.getUser());
            }
        }
        return List.copyOf(users.values());
    }
}
