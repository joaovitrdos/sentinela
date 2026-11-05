package br.com.sentinela.repository;

import br.com.sentinela.entity.PropertyAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PropertyAlertRepository extends JpaRepository<PropertyAlert, UUID> {
    boolean existsByProperty_PropertyIdAndAlertKey(UUID propertyId, String alertKey);
    List<PropertyAlert> findTop50ByProperty_PropertyIdOrderByCreatedAtDesc(UUID propertyId);
}
