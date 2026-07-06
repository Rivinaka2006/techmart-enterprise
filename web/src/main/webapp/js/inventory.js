
(function () {
    'use strict';

    const API_BASE = (window.getTmApiBase ? window.getTmApiBase() : '/techmart-web/api');

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

    async function loadInventoryDashboard() {
        try {
            const response = await fetch(`${API_BASE}/inventory/dashboard?transactionLimit=10`);
            const data = await parseJsonResponse(response);

            renderSummary(data);
            renderWarehouseStockList(data.warehouses || []);
            renderLowStockAlerts(data.lowStockProducts || []);
            renderInventoryTransactions(data.transactions || []);
        } catch (error) {
            console.error('Error loading inventory dashboard:', error);
            setStatus('inventoryTotalSkusStatus', 'Load failed', true);
            setStatus('inventoryLowStockStatus', 'Load failed', true);
            setStatus('inventoryOutOfStockStatus', 'Load failed', true);
            setStatus('inventoryStockRecordsStatus', 'Load failed', true);
            renderWarehouseStockList([]);
            renderLowStockAlerts([]);
            renderInventoryTransactions([]);
        }
    }

    async function openStockEntryModal() {
        const form = document.getElementById('stockEntryForm');
        const submitBtn = document.getElementById('stockEntrySubmitBtn');
        if (form) form.reset();
        showStockEntryError('');
        populateSelect('stockProductInput', [], 'id', 'name', 'Loading products...');
        populateSelect('stockWarehouseInput', [], 'id', 'warehouseName', 'Loading warehouses...');
        if (submitBtn) submitBtn.disabled = true;

        const modalEl = document.getElementById('stockEntryModal');
        if (modalEl && window.bootstrap) {
            bootstrap.Modal.getOrCreateInstance(modalEl).show();
        }

        try {
            const response = await fetch(`${API_BASE}/inventory/meta`);
            const meta = await parseJsonResponse(response);
            populateSelect('stockProductInput', meta.products || [], 'id', 'name', 'Select product');
            populateSelect('stockWarehouseInput', meta.warehouses || [], 'id', 'warehouseName', 'Select warehouse');
        } catch (error) {
            console.error('Error loading stock entry metadata:', error);
            showStockEntryError(error.message || 'Failed to load stock entry options.');
        } finally {
            if (submitBtn) submitBtn.disabled = false;
        }
    }

    async function submitStockEntry(event) {
        event.preventDefault();

        const form = event.currentTarget;
        const fields = form.elements;
        const submitBtn = document.getElementById('stockEntrySubmitBtn');
        const productId = Number(fields.namedItem('productId').value);
        const warehouseId = Number(fields.namedItem('warehouseId').value);
        const quantity = Number(fields.namedItem('quantity').value);

        if (!productId || !warehouseId || !Number.isInteger(quantity) || quantity <= 0) {
            showStockEntryError('Product, warehouse, and a positive whole-number quantity are required.');
            return;
        }

        if (submitBtn) submitBtn.disabled = true;
        showStockEntryError('');

        try {
            const params = new URLSearchParams({
                productId: String(productId),
                warehouseId: String(warehouseId),
                quantity: String(quantity)
            });
            const response = await fetch(`${API_BASE}/inventory/restock?${params.toString()}`, {
                method: 'POST'
            });
            await parseJsonResponse(response);

            const modalEl = document.getElementById('stockEntryModal');
            if (modalEl && window.bootstrap) {
                bootstrap.Modal.getOrCreateInstance(modalEl).hide();
            }
            await loadInventoryDashboard();
        } catch (error) {
            console.error('Error adding stock entry:', error);
            showStockEntryError(error.message || 'Failed to add stock entry.');
        } finally {
            if (submitBtn) submitBtn.disabled = false;
        }
    }

    function renderSummary(data) {
        const warehouseCount = numberValue(data.totalWarehouses);
        const lowStockCount = numberValue(data.lowStockCount);
        const outOfStockCount = numberValue(data.outOfStockCount);

        setText('inventoryTotalSkus', formatCount(data.totalSkus));
        setText('inventoryLowStockCount', formatCount(lowStockCount));
        setText('inventoryOutOfStockCount', formatCount(outOfStockCount));
        setText('inventoryStockRecords', formatCount(data.stockRecords));

        setStatus('inventoryTotalSkusStatus', `Across ${warehouseCount} warehouse${warehouseCount === 1 ? '' : 's'}`);
        setStatus('inventoryLowStockStatus', lowStockCount ? 'Needs reorder review' : 'No low stock');
        setStatus('inventoryOutOfStockStatus', outOfStockCount ? 'Immediate restock needed' : 'All tracked items available');
        setStatus('inventoryStockRecordsStatus', 'Loaded from warehouse stock');
    }

    function renderWarehouseStockList(warehouses) {
        const container = document.getElementById('warehouseStockList');
        if (!container) return;

        if (!warehouses.length) {
            container.innerHTML = '<div class="empty-state"><i class="bi bi-boxes"></i>No warehouse stock data available.</div>';
            return;
        }

        container.innerHTML = warehouses.map(function (warehouse) {
            const health = numberValue(warehouse.healthPercent);
            const statusClass = health >= 80 ? 'success' : health >= 50 ? 'warning' : 'danger';
            const totalUnits = formatCount(warehouse.totalUnits);
            const stockRecords = formatCount(warehouse.stockRecords);
            const lowStockRecords = formatCount(warehouse.lowStockRecords);

            return `
                <div>
                    <div class="d-flex justify-content-between align-items-center mb-2">
                        <div>
                            <strong>${escapeHtml(warehouse.name || 'Unknown warehouse')}</strong>
                            <div class="cell-sub">${escapeHtml(warehouse.location || 'No location')} · ${stockRecords} stock record${numberValue(warehouse.stockRecords) === 1 ? '' : 's'}</div>
                        </div>
                        <span class="badge-tm badge-tm-${statusClass}">${health}% healthy</span>
                    </div>
                    <div class="progress-tm mb-2">
                        <div class="progress-tm-bar ${statusClass}" style="width:${health}%"></div>
                    </div>
                    <div class="d-flex justify-content-between text-tertiary" style="font-size:12px;">
                        <span>${totalUnits} total units</span>
                        <span>${lowStockRecords} low-stock record${numberValue(warehouse.lowStockRecords) === 1 ? '' : 's'}</span>
                    </div>
                </div>
            `;
        }).join('');
    }

    function renderLowStockAlerts(products) {
        const container = document.getElementById('lowStockAlertsList');
        const badge = document.getElementById('lowStockAlertsBadge');
        if (!container) return;

        if (badge) {
            badge.textContent = `${products.length} active`;
        }

        if (!products.length) {
            container.innerHTML = '<div class="text-tertiary">No low-stock products returned by the backend.</div>';
            return;
        }

        container.innerHTML = products.map(function (product) {
            const qty = numberValue(product.quantity);
            const severityClass = qty <= 0 ? 'icon-bg-danger' : 'icon-bg-warning';
            const icon = qty <= 0 ? 'bi-x-octagon' : 'bi-exclamation-triangle';
            return `
                <div class="log-item">
                    <span class="log-icon ${severityClass}"><i class="bi ${icon}"></i></span>
                    <div>
                        <strong>${escapeHtml(product.name)}</strong>
                        <div class="cell-sub">${formatCount(qty)} unit${qty === 1 ? '' : 's'} available · threshold ${formatCount(product.threshold)}</div>
                    </div>
                </div>
            `;
        }).join('');
    }

    function renderInventoryTransactions(transactions) {
        const body = document.getElementById('inventoryTransactionsBody');
        if (!body) return;

        if (!transactions || !transactions.length) {
            body.innerHTML = '<tr><td colspan="5" class="text-center text-tertiary">No stock movement data available.</td></tr>';
            return;
        }

        body.innerHTML = transactions.map(function (txn) {
            const quantityChange = numberValue(txn.quantityChange);
            const quantity = Math.abs(quantityChange);
            const isPositive = quantityChange >= 0;
            const movementText = formatMovement(txn.transactionType);
            const movementClass = isPositive ? 'success' : 'danger';
            const movementIcon = isPositive ? 'bi-box-arrow-in-down' : 'bi-box-arrow-up';
            const timestamp = txn.createdAt ? new Date(txn.createdAt).toLocaleString() : '--';

            return `
                <tr>
                    <td class="mono text-secondary">${escapeHtml(timestamp)}</td>
                    <td class="cell-main">${escapeHtml(txn.productName || 'Unknown product')}</td>
                    <td class="cell-sub">${escapeHtml(txn.warehouseName || 'Unknown warehouse')}</td>
                    <td><span class="badge-tm badge-tm-${movementClass}"><i class="bi ${movementIcon}"></i> ${escapeHtml(movementText)}</span></td>
                    <td class="${isPositive ? 'text-success' : 'text-danger'} fw-semibold">${isPositive ? '+' : '-'}${formatCount(quantity)}</td>
                </tr>
            `;
        }).join('');
    }

    function populateSelect(id, items, valueKey, labelKey, placeholder) {
        const select = document.getElementById(id);
        if (!select) return;

        select.innerHTML = `<option value="">${escapeHtml(placeholder)}</option>`;
        items.forEach(function (item) {
            const option = document.createElement('option');
            option.value = item[valueKey];
            option.textContent = item[labelKey] || `#${item[valueKey]}`;
            select.appendChild(option);
        });
    }

    function showStockEntryError(message) {
        const error = document.getElementById('stockEntryError');
        if (!error) return;

        error.textContent = message || '';
        error.classList.toggle('d-none', !message);
    }

    function formatMovement(type) {
        if (type === 'RESTOCK') return 'Restock';
        if (type === 'ORDER_DEDUCTION') return 'Order deduction';
        if (!type) return 'Adjustment';
        return String(type).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, function (letter) {
            return letter.toUpperCase();
        });
    }

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    }

    function setStatus(id, text, isError) {
        const el = document.getElementById(id);
        if (!el) return;
        el.className = `kpi-trend ${isError ? 'down' : 'flat'}`;
        el.innerHTML = `<i class="bi ${isError ? 'bi-exclamation-triangle' : 'bi-check2'}"></i> ${escapeHtml(text)}`;
    }

    function numberValue(value) {
        const parsed = Number(value || 0);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function formatCount(value) {
        return numberValue(value).toLocaleString();
    }

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    document.addEventListener('DOMContentLoaded', function () {
        const newStockEntryBtn = document.getElementById('newStockEntryBtn');
        if (newStockEntryBtn) newStockEntryBtn.addEventListener('click', openStockEntryModal);

        const stockEntryForm = document.getElementById('stockEntryForm');
        if (stockEntryForm) stockEntryForm.addEventListener('submit', submitStockEntry);

        loadInventoryDashboard();
    });
})();
