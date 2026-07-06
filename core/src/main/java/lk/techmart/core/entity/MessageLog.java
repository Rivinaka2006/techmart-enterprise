package lk.techmart.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;

@lombok.Getter
@lombok.Setter
@Entity
@Table(name = "message_logs")
public class MessageLog implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 50)
    @NotNull
    @Column(name = "message_type", nullable = false, length = 50)
    private String messageType;

    @Size(max = 20)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @Column(name = "processing_time_ms", nullable = false)
    private Integer processingTimeMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}