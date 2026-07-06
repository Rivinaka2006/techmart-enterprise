package lk.techmart.core.service;

import jakarta.ejb.Remote;
import lk.techmart.core.dto.CartItem;

import java.math.BigDecimal;
import java.util.List;

@Remote
public interface ShoppingCartService {
    void startCart(Integer userId);
    void addItem(Integer productId, int quantity);
    void updateQuantity(Integer productId, int quantity);
    void removeItem(Integer productId);
    List<CartItem> viewCart();
    BigDecimal getCartTotal();
    String checkout();
}