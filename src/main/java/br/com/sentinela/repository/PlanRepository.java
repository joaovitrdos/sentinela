package br.com.sentinela.repository;

import br.com.sentinela.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, String> {
    List<Plan> findAllByActiveTrueOrderByRank();
}
