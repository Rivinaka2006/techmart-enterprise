package lk.techmart.core.service;

import lk.techmart.core.dto.OrderPayload;

public interface OrderProcessor {
    void processOrder(OrderPayload payload);
}
