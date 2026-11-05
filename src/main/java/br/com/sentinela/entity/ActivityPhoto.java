package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "tb_activity_photo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityPhoto {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "photo_id") private UUID photoId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false) private PropertyActivity activity;
    @Column(name = "file_name", nullable = false, length = 255) private String fileName;
    @Column(name = "content_type", nullable = false, length = 40) private String contentType;
    @Column(name = "image_data", nullable = false, columnDefinition = "bytea") private byte[] imageData;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
