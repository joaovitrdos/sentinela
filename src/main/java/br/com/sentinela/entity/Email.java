package br.com.sentinela.entity;

import br.com.sentinela.enums.StatusEmail;
import br.com.sentinela.enums.TypeEmail;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_emails", indexes = {
        @Index(name = "idx_email_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Email implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "email_id")
    private UUID emailId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_from")
    private String emailFrom;

    @Column(name = "email_to")
    private String emailTo;

    @Column(name = "subject")
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_email")
    private StatusEmail statusEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_email")
    private TypeEmail typeEmail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
