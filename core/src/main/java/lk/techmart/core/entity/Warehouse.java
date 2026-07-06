package lk.techmart.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@lombok.Getter
@lombok.Setter
@Entity
@Table(name = "warehouses")
public class Warehouse implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 100)
    @NotNull
    @Column(name = "warehouse_name", nullable = false, length = 100)
    private String warehouseName;

    @Size(max = 150)
    @Column(name = "location", length = 150)
    private String location;

}