
(function () {
    'use strict';

    const API_BASE = window.getTmApiBase ? window.getTmApiBase() : '/techmart-web/api';
    let currentSnapshot = null;

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

    async function loadSettings() {
        try {
            const response = await fetch(`${API_BASE}/settings/runtime`);
            const data = await parseJsonResponse(response);
            currentSnapshot = data;

            renderDatabase(data.database || {});
            renderJms(data.jms || {});
            renderSessions(data.sessions || {});
            renderNotifications(data.notifications || {});
            renderThresholds(data.thresholds || {});
            renderStatus(data.status || {});
            renderRuntimeSource(data);
        } catch (error) {
            console.error('Error loading runtime settings:', error);
            markLoadFailed();
        }
    }

    function renderDatabase(database) {
        setValue('settingsPersistenceUnit', database.persistenceUnit);
        setValue('settingsDataSource', database.jtaDataSource);
        setValue('settingsJpaProvider', database.provider);
        setValue('settingsSchemaGeneration', database.schemaGeneration);
        setValue('settingsDatabaseProduct', [database.productName, database.productVersion].filter(Boolean).join(' ') || '--');
        setValue('settingsDatabaseDriver', [database.driverName, database.driverVersion].filter(Boolean).join(' ') || '--');
        setValue('settingsJdbcUrl', database.url || '--');

        setBadge('settingsDatabaseBadge', database.connected ? 'Connected' : 'Unavailable', database.connected ? 'success' : 'danger', database.connected);
        setText('settingsDatabaseHint', database.connected ? 'Datasource lookup and connection validation succeeded.' : `Datasource validation failed: ${database.error || 'resource unavailable'}`);
    }

    function renderJms(jms) {
        const orderQueue = jms.orderQueue || {};
        const inventoryTopic = jms.inventoryTopic || {};

        setValue('settingsOrderQueue', orderQueue.jndiName);
        setValue('settingsOrderConsumer', orderQueue.consumer);
        setValue('settingsInventoryTopic', inventoryTopic.jndiName);
        setValue('settingsTopicProducer', inventoryTopic.producer);

        const jmsReady = !!(orderQueue.available && inventoryTopic.available);
        setBadge('settingsJmsBadge', jmsReady ? 'Resources Found' : 'Check JNDI', jmsReady ? 'success' : 'warning', jmsReady);
        renderSubscribers(jms.subscribers || []);
    }

    function renderSubscribers(subscribers) {
        const body = document.getElementById('settingsSubscriberBody');
        if (!body) return;
        if (!subscribers.length) {
            body.innerHTML = '<tr><td colspan="5" class="text-center text-tertiary">No JMS subscribers configured.</td></tr>';
            return;
        }

        body.innerHTML = subscribers.map(function (subscriber) {
            return `
                <tr>
                    <td class="cell-main">${escapeHtml(subscriber.bean)}</td>
                    <td class="mono">${escapeHtml(subscriber.clientId)}</td>
                    <td class="mono">${escapeHtml(subscriber.subscriptionName)}</td>
                    <td>${escapeHtml(subscriber.durability)}</td>
                    <td class="mono">${formatCount(subscriber.messagesRecorded)}</td>
                </tr>
            `;
        }).join('');
    }

    function renderSessions(sessions) {
        setValue('settingsCartTimeout', `${formatCount(sessions.cartStatefulTimeoutMinutes)} minutes`);
        setValue('settingsActiveSessions', formatCount(sessions.activeSessions));
        setValue('settingsIdleSessions', formatCount(sessions.idleSessions));
        setValue('settingsExpiredSessions', formatCount(sessions.expiredSessions));
        setValue('settingsExpireEndpoint', sessions.autoExpireEndpoint);
    }

    function renderNotifications(notifications) {
        setValue('settingsGatewayThreshold', `${formatCount(notifications.circuitBreakerFailureThreshold)} failures`);
        setValue('settingsGatewayReset', `${formatCount(notifications.circuitBreakerResetTimeoutSeconds)} seconds`);
        setValue('settingsAdminEmail', notifications.adminEmailRecipient);
        setValue('settingsAdminPushUser', notifications.adminPushUser);

        const gateways = [notifications.emailGateway, notifications.pushGateway].filter(Boolean);
        const body = document.getElementById('settingsGatewayBody');
        if (!body) return;
        if (!gateways.length) {
            body.innerHTML = '<tr><td colspan="4" class="text-center text-tertiary">No gateway metrics recorded.</td></tr>';
            return;
        }

        body.innerHTML = gateways.map(function (gateway) {
            return `
                <tr>
                    <td class="cell-main">${escapeHtml(gateway.name)}</td>
                    <td class="mono">${formatCount(gateway.totalCalls)}</td>
                    <td class="mono">${formatCount(gateway.failures)}</td>
                    <td class="mono">${formatPercent(gateway.successRate)}</td>
                </tr>
            `;
        }).join('');
    }

    function renderThresholds(thresholds) {
        setValue('settingsHighLoadThreshold', `${formatCount(thresholds.resourceHighLoadPercent)}%`);
        setValue('settingsCriticalThreshold', `${formatCount(thresholds.resourceCriticalPercent)}%`);
        setValue('settingsLowStockThreshold', `${formatCount(thresholds.inventoryLowStockThreshold)} units`);
        setValue('settingsThresholdGatewayReset', `${formatCount(thresholds.gatewayResetTimeoutSeconds)} seconds`);
    }

    function renderStatus(status) {
        setStatusBadge('settingsStatusDatabase', status.database);
        setStatusBadge('settingsStatusJms', status.jmsBroker);
        setStatusBadge('settingsStatusSessions', status.sessionStore);
        setStatusBadge('settingsStatusNotifications', status.notificationChannels);
    }

    function renderRuntimeSource(data) {
        const container = document.getElementById('settingsRuntimeSource');
        if (!container) return;
        const database = data.database || {};
        const sessions = data.sessions || {};
        const uptime = formatUptime(sessions.uptimeSeconds);

        container.innerHTML = `
            <div class="log-item"><span class="log-time">DB</span><span class="log-icon icon-bg-accent"><i class="bi bi-database"></i></span><div>Datasource <span class="mono">${escapeHtml(database.jtaDataSource || '--')}</span> checked through JNDI.</div></div>
            <div class="log-item"><span class="log-time">JMS</span><span class="log-icon icon-bg-success"><i class="bi bi-envelope-paper"></i></span><div>Queue and topic names are loaded from the deployed JMS code paths.</div></div>
            <div class="log-item"><span class="log-time">Run</span><span class="log-icon icon-bg-warning"><i class="bi bi-clock-history"></i></span><div>System health monitor uptime is <span class="mono">${escapeHtml(uptime)}</span>.</div></div>
        `;
    }

    function exportSnapshot() {
        if (!currentSnapshot) return;
        const blob = new Blob([JSON.stringify(currentSnapshot, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'techmart-runtime-settings.json';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    function markLoadFailed() {
        [
            'settingsPersistenceUnit',
            'settingsDataSource',
            'settingsJpaProvider',
            'settingsSchemaGeneration',
            'settingsDatabaseProduct',
            'settingsDatabaseDriver',
            'settingsJdbcUrl',
            'settingsOrderQueue',
            'settingsOrderConsumer',
            'settingsInventoryTopic',
            'settingsTopicProducer',
            'settingsCartTimeout',
            'settingsActiveSessions',
            'settingsIdleSessions',
            'settingsExpiredSessions',
            'settingsExpireEndpoint'
        ].forEach(function (id) {
            setValue(id, '--');
        });
        setBadge('settingsDatabaseBadge', 'Load Failed', 'danger', false);
        setBadge('settingsJmsBadge', 'Load Failed', 'danger', false);
        setText('settingsDatabaseHint', 'Unable to load runtime settings.');
    }

    function setStatusBadge(id, value) {
        const text = value || 'Unknown';
        const normalized = text.toUpperCase();
        if (normalized === 'VALIDATED' || normalized === 'HEALTHY') {
            setBadge(id, text, 'success', true);
        } else if (normalized === 'ATTENTION') {
            setBadge(id, text, 'warning', false);
        } else {
            setBadge(id, text, 'danger', false);
        }
    }

    function setBadge(id, text, tone, ok) {
        const el = document.getElementById(id);
        if (!el) return;
        const dot = ok ? 'dot-success' : tone === 'warning' ? 'dot-warning' : 'dot-danger';
        el.className = `badge-tm badge-tm-${tone}`;
        el.innerHTML = `<span class="dot ${dot}"></span> ${escapeHtml(text)}`;
    }

    function setValue(id, value) {
        const el = document.getElementById(id);
        if (el) el.value = value == null || value === '' ? '--' : value;
    }

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value == null || value === '' ? '--' : value;
    }

    function formatCount(value) {
        return Math.round(numberValue(value)).toLocaleString();
    }

    function formatPercent(value) {
        return `${numberValue(value).toFixed(2)}%`;
    }

    function formatUptime(value) {
        let seconds = Math.max(0, numberValue(value));
        const days = Math.floor(seconds / 86400);
        seconds %= 86400;
        const hours = Math.floor(seconds / 3600);
        seconds %= 3600;
        const minutes = Math.floor(seconds / 60);
        if (days) return `${days}d ${hours}h`;
        if (hours) return `${hours}h ${minutes}m`;
        return `${minutes}m`;
    }

    function numberValue(value) {
        const parsed = Number(value || 0);
        return Number.isFinite(parsed) ? parsed : 0;
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
        loadSettings();

        const refresh = document.getElementById('settingsRefresh');
        if (refresh) refresh.addEventListener('click', loadSettings);

        const exportButton = document.getElementById('settingsExport');
        if (exportButton) exportButton.addEventListener('click', exportSnapshot);
    });
})();
