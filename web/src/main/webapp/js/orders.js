
(function () {
    'use strict';

    const API_BASE = (window.getTmApiBase ? window.getTmApiBase() : '/techmart-web/api');
    const PAGE_SIZE = 25;
    let currentOffset = 0;
    let currentStatus = null;
    let lastLoadedCount = 0;
    let totalOrders = 0;

    async function parseJsonResponse(response) {
        const text = await response.text();
        if (!response.ok) {
            let message = `Request failed: ${response.status} ${response.statusText}`;
            if (text) {
                try {
                    const body = JSON.parse(text);
                    if (body && body.error) message += ` - ${body.error}`;
                } catch (e) {
                    message += ` - ${text.trim().slice(0, 120)}`;
                }
            }
            throw new Error(message);
        }
        return text ? JSON.parse(text) : {};
    }

    function readCount(data, key) {
        const value = data ? data[key] : null;
        if (value === null || value === undefined || Number.isNaN(Number(value))) {
            throw new Error(`Missing numeric field: ${key}`);
        }
        return Number(value);
    }

    function setKpiValue(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = Number(value || 0).toLocaleString();
    }

    function setKpiTrend(id, text, state = 'flat', icon = 'bi-check2') {
        const el = document.getElementById(id);
        if (!el) return;
        el.className = `kpi-trend ${state}`;
        el.innerHTML = `<i class="bi ${icon}"></i> ${escapeHtml(text)}`;
    }

    function setKpiError(valueId, trendId) {
        setKpiValue(valueId, 0);
        setKpiTrend(trendId, 'Load failed', 'down', 'bi-exclamation-triangle');
    }

    async function loadOrders() {
        try {
            const params = new URLSearchParams({
                offset: String(currentOffset),
                limit: String(PAGE_SIZE)
            });
            if (currentStatus) params.set('status', currentStatus);

            const response = await fetch(`${API_BASE}/orders?${params.toString()}`);
            const orders = await parseJsonResponse(response);
            lastLoadedCount = Array.isArray(orders) ? orders.length : 0;
            renderOrdersTable(Array.isArray(orders) ? orders : []);
            renderPagination();
        } catch (error) {
            console.error('Error loading orders:', error);
            renderOrdersTable([]);
            setText('ordersTableSummary', 'Failed to load orders.');
        }
    }

    async function loadKpiStats() {
        try {
            const response = await fetch(`${API_BASE}/orders/stats`);
            const stats = await parseJsonResponse(response);

            const ordersToday = readCount(stats, 'ordersToday');
            const processing = readCount(stats, 'processing');
            const completedToday = readCount(stats, 'completedToday');
            const cancelled = readCount(stats, 'cancelled');
            totalOrders = Number(stats.totalOrders || 0);

            setKpiValue('ordersTodayValue', ordersToday);
            setKpiValue('processingValue', processing);
            setKpiValue('completedTodayValue', completedToday);
            setKpiValue('cancelledValue', cancelled);

            setKpiTrend('ordersTodayTrend', 'Created today');
            setKpiTrend('processingTrend', processing ? 'Awaiting fulfillment' : 'No processing orders');
            setKpiTrend(
                'completedTodayTrend',
                ordersToday ? `${Math.round((completedToday / ordersToday) * 100)}% of today` : 'No orders today',
                completedToday > 0 ? 'up' : 'flat',
                completedToday > 0 ? 'bi-arrow-up-short' : 'bi-dash'
            );
            setKpiTrend(
                'cancelledTrend',
                totalOrders ? `${((cancelled / totalOrders) * 100).toFixed(1)}% of total` : 'No cancellations',
                cancelled > 0 ? 'down' : 'flat',
                cancelled > 0 ? 'bi-arrow-up-short' : 'bi-dash'
            );
        } catch (error) {
            console.error('Error loading KPI stats:', error);
            setKpiError('ordersTodayValue', 'ordersTodayTrend');
            setKpiError('processingValue', 'processingTrend');
            setKpiError('completedTodayValue', 'completedTodayTrend');
            setKpiError('cancelledValue', 'cancelledTrend');
        }
    }

    async function loadOrderDetails(orderId) {
        try {
            const response = await fetch(`${API_BASE}/orders/${orderId}`);
            const order = await parseJsonResponse(response);
            showOrderDetailsModal(order);
        } catch (error) {
            console.error('Error loading order details:', error);
            alert('Failed to load order details');
        }
    }

    async function updateOrderStatus(orderId, newStatus, comment) {
        try {
            const response = await fetch(`${API_BASE}/orders/${orderId}/status`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ status: newStatus, comment: comment })
            });
            await parseJsonResponse(response);
            await Promise.all([loadOrders(), loadKpiStats()]);
        } catch (error) {
            console.error('Error updating order status:', error);
            alert('Failed to update order status');
        }
    }

    function renderOrdersTable(orders) {
        const tbody = document.getElementById('ordersTableBody');
        if (!tbody) return;

        if (!orders.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-tertiary">No orders found.</td></tr>';
            return;
        }

        tbody.innerHTML = orders.map(order => `
            <tr>
                <td class="cell-id">ORD-${escapeHtml(order.id)}</td>
                <td>
                    <div class="cell-main">${escapeHtml(order.customerName || 'Unknown customer')}</div>
                    <div class="cell-sub">${escapeHtml(order.customerEmail || '')}</div>
                </td>
                <td class="mono cell-sub">${formatDate(order.createdAt)}</td>
                <td><span class="badge-tm ${getStatusBadgeClass(order.status)}">${escapeHtml(formatStatus(order.status))}</span></td>
                <td class="mono">${Number(order.itemCount || 0).toLocaleString()}</td>
                <td class="mono">${formatMoney(order.totalAmount)}</td>
                <td>
                    <div class="row-actions">
                        <button class="btn-icon-tm" title="View" data-order-action="view" data-order-id="${escapeHtml(order.id)}"><i class="bi bi-eye"></i></button>
                        <button class="btn-icon-tm" title="Update status" data-order-action="status" data-order-id="${escapeHtml(order.id)}" data-order-status="${escapeHtml(order.status)}"><i class="bi bi-pencil"></i></button>
                    </div>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('[data-order-action="view"]').forEach(button => {
            button.addEventListener('click', () => loadOrderDetails(button.getAttribute('data-order-id')));
        });
        tbody.querySelectorAll('[data-order-action="status"]').forEach(button => {
            button.addEventListener('click', () => promptStatusChange(
                button.getAttribute('data-order-id'),
                button.getAttribute('data-order-status')
            ));
        });
    }

    function renderPagination() {
        const pageNumber = Math.floor(currentOffset / PAGE_SIZE) + 1;
        const start = lastLoadedCount ? currentOffset + 1 : 0;
        const end = currentOffset + lastLoadedCount;
        const summary = currentStatus
            ? `Showing ${start}-${end} ${currentStatus.toLowerCase()} order${lastLoadedCount === 1 ? '' : 's'}`
            : `Showing ${start}-${end} of ${Number(totalOrders || 0).toLocaleString()} orders`;

        setText('ordersTableSummary', summary);
        setText('ordersPageBtn', String(pageNumber));

        const prev = document.getElementById('ordersPrevBtn');
        const next = document.getElementById('ordersNextBtn');
        if (prev) prev.disabled = currentOffset === 0;
        if (next) next.disabled = lastLoadedCount < PAGE_SIZE || (!currentStatus && currentOffset + lastLoadedCount >= totalOrders);
    }

    function promptStatusChange(orderId, currentStatusValue) {
        const statuses = ['PROCESSING', 'COMPLETED', 'CANCELLED'];
        const newStatus = prompt(`Current status: ${currentStatusValue}\n\nEnter new status (${statuses.join(', ')}):`);
        if (!newStatus || !statuses.includes(newStatus.toUpperCase())) {
            alert('Invalid status');
            return;
        }
        const comment = prompt('Enter comment (optional):') || '';
        updateOrderStatus(orderId, newStatus.toUpperCase(), comment);
    }

    function showOrderDetailsModal(order) {
        const existing = document.getElementById('orderDetailsModal');
        if (existing) existing.remove();

        const modal = document.createElement('div');
        modal.className = 'modal fade';
        modal.id = 'orderDetailsModal';
        modal.tabIndex = -1;
        modal.innerHTML = `
            <div class="modal-dialog modal-lg modal-dialog-scrollable">
                <div class="modal-content" style="background:var(--card);color:var(--text-primary);border:1px solid var(--border);">
                    <div class="modal-header" style="border-bottom:1px solid var(--border);">
                        <div>
                            <h5 class="modal-title">Order ORD-${escapeHtml(order.id)}</h5>
                            <div class="cell-sub">${escapeHtml(order.customerEmail || '')}</div>
                        </div>
                        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <div class="row g-3 mb-4">
                            <div class="col-md-4"><div class="cell-sub">Customer</div><strong>${escapeHtml(order.customerName || 'Unknown')}</strong></div>
                            <div class="col-md-4"><div class="cell-sub">Status</div><span class="badge-tm ${getStatusBadgeClass(order.status)}">${escapeHtml(formatStatus(order.status))}</span></div>
                            <div class="col-md-4"><div class="cell-sub">Total</div><strong class="mono">${formatMoney(order.totalAmount)}</strong></div>
                        </div>
                        <h6>Order Items</h6>
                        <div class="table-tm-wrap mb-4">
                            <table class="table-tm">
                                <thead><tr><th>Product</th><th>Qty</th><th>Price</th><th>Subtotal</th></tr></thead>
                                <tbody>${renderDetailItems(order.items || [])}</tbody>
                            </table>
                        </div>
                        <h6>Status History</h6>
                        <div class="table-tm-wrap">
                            <table class="table-tm">
                                <thead><tr><th>Changed At</th><th>From</th><th>To</th><th>Notes</th></tr></thead>
                                <tbody>${renderStatusHistory(order.statusHistory || [])}</tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
        bootstrap.Modal.getOrCreateInstance(modal).show();
    }

    function renderDetailItems(items) {
        if (!items.length) return '<tr><td colspan="4" class="text-center text-tertiary">No items found.</td></tr>';
        return items.map(item => {
            const qty = Number(item.quantity || 0);
            const price = Number(item.pricePerUnit || 0);
            return `
                <tr>
                    <td>${escapeHtml(item.productName || 'Unknown product')}</td>
                    <td class="mono">${qty.toLocaleString()}</td>
                    <td class="mono">${formatMoney(price)}</td>
                    <td class="mono">${formatMoney(qty * price)}</td>
                </tr>
            `;
        }).join('');
    }

    function renderStatusHistory(history) {
        if (!history.length) return '<tr><td colspan="4" class="text-center text-tertiary">No status history found.</td></tr>';
        return history.map(item => `
            <tr>
                <td class="mono cell-sub">${formatDate(item.changedAt)}</td>
                <td>${escapeHtml(item.oldStatus || 'N/A')}</td>
                <td>${escapeHtml(item.newStatus || 'N/A')}</td>
                <td>${escapeHtml(item.notes || '')}</td>
            </tr>
        `).join('');
    }

    function getStatusBadgeClass(status) {
        switch (status) {
            case 'PROCESSING': return 'badge-tm-accent';
            case 'COMPLETED': return 'badge-tm-success';
            case 'CANCELLED': return 'badge-tm-danger';
            case 'REFUNDED': return 'badge-tm-neutral';
            default: return 'badge-tm-neutral';
        }
    }

    function formatStatus(status) {
        return status ? String(status).replace(/_/g, ' ') : 'Unknown';
    }

    function formatDate(value) {
        if (!value) return '--';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '--';
        return date.toLocaleString();
    }

    function formatMoney(value) {
        return 'LKR ' + Number(value || 0).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    }

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function bindControls() {
        const filter = document.getElementById('orderStatusFilter');
        if (filter) {
            filter.addEventListener('change', () => {
                currentStatus = filter.value === 'all' ? null : filter.value;
                currentOffset = 0;
                loadOrders();
            });
        }

        const refresh = document.getElementById('ordersRefreshBtn');
        if (refresh) {
            refresh.addEventListener('click', () => Promise.all([loadOrders(), loadKpiStats()]));
        }

        const prev = document.getElementById('ordersPrevBtn');
        if (prev) {
            prev.addEventListener('click', () => {
                currentOffset = Math.max(0, currentOffset - PAGE_SIZE);
                loadOrders();
            });
        }

        const next = document.getElementById('ordersNextBtn');
        if (next) {
            next.addEventListener('click', () => {
                currentOffset += PAGE_SIZE;
                loadOrders();
            });
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        bindControls();
        Promise.all([loadKpiStats(), loadOrders()]);
        setInterval(() => Promise.all([loadKpiStats(), loadOrders()]), 30000);
    });
})();
