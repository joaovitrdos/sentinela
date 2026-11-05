package br.com.sentinela.entity;

import br.com.sentinela.enums.Feature;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.Set;

@Entity @Table(name = "tb_plan")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Plan {
    @Id @Column(name = "plan_code", length = 16) private String code;
    @Column(nullable = false, length = 60) private String name;
    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2) private BigDecimal monthlyPrice;
    @Column(name = "yearly_price", nullable = false, precision = 10, scale = 2) private BigDecimal yearlyPrice;
    @Column(name = "max_properties") private Integer maxProperties;
    @Column(name = "plan_rank", nullable = false) private int rank;
    @Column(nullable = false) private boolean active;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tb_plan_feature", joinColumns = @JoinColumn(name = "plan_code"))
    @Column(name = "feature", length = 24)
    @Enumerated(EnumType.STRING)
    private Set<Feature> features;
}
