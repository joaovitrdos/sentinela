package br.com.sentinela.repository;

import br.com.sentinela.entity.FieldAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldActionRepository extends JpaRepository<FieldAction, UUID> {
    List<FieldAction> findAllByProperty_PropertyIdOrderByDueDateAscCreatedAtAsc(UUID propertyId);
    Optional<FieldAction> findByActionIdAndProperty_PropertyId(UUID actionId, UUID propertyId);
    void deleteAllByProperty_PropertyId(UUID propertyId);
    void deleteAllByProperty_User_UserId(UUID ownerId);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FieldAction action set action.plot = null where action.plot in (select plot from Plot plot where plot.plotId = :plotId)")
    void detachPlot(@Param("plotId") UUID plotId);
}
