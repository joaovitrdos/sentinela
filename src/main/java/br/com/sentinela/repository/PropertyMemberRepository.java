package br.com.sentinela.repository;

import br.com.sentinela.entity.PropertyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMemberRepository extends JpaRepository<PropertyMember, UUID> {
    Optional<PropertyMember> findByProperty_PropertyIdAndUser_UserId(UUID propertyId, UUID userId);
    List<PropertyMember> findAllByProperty_PropertyIdOrderByJoinedAtAsc(UUID propertyId);
    boolean existsByProperty_PropertyIdAndUser_UserId(UUID propertyId, UUID userId);
    void deleteAllByProperty_PropertyId(UUID propertyId);
    void deleteAllByUser_UserId(UUID userId);
}
