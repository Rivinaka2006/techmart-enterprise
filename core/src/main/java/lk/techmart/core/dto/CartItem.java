package lk.techmart.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {
    private Integer productId;
    private String productName;
    private Integer quantity;
    private BigDecimal pricePerUnit;
}
