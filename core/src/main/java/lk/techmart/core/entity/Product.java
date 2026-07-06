package lk.techmart.core.entity;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@lombok.Getter
@lombok.Setter
@Entity
@Table(name = "products")
public class Product implements Serializable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 150)
    @NotNull
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)

    private Brand brand;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)

    private Category category;

    @NotNull
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @org.eclipse.persistence.annotations.Converter(
            name = "jsonbConverter",
            converterClass = lk.techmart.core.converter.JsonbConverter.class
    )
    @org.eclipse.persistence.annotations.Convert("jsonbConverter")
    @Column(name = "attributes", columnDefinition = "jsonb")
    private String attributes;

    @Column(name = "created_at", columnDefinition = "timestamp without time zone")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @JsonbTransient
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<WarehouseStock> warehouseStocks;

    @Transient
    private Integer quantity;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";  // ACTIVE, INACTIVE, OUT_OF_STOCK

}
