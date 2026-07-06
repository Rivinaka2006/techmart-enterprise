package lk.techmart.core.entity;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;

@lombok.Getter
@lombok.Setter
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonbTransient
    private Order order;

    @Size(max = 20)
    @Column(name = "old_status", length = 20)
    private String oldStatus;

    @Size(max = 20)
    @Column(name = "new_status", length = 20)
    private String newStatus;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "notes", length = 500)
    private String notes;

}