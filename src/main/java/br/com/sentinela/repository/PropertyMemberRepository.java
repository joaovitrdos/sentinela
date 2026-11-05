package br.com.sentinela.repository;

import br.com.sentinela.entity.PropertyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMemberRepository extends JpaRepository<PropertyMember, UUID> {
    Optional<PropertyMember> findByProperty_PropertyIdAndUser_UserId(UUID propertyId, UUID userId);
    List<PropertyMember> findAllByProperty_PropertyIdOrderByJoinedAtAsc(UUID propertyId);
    @Query("select member from PropertyMember member join fetch member.user where member.property.propertyId = :propertyId")
    List<PropertyMember> findAllWithUserByPropertyId(@Param("propertyId") UUID propertyId);
    boolean existsByProperty_PropertyIdAndUser_UserId(UUID propertyId, UUID userId);
    void deleteAllByProperty_PropertyId(UUID propertyId);
    void deleteAllByUser_UserId(UUID userId);
}
