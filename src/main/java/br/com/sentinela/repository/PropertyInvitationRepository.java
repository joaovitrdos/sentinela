package br.com.sentinela.repository;

import br.com.sentinela.entity.PropertyInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyInvitationRepository extends JpaRepository<PropertyInvitation, UUID> {
    Optional<PropertyInvitation> findByTokenHash(String tokenHash);
    Optional<PropertyInvitation> findByProperty_PropertyIdAndEmail(UUID propertyId, String email);
    List<PropertyInvitation> findAllByProperty_PropertyIdOrderByCreatedAtDesc(UUID propertyId);
    void deleteAllByProperty_PropertyId(UUID propertyId);
}
