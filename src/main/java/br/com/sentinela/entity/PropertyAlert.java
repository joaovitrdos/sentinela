package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "tb_property_alert")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PropertyAlert {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "alert_id") private UUID alertId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false) private Property property;
    @Column(name = "alert_key", nullable = false, length = 120) private String alertKey;
    @Column(name = "alert_type", nullable = false, length = 24) private String type;
    @Column(nullable = false, length = 16) private String severity;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1000) private String message;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
