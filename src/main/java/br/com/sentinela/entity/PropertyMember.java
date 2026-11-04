package br.com.sentinela.entity;

import br.com.sentinela.enums.TeamRole;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "tb_property_member", uniqueConstraints = @UniqueConstraint(columnNames = {"property_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PropertyMember {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "member_id")
    private UUID memberId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TeamRole role;
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
    @PrePersist void onCreate() { if (joinedAt == null) joinedAt = LocalDateTime.now(); }
}
