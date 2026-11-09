package br.com.sentinela.entity;

import br.com.sentinela.util.Util;

import br.com.sentinela.enums.TeamRole;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "tb_property_invitation", uniqueConstraints = @UniqueConstraint(columnNames = {"property_id", "email"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PropertyInvitation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invitation_id")
    private UUID invitationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    @Column(nullable = false, length = 320)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TeamRole role;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (Util.isNull(createdAt)) createdAt = LocalDateTime.now(); }
}
