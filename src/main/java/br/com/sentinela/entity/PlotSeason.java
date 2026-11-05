package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "tb_plot_season")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlotSeason {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "season_id") private UUID seasonId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id", nullable = false) private Plot plot;
    @Column(name = "crop_name", nullable = false, length = 120) private String cropName;
    @Column(length = 120) private String variety;
    @Column(name = "planting_date", nullable = false) private LocalDate plantingDate;
    @Column(name = "harvest_date") private LocalDate harvestDate;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
