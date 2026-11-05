package br.com.sentinela.service;

import br.com.sentinela.dto.request.PlotSeasonRequestDto;
import br.com.sentinela.entity.Plot;
import br.com.sentinela.entity.PlotSeason;
import br.com.sentinela.repository.PlotSeasonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlotSeasonServiceTest {
    @Mock PropertyService properties;
    @Mock PlotService plots;
    @Mock PlotSeasonRepository repository;
    @Mock br.com.sentinela.repository.PropertyActivityRepository activities;
    @InjectMocks PlotSeasonService service;

    @Test
    void startsSeasonForPlotAndKeepsCropOnPlot() {
        UUID userId = UUID.randomUUID(), propertyId = UUID.randomUUID(), plotId = UUID.randomUUID();
        Plot plot = Plot.builder().plotId(plotId).build();
        when(plots.requirePlot(propertyId, userId, plotId)).thenReturn(plot);
        when(repository.saveAndFlush(any(PlotSeason.class))).thenAnswer(call -> call.getArgument(0));
        var result = service.create(userId, propertyId, plotId,
                new PlotSeasonRequestDto("Milho", "AG 1051", LocalDate.of(2026, 9, 1)));
        assertThat(result.cropName()).isEqualTo("Milho");
        assertThat(result.variety()).isEqualTo("AG 1051");
        assertThat(plot.getCropName()).isEqualTo("Milho");
    }

    @Test
    void rejectsSecondActiveSeason() {
        UUID userId = UUID.randomUUID(), propertyId = UUID.randomUUID(), plotId = UUID.randomUUID();
        when(plots.requirePlot(propertyId, userId, plotId)).thenReturn(Plot.builder().plotId(plotId).build());
        when(repository.existsByPlot_PlotIdAndStatus(plotId, "ACTIVE")).thenReturn(true);
        assertThatThrownBy(() -> service.create(userId, propertyId, plotId,
                new PlotSeasonRequestDto("Soja", null, LocalDate.now())))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsHarvestBeforePlanting() {
        UUID userId = UUID.randomUUID(), propertyId = UUID.randomUUID(), plotId = UUID.randomUUID(), seasonId = UUID.randomUUID();
        Plot plot = Plot.builder().plotId(plotId).build();
        PlotSeason season = PlotSeason.builder().seasonId(seasonId).plot(plot).plantingDate(LocalDate.now())
                .status("ACTIVE").build();
        when(plots.requirePlot(propertyId, userId, plotId)).thenReturn(plot);
        when(repository.findBySeasonIdAndPlot_PlotIdAndPlot_Property_PropertyId(seasonId, plotId, propertyId))
                .thenReturn(java.util.Optional.of(season));
        assertThatThrownBy(() -> service.close(userId, propertyId, plotId, seasonId, LocalDate.now().minusDays(1)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(season.getStatus()).isEqualTo("ACTIVE");
    }
}
