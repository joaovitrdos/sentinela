package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "tb_property_activity", indexes = {
        @Index(name = "idx_property_activity_property_date", columnList = "property_id, activity_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "activity_id")
    private UUID activityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plot_id")
    private Plot plot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    private PlotSeason season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private User responsibleUser;

    @Column(name = "responsible_name", length = 120)
    private String responsibleName;

    @Column(name = "input_name", length = 120)
    private String inputName;

    @Column(name = "input_quantity", precision = 14, scale = 3)
    private java.math.BigDecimal inputQuantity;

    @Column(name = "input_unit", length = 24)
    private String inputUnit;

    @Column(name = "harvest_quantity", precision = 14, scale = 3)
    private java.math.BigDecimal harvestQuantity;

    @Column(name = "harvest_unit", length = 24)
    private String harvestUnit;

    @Column(name = "activity_type", nullable = false, length = 24)
    private String type;

    @Column(name = "activity_date", nullable = false)
    private LocalDate date;

    @Column(name = "activity_time")
    private LocalTime time;

    @Column(name = "notes", nullable = false, length = 1000)
    private String notes;

    @Column(name = "amount_mm")
    private Double amountMm;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
