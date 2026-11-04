package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_property", indexes = {
        @Index(name = "idx_property_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Property implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "property_id")
    private UUID propertyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    @Column(name = "team_enabled", nullable = false)
    private Boolean teamEnabled = false;

    @Column(name = "team_title", length = 120)
    private String teamTitle;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "polygon_json", columnDefinition = "text")
    private String polygonJson;

    @Column(name = "area_hectares")
    private Double areaHectares;

    @Column(name = "crop_name", length = 120)
    private String cropName;

    @Column(name = "plot_name", length = 120)
    private String plotName;

    @Column(name = "planting_date")
    private LocalDate plantingDate;

    @Column(name = "crop_stage", length = 80)
    private String cropStage;

    @Builder.Default
    @Column(name = "used")
    private Boolean used = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
