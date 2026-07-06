package lk.techmart.web.session;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import jakarta.ejb.EJB;
import lk.techmart.core.dto.CartItem;
import lk.techmart.core.service.ShoppingCartService;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Named
@SessionScoped
public class CartSessionBean implements Serializable {

    @EJB(beanName = "ShoppingCartBean") 
    private ShoppingCartService cart;

    public void startCart(Integer userId) {
        cart.startCart(userId);
    }

    public void addItem(Integer productId, int quantity) {
        cart.addItem(productId, quantity);
    }

    public void updateQuantity(Integer productId, int quantity) {
        cart.updateQuantity(productId, quantity);
    }

    public void removeItem(Integer productId) {
        cart.removeItem(productId);
    }

    public List<CartItem> viewCart() {
        return cart.viewCart();
    }

    public BigDecimal getCartTotal() {
        return cart.getCartTotal();
    }

    public String checkout() {
        return cart.checkout();
    }
}