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
