package br.com.sentinela.repository;

import br.com.sentinela.entity.Subscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByUserId(UUID userId);

    /** Criada no primeiro uso para cobrir todos os caminhos de cadastro; o conflito resolve acessos simultâneos. */
    @Modifying
    @Query(value = "insert into tb_subscription (subscription_id, user_id, status, trial_ends_at, created_at) "
            + "values (:id, :userId, 'TRIAL', :trialEndsAt, :now) on conflict (user_id) do nothing", nativeQuery = true)
    void insertTrial(@Param("id") UUID id, @Param("userId") UUID userId, @Param("trialEndsAt") LocalDateTime trialEndsAt,
                     @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from Subscription subscription where subscription.userId = :userId")
    Optional<Subscription> lockByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from Subscription subscription where subscription.asaasSubscriptionId = :asaasId")
    Optional<Subscription> lockByAsaasSubscriptionId(@Param("asaasId") String asaasId);

    @Modifying
    @Query(value = "insert into tb_asaas_event (event_id, event_type, received_at) values (:id, :type, :now) "
            + "on conflict (event_id) do nothing", nativeQuery = true)
    int registerEvent(@Param("id") String id, @Param("type") String type, @Param("now") LocalDateTime now);
}
