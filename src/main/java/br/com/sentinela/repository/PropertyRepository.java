package br.com.sentinela.repository;

import br.com.sentinela.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    List<Property> findAllByUser_UserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select property from Property property where property.user.userId = :userId " +
            "or (property.teamEnabled = true and exists (select member from PropertyMember member " +
            "where member.property = property and member.user.userId = :userId)) order by property.createdAt desc")
    List<Property> findAllAccessible(@Param("userId") UUID userId);

    @Query("select property from Property property where property.propertyId in :propertyIds and (property.user.userId = :userId " +
            "or (property.teamEnabled = true and exists (select member from PropertyMember member " +
            "where member.property = property and member.user.userId = :userId)))")
    List<Property> findAllAccessibleByIds(@Param("propertyIds") Collection<UUID> propertyIds, @Param("userId") UUID userId);

    Optional<Property> findByPropertyIdAndUser_UserId(UUID propertyId, UUID userId);

    List<Property> findAllByPropertyIdInAndUser_UserId(Collection<UUID> propertyIds, UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Property property where property.user.userId = :userId")
    void deleteAllOwnedBy(@Param("userId") UUID userId);
}
