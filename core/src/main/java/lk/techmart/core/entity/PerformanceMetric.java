package lk.techmart.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@lombok.Getter
@lombok.Setter
@Entity
@Table(name = "performance_metrics")
public class PerformanceMetric implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 100)
    @NotNull
    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @NotNull
    @Column(name = "metric_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

}