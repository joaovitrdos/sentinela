package br.com.sentinela.entity;

import br.com.sentinela.enums.CodePurpose;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_verification_code", indexes = {
        @Index(name = "idx_verification_code_user", columnList = "user_id"),
        @Index(name = "idx_verification_code_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationCode implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "verification_code_id")
    private UUID verificationCodeId;

    @Column(name = "code")
    private String codeHash;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 24)
    private CodePurpose purpose = CodePurpose.PASSWORD_RECOVERY;

    @Column(name = "target_email")
    private String targetEmail;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "used")
    private Boolean used = false;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired(LocalDateTime moment) {
        return expiresAt == null || expiresAt.isBefore(moment);
    }
}
