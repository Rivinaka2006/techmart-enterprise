
(function () {
    'use strict';

    const API_BASE = window.getTmApiBase ? window.getTmApiBase() : '/techmart-web/api';
    const charts = {};

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

    async function loadMonitoringDashboard() {
        try {
            const response = await fetch(`${API_BASE}/metrics/monitoring-dashboard?limit=300`);
            const data = await parseJsonResponse(response);

            renderRuntime(data.runtime || {});
            renderApplication(data.application || {});
            renderNodes(data.nodes || []);
            renderCharts(data.charts || {});
        } catch (error) {
            console.error('Error loading monitoring dashboard:', error);
            markLoadFailed();
        }
    }

    function renderRuntime(runtime) {
        const cpu = numberValue(runtime.cpuPercent);
        const memory = numberValue(runtime.memoryPercent);
        const heap = numberValue(runtime.heapPercent);
        const threads = numberValue(runtime.threadCount);
        const peakThreads = Math.max(threads, numberValue(runtime.peakThreadCount));

        setText('monitoringRuntimeMeta', `${escapeHtml(runtime.nodeName || 'local-jvm')} · uptime ${formatUptime(runtime.uptimeSeconds)}`);
        setHealthMetric('monitoringCpu', `${formatPercent(cpu)}`, clampPercent(cpu), `${formatCount(runtime.availableProcessors)} available processors`);
        setHealthMetric('monitoringMemory', `${formatPercent(memory)}`, clampPercent(memory), `${formatMb(runtime.memoryUsedMb)} / ${formatMb(runtime.memoryMaxMb)} JVM memory`);
        setHealthMetric('monitoringThread', formatCount(threads), peakThreads ? clampPercent((threads / peakThreads) * 100) : 0, `${formatCount(runtime.daemonThreadCount)} daemon · peak ${formatCount(peakThreads)}`);
        setHealthMetric('monitoringHeap', `${formatPercent(heap)}`, clampPercent(heap), `${formatMb(runtime.heapUsedMb)} / ${formatMb(runtime.heapMaxMb)} heap`);
    }

    function renderApplication(application) {
        setText('monitoringEjbInvocations', formatCount(application.ejbInvocations));
        setText('monitoringMdbProcessing', formatCount(application.mdbProcessing));
        setText('monitoringAsyncTasks', formatCount(application.asyncTasks));
        setText('monitoringDbQueries', formatCount(application.dbQueries));

        setTrend('monitoringEjbTrend', 'From SystemHealthMonitorBean');
        setTrend('monitoringMdbTrend', 'Message-driven beans processed');
        setTrend('monitoringAsyncTrend', 'Async service executions');
        setTrend('monitoringDbTrend', 'Database queries recorded');
    }

    function renderNodes(nodes) {
        const body = document.getElementById('monitoringNodesBody');
        if (!body) return;

        if (!nodes.length) {
            body.innerHTML = '<tr><td colspan="6" class="text-center text-tertiary">No runtime data found.</td></tr>';
            return;
        }

        body.innerHTML = nodes.map(function (node) {
            const status = String(node.status || 'UNKNOWN').toUpperCase();
            const badge = badgeForStatus(status);
            return `
                <tr>
                    <td class="cell-main mono">${escapeHtml(node.name || 'local-jvm')}</td>
                    <td class="mono">${formatPercent(node.cpuPercent)}</td>
                    <td class="mono">${formatPercent(node.memoryPercent)}</td>
                    <td class="mono">${formatPercent(node.heapPercent)}</td>
                    <td class="mono">${formatCount(node.threads)}</td>
                    <td><span class="badge-tm ${badge.className}"><span class="dot ${badge.dotClass}"></span> ${badge.label}</span></td>
                </tr>
            `;
        }).join('');
    }

    function renderCharts(chartsData) {
        renderCpuMemoryChart(chartsData.cpuMemory || {});
        renderHeapThreadChart(chartsData.heapThread || {});
    }

    function renderCpuMemoryChart(series) {
        const canvas = document.getElementById('chartCpuMem');
        if (!canvas || !window.Chart) return;

        renderChart('chartCpuMem', canvas, {
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

    function renderHeapThreadChart(series) {
        const canvas = document.getElementById('chartHeapThread');
        if (!canvas || !window.Chart) return;

        renderChart('chartHeapThread', canvas, {
            type: 'line',
            data: {
                labels: arrayValue(series.labels),
                datasets: [
                    {
                        label: 'Heap %',
                        data: numberArray(series.heap),
                        borderColor: window.TM.palette.success,
                        backgroundColor: window.TM.palette.successSoft,
                        fill: true,
                        tension: 0.35,
                        pointRadius: 2,
                        borderWidth: 2
                    },
                    {
                        label: 'Threads',
                        data: numberArray(series.threads),
                        borderColor: window.TM.palette.accent,
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

    function setHealthMetric(prefix, value, barPercent, meta) {
        setText(`${prefix}Value`, value);
        setText(`${prefix}Meta`, meta);
        const bar = document.getElementById(`${prefix}Bar`);
        if (bar) {
            bar.setAttribute('data-value', String(barPercent));
            bar.style.width = `${barPercent}%`;
        }
    }

    function setTrend(id, text) {
        const el = document.getElementById(id);
        if (!el) return;
        el.className = 'kpi-trend flat';
        el.innerHTML = `<i class="bi bi-check2"></i> ${escapeHtml(text)}`;
    }

    function markLoadFailed() {
        [
            'monitoringCpuValue',
            'monitoringMemoryValue',
            'monitoringThreadValue',
            'monitoringHeapValue',
            'monitoringEjbInvocations',
            'monitoringMdbProcessing',
            'monitoringAsyncTasks',
            'monitoringDbQueries'
        ].forEach(function (id) {
            setText(id, '--');
        });

        [
            'monitoringEjbTrend',
            'monitoringMdbTrend',
            'monitoringAsyncTrend',
            'monitoringDbTrend'
        ].forEach(function (id) {
            const el = document.getElementById(id);
            if (!el) return;
            el.className = 'kpi-trend down';
            el.innerHTML = '<i class="bi bi-exclamation-triangle"></i> Load failed';
        });

        const body = document.getElementById('monitoringNodesBody');
        if (body) body.innerHTML = '<tr><td colspan="6" class="text-center text-tertiary">Unable to load runtime data.</td></tr>';
    }

    function badgeForStatus(status) {
        if (status === 'CRITICAL') return { className: 'badge-tm-danger', dotClass: 'dot-danger', label: 'Critical' };
        if (status === 'HIGH_LOAD') return { className: 'badge-tm-warning', dotClass: 'dot-warning', label: 'High Load' };
        if (status === 'HEALTHY') return { className: 'badge-tm-success', dotClass: 'dot-success', label: 'Healthy' };
        return { className: 'badge-tm-neutral', dotClass: 'dot-warning', label: 'Unknown' };
    }

    function formatPercent(value) {
        return `${numberValue(value).toFixed(1)}%`;
    }

    function formatCount(value) {
        return Math.round(numberValue(value)).toLocaleString();
    }

    function formatMb(value) {
        return `${formatCount(value)} MB`;
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

    function clampPercent(value) {
        return Math.max(0, Math.min(100, Math.round(numberValue(value))));
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

    document.addEventListener('DOMContentLoaded', function () {
        loadMonitoringDashboard();

        const refreshButton = document.querySelector('.page-actions .btn-tm');
        if (refreshButton) refreshButton.addEventListener('click', loadMonitoringDashboard);
        setInterval(loadMonitoringDashboard, 30000);
    });
})();
