
(function () {
    'use strict';

    const API_BASE = (window.getTmApiBase ? window.getTmApiBase() : '/techmart-web/api');
    let queueChart;
    let deliveryChart;

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

    async function loadMessagingDashboard() {
        try {
            const response = await fetch(`${API_BASE}/metrics/messaging-dashboard?limit=75`);
            const data = await parseJsonResponse(response);

            renderSummary(data.summary || {});
            renderDelivery(data.delivery || {});
            renderStreams(data.destinations || []);
            renderActivity(data.activity || []);
            renderCharts(data.throughput || [], data.delivery || {});
        } catch (error) {
            console.error('Error loading messaging dashboard:', error);
            setKpiError('messagingTrackedDestinations', 'messagingTrackedDestinationsTrend');
            setKpiError('messagingAvgProcessing', 'messagingAvgProcessingTrend');
            setKpiError('messagingProcessedToday', 'messagingProcessedTodayTrend');
            setKpiError('messagingFailedToday', 'messagingFailedTodayTrend');
            renderStreams([]);
            renderActivity([]);
        }
    }

    function renderSummary(summary) {
        const processed = numberValue(summary.processedToday);
        const failed = numberValue(summary.failedToday);

        setText('messagingTrackedDestinations', formatCount(summary.trackedDestinations));
        setText('messagingAvgProcessing', `${formatCount(summary.avgProcessingMs)} ms`);
        setText('messagingProcessedToday', formatCount(processed));
        setText('messagingFailedToday', formatCount(failed));

        setKpiTrend('messagingTrackedDestinationsTrend', 'From message_logs');
        setKpiTrend('messagingAvgProcessingTrend', 'Average processing time');
        setKpiTrend('messagingProcessedTodayTrend', 'Recorded today', processed ? 'up' : 'flat', processed ? 'bi-arrow-up-short' : 'bi-dash');
        setKpiTrend(
            'messagingFailedTodayTrend',
            processed ? `${formatPercent(failed, processed)} failure rate` : 'No failures recorded',
            failed ? 'down' : 'flat',
            failed ? 'bi-arrow-up-short' : 'bi-check2'
        );
    }

    function renderDelivery(delivery) {
        setText('messagingTopicMessages', formatCount(delivery.topicMessages));
        setText('messagingGatewayCalls', formatCount(delivery.gatewayCalls));
        setText('messagingSuccessRate', `${numberValue(delivery.successRate).toFixed(2)}%`);

        setKpiTrend('messagingTopicMessagesTrend', 'InventoryUpdatesTopic consumers');
        setKpiTrend('messagingGatewayCallsTrend', 'Email and push dispatch');
        setKpiTrend('messagingSuccessRateTrend', 'Calculated from message_logs');
    }

    function renderStreams(streams) {
        const body = document.getElementById('messagingStreamsBody');
        if (!body) return;

        if (!streams.length) {
            body.innerHTML = '<tr><td colspan="6" class="text-center text-tertiary">No messaging records found.</td></tr>';
            return;
        }

        body.innerHTML = streams.map(function (stream) {
            const failed = numberValue(stream.failed);
            const statusClass = failed ? 'badge-tm-warning' : 'badge-tm-success';
            const statusText = failed ? 'Attention' : 'Healthy';
            return `
                <tr>
                    <td>
                        <div class="cell-main mono">${escapeHtml(stream.messageType)}</div>
                        <div class="cell-sub">${escapeHtml(stream.destinationName)}</div>
                    </td>
                    <td>${escapeHtml(stream.destinationType)}</td>
                    <td class="mono">${formatCount(stream.processed)}</td>
                    <td class="mono">${formatCount(stream.failed)}</td>
                    <td class="mono">${formatCount(stream.avgProcessingMs)} ms</td>
                    <td><span class="badge-tm ${statusClass}">${statusText}</span></td>
                </tr>
            `;
        }).join('');
    }

    function renderActivity(activity) {
        const container = document.getElementById('messagingActivityLog');
        if (!container) return;

        if (!activity.length) {
            container.innerHTML = '<div class="text-tertiary">No messaging activity has been recorded yet.</div>';
            return;
        }

        container.innerHTML = activity.map(function (item) {
            const failed = isFailure(item.status);
            const iconClass = failed ? 'icon-bg-danger' : 'icon-bg-success';
            const icon = failed ? 'bi-x' : 'bi-check2';
            return `
                <div class="log-item">
                    <span class="log-time">${formatTime(item.createdAt)}</span>
                    <span class="log-icon ${iconClass}"><i class="bi ${icon}"></i></span>
                    <div>
                        <strong>${escapeHtml(item.messageType)}</strong>
                        <span class="text-secondary"> ${escapeHtml(item.status || 'UNKNOWN')}</span>
                        on <span class="mono">${escapeHtml(item.destinationName)}</span>
                        <div class="cell-sub">${formatCount(item.processingTimeMs)} ms processing time</div>
                    </div>
                </div>
            `;
        }).join('');
    }

    function renderCharts(throughput, delivery) {
        if (!window.Chart) return;

        const queueCanvas = document.getElementById('chartQueueThroughput');
        if (queueCanvas) {
            if (queueChart) queueChart.destroy();
            queueChart = new Chart(queueCanvas, {
                type: 'bar',
                data: {
                    labels: throughput.map(item => item.label),
                    datasets: [{
                        label: 'Processed today',
                        data: throughput.map(item => numberValue(item.value)),
                        backgroundColor: window.TM.palette.accent,
                        borderRadius: 4,
                        barThickness: 16
                    }]
                },
                options: Object.assign(window.TM.baseLineOptions(), {
                    indexAxis: 'y',
                    scales: {
                        x: { grid: { color: window.TM.palette.grid }, border: { display: false }, ticks: { color: window.TM.palette.text } },
                        y: { grid: { display: false }, border: { display: false }, ticks: { color: window.TM.palette.text } }
                    }
                })
            });
        }

        const deliveryCanvas = document.getElementById('chartDeliveryRate');
        if (deliveryCanvas) {
            const failed = numberValue(delivery.failed);
            const successful = numberValue(delivery.successful);
            if (deliveryChart) deliveryChart.destroy();
            deliveryChart = new Chart(deliveryCanvas, {
                type: 'doughnut',
                data: {
                    labels: ['Successful', 'Failed'],
                    datasets: [{
                        data: [successful, failed],
                        backgroundColor: [window.TM.palette.success, window.TM.palette.danger],
                        borderWidth: 0
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: '72%',
                    plugins: {
                        legend: { display: true, position: 'bottom', labels: { color: '#B0B0B0', boxWidth: 10, padding: 14, font: { size: 11 } } }
                    }
                }
            });
        }
    }

    function setKpiTrend(id, text, state = 'flat', icon = 'bi-check2') {
        const el = document.getElementById(id);
        if (!el) return;
        el.className = `kpi-trend ${state}`;
        el.innerHTML = `<i class="bi ${icon}"></i> ${escapeHtml(text)}`;
    }

    function setKpiError(valueId, trendId) {
        setText(valueId, '--');
        setKpiTrend(trendId, 'Load failed', 'down', 'bi-exclamation-triangle');
    }

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    }

    function formatCount(value) {
        return numberValue(value).toLocaleString();
    }

    function formatPercent(value, total) {
        if (!numberValue(total)) return '0.00%';
        return ((numberValue(value) / numberValue(total)) * 100).toFixed(2) + '%';
    }

    function formatTime(value) {
        if (!value) return '--:--';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '--:--';
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    }

    function numberValue(value) {
        const parsed = Number(value || 0);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function isFailure(status) {
        return ['FAILED', 'ERROR', 'CIRCUIT_OPEN'].includes(String(status || '').toUpperCase());
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
        loadMessagingDashboard();
        const refresh = document.querySelector('.page-actions .btn-tm');
        if (refresh) refresh.addEventListener('click', loadMessagingDashboard);
        setInterval(loadMessagingDashboard, 30000);
    });
})();
