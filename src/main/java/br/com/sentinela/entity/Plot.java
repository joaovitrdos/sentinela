package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_plot")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Plot {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plot_id")
    private UUID plotId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "crop_name", length = 120)
    private String cropName;
    @Column(name = "planting_date")
    private LocalDate plantingDate;
    @Column(name = "crop_stage", length = 80)
    private String cropStage;
    @Column(name = "area_hectares")
    private Double areaHectares;
}
