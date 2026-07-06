package lk.techmart.ejb;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateful;
import jakarta.ejb.StatefulTimeout;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import lk.techmart.core.dto.CartItem;
import lk.techmart.core.dto.OrderPayload;
import lk.techmart.core.entity.Product;
import lk.techmart.core.service.ProductService;
import lk.techmart.core.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Stateful(name = "ShoppingCartBean")
@StatefulTimeout(value = 30, unit = TimeUnit.MINUTES)
public class ShoppingCartBean implements ShoppingCartService,Serializable {

    private static final Jsonb JSONB = JsonbBuilder.create();
    private static final String ACTIVE_STATUS = "ACTIVE";

    @EJB
    private ProductService productService;

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "jms/queue/OrderQueue")
    private Queue orderQueue;

    private Integer userId;
    private final Map<Integer, CartItem> items = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        log.info("ShoppingCartBean instance created: {}", this.hashCode());
    }


    public void startCart(Integer userId) {
        this.userId = userId;
    }

    public void addItem(Integer productId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        Product product = productService.getProductById(productId);
        if (product == null) throw new IllegalArgumentException("Unknown product: " + productId);
        int existingQuantity = items.containsKey(productId) ? items.get(productId).getQuantity() : 0;
        validateProductCanBeAdded(product, existingQuantity + quantity);

        items.merge(
                productId,
                new CartItem(productId, product.getName(), quantity, product.getPrice()),
                (existing, incoming) -> {
                    existing.setQuantity(existing.getQuantity() + incoming.getQuantity());
                    return existing;
                }
        );
    }

    private void validateProductCanBeAdded(Product product, int cartQuantity) {
        String status = product.getStatus() == null ? "" : product.getStatus().trim();
        if (!ACTIVE_STATUS.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Cannot add inactive product to cart");
        }

        int availableQuantity = product.getQuantity() == null ? 0 : product.getQuantity();
        if (availableQuantity <= 0) {
            throw new IllegalArgumentException("Cannot add out-of-stock product to cart");
        }
        if (cartQuantity > availableQuantity) {
            throw new IllegalArgumentException("Only " + availableQuantity + " item(s) available in stock");
        }
    }

    public void updateQuantity(Integer productId, int quantity) {
        if (quantity <= 0) {
            removeItem(productId);
            return;
        }
        CartItem item = items.get(productId);
        if (item == null) throw new IllegalArgumentException("Item not in cart: " + productId);
        Product product = productService.getProductById(productId);
        if (product == null) throw new IllegalArgumentException("Unknown product: " + productId);
        validateProductCanBeAdded(product, quantity);
        item.setQuantity(quantity);
    }

    public void removeItem(Integer productId) {
        items.remove(productId);
    }

    public List<CartItem> viewCart() {
        return new ArrayList<>(items.values());
    }

    public BigDecimal getCartTotal() {
        return items.values().stream()
                .map(i -> i.getPricePerUnit().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String checkout() {
        if (items.isEmpty()) throw new IllegalStateException("Cannot checkout an empty cart");
        if (userId == null) throw new IllegalStateException("Cart has no associated user");

        OrderPayload payload = new OrderPayload();
        payload.setUserId(userId);
        payload.setItems(new ArrayList<>(items.values()));
        payload.setTotalAmount(getCartTotal());
        payload.setSubmittedAt(Instant.now());

        String json = JSONB.toJson(payload);
        jmsContext.createProducer().send(orderQueue, json);

        log.info("Order queued for user {} — total {}", userId, payload.getTotalAmount());


        BigDecimal total = getCartTotal();
        items.clear();

        return "Order submitted for processing. Total: " + total;
    }

    @PreDestroy
    public void cleanup() {
        if (!items.isEmpty()) {
            log.warn("Cart for user {} discarded with {} unchecked-out item(s)", userId, items.size());
        }
    }
}
