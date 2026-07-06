package lk.techmart.core.dto;

import java.io.Serializable;
import java.math.BigDecimal;

@lombok.Getter
@lombok.Setter
public class ProductCreateRequest implements Serializable {
    private String name;
    private Integer brandId;
    private Integer categoryId;
    private BigDecimal price;
    private String status;
    private String attributes;
}
