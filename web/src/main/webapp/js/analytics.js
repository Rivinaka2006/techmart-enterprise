
(function () {
    'use strict';

    const API_BASE = window.getTmApiBase ? window.getTmApiBase() : '/techmart-web/api';
    const charts = {};
    let currentReport = null;

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

    async function loadAnalytics() {
        const range = selectedRange();
        try {
            const response = await fetch(`${API_BASE}/metrics/analytics-dashboard?hours=${range}`);
            const data = await parseJsonResponse(response);
            currentReport = data;

            renderSummary(data.summary || {}, data.rangeHours || range);
            renderCharts(data.charts || {});
        } catch (error) {
            console.error('Error loading analytics dashboard:', error);
            markLoadFailed();
        }
    }

    function renderSummary(summary, rangeHours) {
        setText('analyticsAvgResponse', `${formatCount(summary.avgResponseMs)}ms`);
        setText('analyticsPeakResponse', `${formatCount(summary.peakResponseMs)}ms`);
        setText('analyticsThroughput', `${formatDecimal(summary.throughputPerMinute, 2)}/min`);
        setText('analyticsErrorRate', `${formatDecimal(summary.errorRate, 2)}%`);
        setText('analyticsRequestsPerSecond', formatDecimal(summary.requestsPerSecond, 2));

        const rangeLabel = rangeText(rangeHours);
        setTrend('analyticsAvgResponseTrend', `${formatCount(summary.operationSamples)} samples in ${rangeLabel}`, 'flat', 'bi-check2');
        setTrend('analyticsPeakResponseTrend', 'Highest recorded response metric', numberValue(summary.peakResponseMs) ? 'flat' : 'down', numberValue(summary.peakResponseMs) ? 'bi-graph-up' : 'bi-dash');
        setTrend('analyticsThroughputTrend', 'Calculated from performance samples', 'flat', 'bi-check2');
        setTrend(
            'analyticsErrorRateTrend',
            `${formatCount(summary.failedMessages)} failed of ${formatCount(summary.totalMessages)} JMS logs`,
            numberValue(summary.failedMessages) ? 'down' : 'flat',
            numberValue(summary.failedMessages) ? 'bi-exclamation-triangle' : 'bi-check2'
        );
        setTrend('analyticsRequestsPerSecondTrend', 'EJB invocations divided by uptime', 'flat', 'bi-check2');
    }

    function renderCharts(chartsData) {
        renderThroughputChart(chartsData.throughputTrend || {});
        renderResponseChart(chartsData.responseTrend || {});
        renderQueueChart(chartsData.queueTrend || {});
        renderResourceChart(chartsData.resourceTrend || {});
    }

    function renderThroughputChart(series) {
        const canvas = document.getElementById('chartThroughputTrend');
        if (!canvas || !window.Chart) return;
        renderChart('chartThroughputTrend', canvas, {
            type: 'line',
            data: {
                labels: arrayValue(series.labels),
                datasets: [{
                    label: 'samples/min',
                    data: numberArray(series.values),
                    borderColor: window.TM.palette.accent,
                    backgroundColor: window.TM.palette.accentSoft,
                    fill: true,
                    tension: 0.35,
                    pointRadius: 2,
                    borderWidth: 2
                }]
            },
            options: window.TM.baseLineOptions()
        });
    }

    function renderResponseChart(series) {
        const canvas = document.getElementById('chartResponseTrend');
        if (!canvas || !window.Chart) return;
        renderChart('chartResponseTrend', canvas, {
            type: 'line',
            data: {
                labels: arrayValue(series.labels),
                datasets: [
                    {
                        label: 'P50 (ms)',
                        data: numberArray(series.p50),
                        borderColor: window.TM.palette.success,
                        backgroundColor: 'transparent',
                        tension: 0.35,
                        pointRadius: 2,
                        borderWidth: 2
                    },
                    {
                        label: 'P95 (ms)',
                        data: numberArray(series.p95),
                        borderColor: window.TM.palette.warning,
                        backgroundColor: 'transparent',
                        tension: 0.35,
                        pointRadius: 2,
                        borderWidth: 2
                    }
                ]
            },
            options: lineOptions()
        });
    }

    function renderQueueChart(series) {
        const canvas = document.getElementById('chartQueueTrend');
        if (!canvas || !window.Chart) return;
        renderChart('chartQueueTrend', canvas, {
            type: 'bar',
            data: {
                labels: arrayValue(series.labels),
                datasets: [{
                    label: 'messages',
                    data: numberArray(series.values),
                    backgroundColor: window.TM.palette.warning,
                    borderRadius: 3,
                    barThickness: 16
                }]
            },
            options: window.TM.baseLineOptions()
        });
    }

    function renderResourceChart(series) {
        const canvas = document.getElementById('chartResourceTrend');
        if (!canvas || !window.Chart) return;
        renderChart('chartResourceTrend', canvas, {
            type: 'line',
            data: {
                labels: arrayValue(series.labels),
                datasets: [
                    {
                        label: 'CPU %',
                        data: numberArray(series.cpu),
                        borderColor: window.TM.palette.accent,
                        backgroundColor: 'transparent',
                        tension: 0.35,
                        pointRadius: 2,
                        borderWidth: 2
                    },
                    {
                        label: 'Memory %',
                        data: numberArray(series.memory),
                        borderColor: window.TM.palette.danger,
                        backgroundColor: 'transparent',
                        tension: 0.35,
                        pointRadius: 2,
                        borderWidth: 2
                    }
                ]
            },
            options: lineOptions()
        });
    }

    function lineOptions() {
        return Object.assign(window.TM.baseLineOptions(), {
            plugins: {
                legend: { display: true, position: 'bottom', labels: { color: '#B0B0B0', boxWidth: 10, font: { size: 11 } } },
                tooltip: window.TM.baseLineOptions().plugins.tooltip
            }
        });
    }

    function renderChart(key, canvas, config) {
        if (charts[key]) charts[key].destroy();
        charts[key] = new Chart(canvas, config);
    }

    function exportReport() {
        if (!currentReport) return;
        const blob = new Blob([JSON.stringify(currentReport, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `techmart-analytics-${selectedRange()}h.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    function markLoadFailed() {
        [
            'analyticsAvgResponse',
            'analyticsPeakResponse',
            'analyticsThroughput',
            'analyticsErrorRate',
            'analyticsRequestsPerSecond'
        ].forEach(function (id) {
            setText(id, '--');
        });

        [
            'analyticsAvgResponseTrend',
            'analyticsPeakResponseTrend',
            'analyticsThroughputTrend',
            'analyticsErrorRateTrend',
            'analyticsRequestsPerSecondTrend'
        ].forEach(function (id) {
            setTrend(id, 'Load failed', 'down', 'bi-exclamation-triangle');
        });
    }

    function selectedRange() {
        const select = document.getElementById('analyticsRange');
        return select ? numberValue(select.value) || 24 : 24;
    }

    function rangeText(hours) {
        const value = numberValue(hours);
        if (value >= 720) return '30 days';
        if (value >= 168) return '7 days';
        return '24 hours';
    }

    function setTrend(id, text, state, icon) {
        const el = document.getElementById(id);
        if (!el) return;
        el.className = `kpi-trend ${state || 'flat'}`;
        el.innerHTML = `<i class="bi ${icon || 'bi-check2'}"></i> ${escapeHtml(text)}`;
    }

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    }

    function formatCount(value) {
        return Math.round(numberValue(value)).toLocaleString();
    }

    function formatDecimal(value, digits) {
        return numberValue(value).toLocaleString(undefined, {
            minimumFractionDigits: digits,
            maximumFractionDigits: digits
        });
    }

    function numberValue(value) {
        const parsed = Number(value || 0);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    function numberArray(value) {
        return arrayValue(value).map(numberValue);
    }

    function arrayValue(value) {
        return Array.isArray(value) ? value : [];
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
        loadAnalytics();

        const range = document.getElementById('analyticsRange');
        if (range) range.addEventListener('change', loadAnalytics);

        const exportButton = document.getElementById('analyticsExport');
        if (exportButton) exportButton.addEventListener('click', exportReport);
    });
})();
