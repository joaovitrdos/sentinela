package br.com.sentinela.repository;

import br.com.sentinela.entity.System;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SystemRepository extends JpaRepository<System, UUID> {
}
