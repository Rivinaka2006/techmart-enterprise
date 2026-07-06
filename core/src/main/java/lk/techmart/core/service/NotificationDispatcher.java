package lk.techmart.core.service;

import lk.techmart.core.dto.NotificationEvent;

public interface NotificationDispatcher {
    void dispatchEmail(NotificationEvent event);
    void dispatchPush(NotificationEvent event);
}
