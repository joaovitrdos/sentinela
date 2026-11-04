package br.com.sentinela.repository;

import br.com.sentinela.entity.Email;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EmailRepository extends JpaRepository<Email, UUID> {

    @Modifying
    @Query("delete from Email e where e.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

}
