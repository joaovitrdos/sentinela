package br.com.sentinela.repository;

import br.com.sentinela.entity.PropertyActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PropertyActivityRepository extends JpaRepository<PropertyActivity, UUID> {
    List<PropertyActivity> findAllByProperty_PropertyIdOrderByDateDescCreatedAtDesc(UUID propertyId);
    @Query("select activity from PropertyActivity activity join fetch activity.property " +
            "where activity.property.user.userId = :ownerId " +
            "and (:propertyId is null or activity.property.propertyId = :propertyId) " +
            "and activity.date between :fromDate and :toDate " +
            "order by activity.property.name, activity.date desc, activity.createdAt desc")
    List<PropertyActivity> findReportActivities(@Param("ownerId") UUID ownerId,
            @Param("propertyId") UUID propertyId,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate);
    void deleteAllByProperty_PropertyId(UUID propertyId);
    void deleteAllByProperty_User_UserId(UUID ownerId);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PropertyActivity activity set activity.plot = null where activity.plot in (select plot from Plot plot where plot.plotId = :plotId)")
    void detachPlot(@Param("plotId") UUID plotId);
}
