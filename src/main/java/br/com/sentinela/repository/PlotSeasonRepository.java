package br.com.sentinela.repository;

import br.com.sentinela.entity.PlotSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlotSeasonRepository extends JpaRepository<PlotSeason, UUID> {
    List<PlotSeason> findAllByPlot_Property_PropertyIdOrderByPlantingDateDesc(UUID propertyId);
    Optional<PlotSeason> findBySeasonIdAndPlot_PlotIdAndPlot_Property_PropertyId(UUID seasonId, UUID plotId, UUID propertyId);
    boolean existsByPlot_PlotIdAndStatus(UUID plotId, String status);
    List<PlotSeason> findAllByPlot_PlotIdOrderByPlantingDateDesc(UUID plotId);
}
