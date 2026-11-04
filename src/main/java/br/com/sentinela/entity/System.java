package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_system")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class System implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "system_id")
    private UUID systemId;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "maintenance", nullable = false)
    private boolean maintenance;

    @Column(name = "maintenance_start")
    private LocalDateTime maintenanceStart;

    @Column(name = "maintenance_end")
    private LocalDateTime maintenanceEnd;

    @Transient
    private boolean previousMaintenance;

    @PostLoad
    public void postLoad() {
        this.previousMaintenance = this.maintenance;
    }

    @PrePersist
    public void prePersist() {
        if (this.maintenance)  {
            this.maintenanceStart = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        if (this.maintenance)  {
            this.maintenanceStart = LocalDateTime.now();
            maintenanceEnd = null;
        } else {
            this.maintenanceEnd = LocalDateTime.now();
        }
    }

}
