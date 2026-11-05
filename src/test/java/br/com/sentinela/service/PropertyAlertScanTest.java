package br.com.sentinela.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.sentinela.dto.response.ForecastWeatherDto;
import br.com.sentinela.entity.Property;
import br.com.sentinela.entity.PropertyAlert;
import br.com.sentinela.entity.PropertyMember;
import br.com.sentinela.entity.User;
import br.com.sentinela.exception.WeatherApiUnavailableException;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.notification.EmailMessage;
import br.com.sentinela.repository.PropertyAlertRepository;
import br.com.sentinela.repository.PropertyMemberRepository;
import br.com.sentinela.repository.PropertyRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class PropertyAlertScanTest {

    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final PropertyAlertRepository alerts = mock(PropertyAlertRepository.class);
    private final PropertyMemberRepository members = mock(PropertyMemberRepository.class);
    private final PropertyForecastService forecasts = mock(PropertyForecastService.class);
    private final InmetService inmet = mock(InmetService.class);
    private final ReferenceLevelService rivers = mock(ReferenceLevelService.class);
    private final EmailSenderService sender = mock(EmailSenderService.class);
    private final WeatherApiUsageService usage = mock(WeatherApiUsageService.class);
    private final PlanService plans = mock(PlanService.class);
    private final PropertyAlertService service = new PropertyAlertService(properties, alerts, members, mock(PropertyService.class),
            forecasts, plans, usage, inmet, rivers, mock(CopernicusService.class), new EmailFactory("http://localhost"), sender);

    private final User owner = User.builder().userId(UUID.randomUUID()).email("dono@example.com").build();
    private final User member = User.builder().userId(UUID.randomUUID()).email("equipe@example.com").build();
    private final Property property = Property.builder().propertyId(UUID.randomUUID()).name("Fazenda").user(owner)
            .latitude(-16.68).longitude(-49.25).alertEnabled(true).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        when(usage.automationAllowed()).thenReturn(true);
        when(plans.allows(any(UUID.class), any(UUID.class), any(br.com.sentinela.enums.Feature.class))).thenReturn(true);
        when(properties.findAllWithAlertsEnabled()).thenReturn(List.of(property));
        when(inmet.activeWarnings()).thenReturn(List.of());
        when(rivers.getRiverLevels()).thenReturn(List.of());
        when(forecasts.forecast(property)).thenReturn(frostForecast());
    }

    @Test
    void newAlertIsSavedAndSentToOwnerAndTeamOnce() {
        property.setTeamEnabled(true);
        when(members.findAllWithUserByPropertyId(property.getPropertyId())).thenReturn(List.of(
                PropertyMember.builder().user(member).build(), PropertyMember.builder().user(owner).build()));

        service.scanWeatherAndRivers();

        ArgumentCaptor<PropertyAlert> saved = ArgumentCaptor.forClass(PropertyAlert.class);
        verify(alerts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAlertKey()).isEqualTo("FROST:2026-11-05");
        assertThat(saved.getValue().getMessage()).contains("1,5 °C");
        verify(sender).send(any(), eq(owner));
        verify(sender).send(any(), eq(member));
        verify(sender, times(2)).send(any(), any());
    }

    @Test
    void alertAlreadyGeneratedIsNotRepeated() {
        when(alerts.existsByProperty_PropertyIdAndAlertKey(property.getPropertyId(), "FROST:2026-11-05")).thenReturn(true);

        service.scanWeatherAndRivers();

        verify(alerts, never()).saveAndFlush(any());
        verify(sender, never()).send(any(), any());
    }

    @Test
    void concurrentDuplicateDoesNotSendEmail() {
        when(alerts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicado"));

        service.scanWeatherAndRivers();

        verify(sender, never()).send(any(), any());
    }

    @Test
    void disabledMailStillKeepsTheAlertHistory() {
        ReflectionTestUtils.setField(service, "mailEnabled", false);

        service.scanWeatherAndRivers();

        verify(alerts).saveAndFlush(any());
        verify(sender, never()).send(any(), any());
    }

    @Test
    void failureInOneSourceDoesNotStopTheOthers() {
        when(inmet.activeWarnings()).thenThrow(new IllegalStateException("INMET fora do ar"));

        service.scanWeatherAndRivers();

        verify(alerts).saveAndFlush(any());
    }

    @Test
    void failureInOnePropertyDoesNotStopTheNext() {
        Property other = Property.builder().propertyId(UUID.randomUUID()).name("Outra").user(owner)
                .latitude(-10.0).longitude(-49.0).alertEnabled(true).build();
        when(properties.findAllWithAlertsEnabled()).thenReturn(List.of(property, other));
        when(forecasts.forecast(property)).thenThrow(new WeatherApiUnavailableException());
        when(forecasts.forecast(other)).thenReturn(frostForecast());

        service.scanWeatherAndRivers();

        verify(alerts).existsByProperty_PropertyIdAndAlertKey(eq(other.getPropertyId()), anyString());
        verify(alerts).saveAndFlush(any());
    }

    @Test
    void ownerWithoutAlertsInThePlanIsNotScanned() {
        when(plans.allows(any(UUID.class), any(UUID.class), any(br.com.sentinela.enums.Feature.class))).thenReturn(false);

        service.scanWeatherAndRivers();

        verify(forecasts, never()).forecast(any());
        verify(alerts, never()).saveAndFlush(any());
    }

    @Test
    void noMonitoredPropertySkipsExternalCalls() {
        when(properties.findAllWithAlertsEnabled()).thenReturn(List.of());

        service.scanWeatherAndRivers();

        verify(inmet, never()).activeWarnings();
        verify(rivers, never()).getRiverLevels();
    }

    @Test
    void weatherAlertCreditsTheSourceAndCarriesTheDisclaimer() {
        service.scanWeatherAndRivers();

        ArgumentCaptor<PropertyAlert> saved = ArgumentCaptor.forClass(PropertyAlert.class);
        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(alerts).saveAndFlush(saved.capture());
        verify(sender).send(email.capture(), eq(owner));
        assertThat(saved.getValue().getMessage()).contains("Fonte: WeatherAPI.com");
        assertThat(org.springframework.web.util.HtmlUtils.htmlUnescape(email.getValue().getBody())).contains("WeatherAPI.com").contains("consulte sempre os serviços meteorológicos oficiais");
    }

    @Test
    void quotaNearTheEndPausesForecastButKeepsOtherSources() {
        when(usage.automationAllowed()).thenReturn(false);

        service.scanWeatherAndRivers();

        verify(forecasts, never()).forecast(any());
        verify(inmet).activeWarnings();
    }

    private ForecastWeatherDto frostForecast() {
        var mapper = JsonMapper.builder().build();
        return new ForecastWeatherDto(mapper.readTree("{}"), mapper.readTree("{}"), mapper.readTree("""
                {"forecastday":[{"date":"2026-11-05","day":{"mintemp_c":1.5,"totalprecip_mm":0}}]}"""));
    }
}
