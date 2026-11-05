package br.com.sentinela.service;

import br.com.sentinela.dto.response.IrrigationRecommendationResponseDto;
import br.com.sentinela.dto.response.IrrigationRecommendationResponseDto.Day;
import br.com.sentinela.entity.Plot;
import br.com.sentinela.entity.PlotSeason;
import br.com.sentinela.entity.Property;
import br.com.sentinela.entity.PropertyActivity;
import br.com.sentinela.exception.WeatherApiUnavailableException;
import br.com.sentinela.repository.PlotSeasonRepository;
import br.com.sentinela.repository.PropertyActivityRepository;
import br.com.sentinela.util.LogUtil;
import br.com.sentinela.util.WeatherAttribution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Balanço hídrico simplificado: demanda da cultura menos chuva efetiva e irrigação registrada. */
@Service @RequiredArgsConstructor
public class IrrigationService {
    private static final int HISTORY_DAYS = 7;
    private static final double EFFECTIVE_RAIN = 0.8;
    private static final double SOLAR_CONSTANT = 0.0820;
    private static final double MJ_TO_MM = 0.408;
    private static final Crop REFERENCE = new Crop("referência", 1, 1, 1, 1);
    /** Coeficientes da tabela FAO-56 e ciclos médios; devem ser revisados para a região de cada cliente. */
    private static final List<Crop> CROPS = List.of(
            new Crop("soja", 0.40, 1.15, 0.50, 120), new Crop("milho", 0.30, 1.20, 0.60, 125),
            new Crop("feijao", 0.40, 1.15, 0.35, 90), new Crop("trigo", 0.40, 1.15, 0.25, 120),
            new Crop("algodao", 0.35, 1.15, 0.60, 160), new Crop("arroz", 1.05, 1.20, 0.75, 130),
            new Crop("sorgo", 0.30, 1.10, 0.55, 120), new Crop("cana", 0.40, 1.25, 0.75, 365),
            new Crop("cafe", 0.95, 0.95, 0.95, 365), new Crop("tomate", 0.60, 1.15, 0.80, 130),
            new Crop("batata", 0.50, 1.15, 0.75, 110));
    private static final String METHOD = "ET0 por Hargreaves-Samani, Kc FAO-56 e chuva efetiva de 80%";
    private static final String SOURCE = WeatherAttribution.SOURCE + " e NASA POWER";
    private static final String DISCLAIMER = WeatherAttribution.DISCLAIMER + " Estimativa de lâmina líquida, sem considerar o tipo de solo nem a eficiência "
            + "do sistema de irrigação. Não substitui a avaliação de um agrônomo.";

    private final PropertyService properties;
    private final PlotService plots;
    private final PlotSeasonRepository seasons;
    private final PropertyActivityRepository activities;
    private final PropertyForecastService forecasts;
    private final NasaPowerService nasaPower;

    record Crop(String key, double initial, double middle, double end, int cycleDays) {}

    record ClimateDay(LocalDate date, double maxTemperatureC, double minTemperatureC, double rainMm, boolean forecast) {}

    public IrrigationRecommendationResponseDto recommend(UUID userId, UUID propertyId, UUID plotId) {
        Property property = properties.requireView(propertyId, userId);
        Plot plot = plots.requirePlot(propertyId, userId, plotId);
        PlotSeason season = seasons.findAllByPlot_PlotIdOrderByPlantingDateDesc(plotId).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus())).findFirst().orElse(null);
        String cropName = season == null ? plot.getCropName() : season.getCropName();
        LocalDate plantingDate = season == null ? plot.getPlantingDate() : season.getPlantingDate();

        List<String> warnings = new ArrayList<>();
        List<ClimateDay> forecastDays = forecastDays(forecasts.forecast(property).forecast());
        LocalDate today = forecastDays.getFirst().date();
        LocalDate from = today.minusDays(HISTORY_DAYS);
        List<ClimateDay> climate = new ArrayList<>(history(property, from, today.minusDays(1), warnings));
        climate.addAll(forecastDays);

        Map<LocalDate, Double> irrigation = new HashMap<>();
        Map<LocalDate, Double> recordedRain = new HashMap<>();
        for (PropertyActivity activity : activities.findAllByProperty_PropertyIdAndDateBetween(propertyId, from, today)) {
            if (activity.getAmountMm() == null) continue;
            boolean samePlot = activity.getPlot() != null && plotId.equals(activity.getPlot().getPlotId());
            if ("IRRIGATION".equals(activity.getType()) && samePlot) irrigation.merge(activity.getDate(), activity.getAmountMm(), Double::sum);
            if ("RAINFALL".equals(activity.getType()) && (samePlot || activity.getPlot() == null)) {
                recordedRain.merge(activity.getDate(), activity.getAmountMm(), Double::sum);
            }
        }

        Crop crop = crop(cropName);
        boolean cropKnown = crop != null && plantingDate != null;
        if (!cropKnown) {
            crop = REFERENCE;
            plantingDate = null;
            warnings.add("Cultura ou data de plantio não reconhecida: usamos a demanda de referência, sem ajuste pela cultura.");
        }
        List<Day> days = balance(property.getLatitude(), climate, irrigation, recordedRain, crop, plantingDate);
        int expectedDays = HISTORY_DAYS + forecastDays.size();
        int missing = expectedDays - climate.size();
        if (missing > 0) {
            warnings.add(missing + " dia(s) recente(s) ainda sem dados, fora do cálculo: o déficit real pode ser maior.");
        }
        double recommended = days.isEmpty() ? 0 : days.getLast().deficitMm();
        return new IrrigationRecommendationResponseDto(plotId, plot.getName(), cropName, cropKnown,
                round(coefficient(crop, plantingDate, today)), recommended, missing, days, warnings, METHOD, SOURCE, DISCLAIMER);
    }

    private List<ClimateDay> forecastDays(JsonNode forecast) {
        List<ClimateDay> days = new ArrayList<>();
        for (JsonNode node : forecast.path("forecastday")) {
            JsonNode day = node.path("day");
            days.add(new ClimateDay(LocalDate.parse(node.path("date").asText()), day.path("maxtemp_c").asDouble(),
                    day.path("mintemp_c").asDouble(), day.path("totalprecip_mm").asDouble(), true));
        }
        if (days.isEmpty()) throw new WeatherApiUnavailableException();
        return days;
    }

    private List<ClimateDay> history(Property property, LocalDate from, LocalDate to, List<String> warnings) {
        try {
            return nasaPower.daily(property.getLatitude(), property.getLongitude(), from, to).stream()
                    .map(day -> new ClimateDay(day.date(), day.maxTemperatureC(), day.minTemperatureC(), day.rainMm(), false))
                    .toList();
        } catch (RuntimeException exception) {
            LogUtil.warn("NASA POWER indisponível");
            warnings.add("Histórico climático indisponível: o cálculo usa apenas a previsão.");
            return List.of();
        }
    }

    static List<Day> balance(double latitude, List<ClimateDay> climate, Map<LocalDate, Double> irrigation,
                             Map<LocalDate, Double> recordedRain, Crop crop, LocalDate plantingDate) {
        List<Day> days = new ArrayList<>();
        double deficit = 0;
        for (ClimateDay day : climate) {
            double et0 = hargreaves(latitude, day.date(), day.maxTemperatureC(), day.minTemperatureC());
            double demand = et0 * coefficient(crop, plantingDate, day.date());
            double rain = recordedRain.getOrDefault(day.date(), day.rainMm());
            double applied = irrigation.getOrDefault(day.date(), 0.0);
            deficit = Math.max(0, deficit + demand - rain * EFFECTIVE_RAIN - applied);
            days.add(new Day(day.date(), day.forecast(), round(et0), round(demand), round(rain), round(applied), round(deficit)));
        }
        return days;
    }

    static Crop crop(String name) {
        if (name == null) return null;
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return CROPS.stream().filter(crop -> normalized.contains(crop.key())).findFirst().orElse(null);
    }

    static double coefficient(Crop crop, LocalDate plantingDate, LocalDate date) {
        if (plantingDate == null) return crop.middle();
        long age = ChronoUnit.DAYS.between(plantingDate, date);
        if (age < 0) return 0;
        double progress = (double) age / crop.cycleDays();
        if (progress < 0.20) return crop.initial();
        if (progress < 0.45) return crop.initial() + (crop.middle() - crop.initial()) * (progress - 0.20) / 0.25;
        if (progress < 0.75) return crop.middle();
        if (progress < 1) return crop.middle() + (crop.end() - crop.middle()) * (progress - 0.75) / 0.25;
        return crop.end();
    }

    static double hargreaves(double latitude, LocalDate date, double maxTemperatureC, double minTemperatureC) {
        double range = Math.max(0, maxTemperatureC - minTemperatureC);
        double mean = (maxTemperatureC + minTemperatureC) / 2;
        return Math.max(0, 0.0023 * (mean + 17.8) * Math.sqrt(range) * extraterrestrialRadiation(latitude, date) * MJ_TO_MM);
    }

    /** Radiação no topo da atmosfera, em MJ/m²/dia (FAO-56, equação 21). */
    static double extraterrestrialRadiation(double latitude, LocalDate date) {
        double dayAngle = 2 * Math.PI * date.getDayOfYear() / 365;
        double inverseDistance = 1 + 0.033 * Math.cos(dayAngle);
        double declination = 0.409 * Math.sin(dayAngle - 1.39);
        double radians = Math.toRadians(latitude);
        double sunsetAngle = Math.acos(Math.clamp(-Math.tan(radians) * Math.tan(declination), -1, 1));
        return 24 * 60 / Math.PI * SOLAR_CONSTANT * inverseDistance * (sunsetAngle * Math.sin(radians) * Math.sin(declination)
                + Math.cos(radians) * Math.cos(declination) * Math.sin(sunsetAngle));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
