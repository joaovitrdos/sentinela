package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_field_action")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FieldAction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "action_id")
    private UUID actionId;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plot_id")
    private Plot plot;
    @Column(nullable = false, length = 24)
    private String kind;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(name = "responsible_name", nullable = false, length = 120)
    private String responsibleName;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id") private User assignee;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_user_id") private User completedBy;
    @Column(name = "reminder_sent_at") private LocalDateTime reminderSentAt;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "source_note", length = 500)
    private String sourceNote;
    @Column(name = "completion_note", length = 1000)
    private String completionNote;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
