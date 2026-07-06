
(function () {
    "use strict";

    var API_BASE = (window.getTmApiBase ? window.getTmApiBase() : "/techmart-web/api") + "/cart";
    var NOTIFICATION_STORE_KEY = "techmartNotifications";

    function refreshCart() {
        return fetch(API_BASE)
            .then(function (res) { return res.ok ? res.json() : { items: [], total: 0 }; })
            .then(renderCart);
    }

    function addToCart(productId, quantity) {
        quantity = quantity || 1;
        fetch(API_BASE + "/items", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ productId: productId, quantity: quantity })
        })
            .then(function (res) {
                if (res.status === 401) { window.location.reload(); return; } 
                if (!res.ok) return res.json().then(function (e) { throw new Error(e.error || "Failed to add item"); });
                return res.json();
            })
            .then(function (data) { if (data) { renderCart(data); openCartPanel(); } })
            .catch(function (err) {
                console.error("addToCart failed:", err);
                addNotification({
                    type: "CART_ADD_FAILED",
                    title: "Cannot add product",
                    message: err.message || "Product cannot be added to cart.",
                    detail: "Check product status and available quantity.",
                    icon: "bi-cart-x",
                    severity: "danger"
                });
                openNotificationPanel();
            });
    }
    window.addToCart = addToCart;

    function updateQuantity(productId, quantity) {
        fetch(API_BASE + "/items/" + productId, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ quantity: quantity })
        }).then(function (res) { return res.json(); }).then(renderCart);
    }

    function removeItem(productId) {
        fetch(API_BASE + "/items/" + productId, { method: "DELETE" })
            .then(function (res) { return res.json(); }).then(renderCart);
    }

    function checkout() {
        fetch(API_BASE + "/checkout", { method: "POST" })
            .then(function (res) {
                if (!res.ok) return res.json().then(function (e) { throw new Error(e.error || "Checkout failed"); });
                return res.json();
            })
            .then(function (data) {
                addNotification({
                    type: "ORDER_SUBMITTED",
                    title: "Order queued",
                    message: data.message || "Order submitted for processing.",
                    detail: "Checkout message was sent to jms/queue/OrderQueue.",
                    icon: "bi-receipt-cutoff"
                });
                renderCart({ items: [], total: 0 });
                closeCartPanel();
                openNotificationPanel();
            })
            .catch(function (err) {
                addNotification({
                    type: "ORDER_FAILED",
                    title: "Checkout failed",
                    message: err.message,
                    detail: "The order was not queued.",
                    icon: "bi-exclamation-triangle",
                    severity: "danger"
                });
                openNotificationPanel();
            });
    }

    

    function getNotifications() {
        try {
            return JSON.parse(localStorage.getItem(NOTIFICATION_STORE_KEY) || "[]");
        } catch (e) {
            return [];
        }
    }

    function saveNotifications(notifications) {
        localStorage.setItem(NOTIFICATION_STORE_KEY, JSON.stringify(notifications.slice(0, 25)));
    }

    function addNotification(notification) {
        var notifications = getNotifications();
        notifications.unshift({
            id: Date.now(),
            type: notification.type,
            title: notification.title,
            message: notification.message,
            detail: notification.detail,
            icon: notification.icon || "bi-bell",
            severity: notification.severity || "success",
            createdAt: new Date().toISOString(),
            read: false
        });
        saveNotifications(notifications);
        renderNotifications();
    }

    function markNotificationsRead() {
        var notifications = getNotifications().map(function (notification) {
            notification.read = true;
            return notification;
        });
        saveNotifications(notifications);
        renderNotifications();
    }

    function clearNotifications() {
        saveNotifications([]);
        renderNotifications();
    }

    function formatNotificationTime(value) {
        var date = new Date(value);
        if (Number.isNaN(date.getTime())) return "";
        return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    }

    function injectNotificationUI() {
        var bell = document.querySelector('.topbar-right .icon-btn[aria-label="Notifications"]');
        if (!bell || document.getElementById("notificationPanel")) return;

        bell.id = "notificationToggleBtn";
        bell.addEventListener("click", function () {
            toggleNotificationPanel();
            markNotificationsRead();
        });

        var panel = document.createElement("div");
        panel.id = "notificationPanel";
        panel.className = "notification-panel";
        panel.innerHTML =
            '<div class="notification-panel-header">' +
            '<div>' +
            '<p class="card-tm-title mb-0">Notifications</p>' +
            '<p class="card-tm-subtitle">Order and JMS processing updates</p>' +
            '</div>' +
            '<button class="icon-btn" id="notificationCloseBtn"><i class="bi bi-x-lg"></i></button>' +
            '</div>' +
            '<div class="notification-panel-body" id="notificationPanelBody"></div>' +
            '<div class="notification-panel-footer">' +
            '<button class="btn-tm btn-tm-sm" id="notificationClearBtn"><i class="bi bi-trash3"></i> Clear</button>' +
            '</div>';
        document.body.appendChild(panel);

        document.getElementById("notificationCloseBtn").addEventListener("click", closeNotificationPanel);
        document.getElementById("notificationClearBtn").addEventListener("click", clearNotifications);
        renderNotifications();
    }

    function openNotificationPanel() {
        var panel = document.getElementById("notificationPanel");
        if (panel) {
            panel.classList.add("open");
            markNotificationsRead();
        }
    }

    function closeNotificationPanel() {
        var panel = document.getElementById("notificationPanel");
        if (panel) panel.classList.remove("open");
    }

    function toggleNotificationPanel() {
        var panel = document.getElementById("notificationPanel");
        if (!panel) return;
        if (panel.classList.contains("open")) {
            closeNotificationPanel();
        } else {
            panel.classList.add("open");
        }
    }

    function renderNotifications() {
        var notifications = getNotifications();
        var unreadCount = notifications.filter(function (notification) { return !notification.read; }).length;
        var dot = document.querySelector("#notificationToggleBtn .notif-dot");
        if (dot) {
            dot.textContent = unreadCount > 9 ? "9+" : unreadCount ? String(unreadCount) : "";
            dot.classList.toggle("visible", unreadCount > 0);
        }

        var body = document.getElementById("notificationPanelBody");
        if (!body) return;

        body.innerHTML = notifications.length ? notifications.map(function (notification) {
            var severityClass = notification.severity === "danger" ? "icon-bg-danger" : "icon-bg-success";
            return '<div class="notification-item ' + (notification.read ? "" : "unread") + '">' +
                '<span class="notification-icon ' + severityClass + '"><i class="bi ' + notification.icon + '"></i></span>' +
                '<div class="notification-content">' +
                '<div class="notification-title-row">' +
                '<strong>' + escapeHtml(notification.title) + '</strong>' +
                '<span class="notification-time mono">' + formatNotificationTime(notification.createdAt) + '</span>' +
                '</div>' +
                '<div class="notification-message">' + escapeHtml(notification.message) + '</div>' +
                '<div class="notification-detail">' + escapeHtml(notification.detail || "") + '</div>' +
                '</div>' +
                '</div>';
        }).join("") : '<div class="empty-state"><i class="bi bi-bell"></i>No notifications yet.</div>';
    }

    function escapeHtml(value) {
        return String(value || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    function injectCartUI() {
        var topbarRight = document.querySelector(".topbar-right");
        if (!topbarRight || document.getElementById("cartToggleBtn")) return;

        var btn = document.createElement("button");
        btn.className = "icon-btn";
        btn.id = "cartToggleBtn";
        btn.setAttribute("aria-label", "Shopping cart");
        btn.innerHTML = '<i class="bi bi-cart3"></i><span class="cart-count-badge" id="cartCountBadge">0</span>';
        btn.addEventListener("click", toggleCartPanel);
        topbarRight.insertBefore(btn, topbarRight.firstChild);

        var panel = document.createElement("div");
        panel.id = "cartPanel";
        panel.className = "cart-panel";
        panel.innerHTML =
            '<div class="cart-panel-header">' +
            '<p class="card-tm-title mb-0">Your Cart</p>' +
            '<button class="icon-btn" id="cartCloseBtn"><i class="bi bi-x-lg"></i></button>' +
            '</div>' +
            '<div class="cart-panel-body" id="cartPanelBody"></div>' +
            '<div class="cart-panel-footer">' +
            '<div class="cart-total-row"><span>Total</span><span class="mono" id="cartTotalValue">LKR 0.00</span></div>' +
            '<button class="btn-tm btn-tm-primary w-100" id="cartCheckoutBtn">Checkout</button>' +
            '</div>';
        document.body.appendChild(panel);

        document.getElementById("cartCloseBtn").addEventListener("click", closeCartPanel);
        document.getElementById("cartCheckoutBtn").addEventListener("click", checkout);
    }

    function openCartPanel() { var p = document.getElementById("cartPanel"); if (p) p.classList.add("open"); }
    function closeCartPanel() { var p = document.getElementById("cartPanel"); if (p) p.classList.remove("open"); }
    function toggleCartPanel() {
        var p = document.getElementById("cartPanel");
        if (!p) return;
        if (p.classList.contains("open")) { closeCartPanel(); } else { refreshCart(); openCartPanel(); }
    }

    function renderCart(data) {
        var items = (data && data.items) || [];
        var total = (data && data.total) || 0;

        var badge = document.getElementById("cartCountBadge");
        if (badge) badge.textContent = items.reduce(function (sum, i) { return sum + i.quantity; }, 0);

        var body = document.getElementById("cartPanelBody");
        if (body) {
            body.innerHTML = items.length ? items.map(function (item) {
                return '<div class="cart-line-item">' +
                    '<div class="cart-line-main">' +
                    '<strong>' + item.productName + '</strong>' +
                    '<div class="cell-sub mono">' + formatCurrency(item.pricePerUnit) + ' each</div>' +
                    '</div>' +
                    '<div class="cart-line-controls">' +
                    '<input type="number" min="1" value="' + item.quantity + '" class="cart-qty-input" data-product-id="' + item.productId + '">' +
                    '<button class="btn-icon-tm cart-remove-btn" data-product-id="' + item.productId + '" title="Remove"><i class="bi bi-trash3"></i></button>' +
                    '</div>' +
                    '</div>';
            }).join("") : '<p class="text-tertiary" style="font-size:13px;">Your cart is empty.</p>';

            body.querySelectorAll(".cart-qty-input").forEach(function (input) {
                input.addEventListener("change", function () {
                    updateQuantity(parseInt(input.getAttribute("data-product-id"), 10), parseInt(input.value, 10) || 1);
                });
            });
            body.querySelectorAll(".cart-remove-btn").forEach(function (btn) {
                btn.addEventListener("click", function () {
                    removeItem(parseInt(btn.getAttribute("data-product-id"), 10));
                });
            });
        }

        var totalEl = document.getElementById("cartTotalValue");
        if (totalEl) totalEl.textContent = formatCurrency(total);
    }

    function formatCurrency(value) {
        return "LKR " + Number(value || 0).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        injectNotificationUI();
        injectCartUI();
        refreshCart();
    });
})();
