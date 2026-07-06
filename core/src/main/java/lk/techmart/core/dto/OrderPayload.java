package lk.techmart.core.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
public class OrderPayload implements Serializable {
    private Integer userId;
    private List<CartItem> items;
    private BigDecimal totalAmount;
    private Instant submittedAt;
}
