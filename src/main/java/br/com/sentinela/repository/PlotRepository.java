package br.com.sentinela.repository;

import br.com.sentinela.entity.Plot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlotRepository extends JpaRepository<Plot, UUID> {
    List<Plot> findAllByProperty_PropertyIdOrderByName(UUID propertyId);
    Optional<Plot> findByPlotIdAndProperty_PropertyId(UUID plotId, UUID propertyId);
    boolean existsByProperty_PropertyIdAndNameIgnoreCase(UUID propertyId, String name);
    void deleteAllByProperty_PropertyId(UUID propertyId);
    void deleteAllByProperty_User_UserId(UUID ownerId);
}
