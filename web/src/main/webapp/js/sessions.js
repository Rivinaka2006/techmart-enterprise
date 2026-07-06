
(function () {
    'use strict';

    const API_BASE = (window.getTmApiBase ? window.getTmApiBase() : '/techmart-web/api');
    const PAGE_SIZE = 25;
    let currentOffset = 0;
    let currentStatus = null;
    let lastLoadedCount = 0;
    let totalSessions = 0;

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

    async function loadSessions() {
        try {
            const params = new URLSearchParams({
                offset: String(currentOffset),
                limit: String(PAGE_SIZE)
            });
            if (currentStatus) params.set('status', currentStatus);

            const response = await fetch(`${API_BASE}/sessions/dashboard?${params.toString()}`);
            const data = await parseJsonResponse(response);

            totalSessions = Number(data.totalSessions || 0);
            const sessions = Array.isArray(data.sessions) ? data.sessions : [];
            lastLoadedCount = sessions.length;

            renderCounts(data.counts || {}, data.avgDurationSeconds || 0);
            renderSessions(sessions);
            renderPagination();
        } catch (error) {
            console.error('Error loading sessions:', error);
            setKpiError('activeSessionCount', 'activeSessionTrend');
            setKpiError('idleSessionCount', 'idleSessionTrend');
            setKpiError('expiredSessionCount', 'expiredSessionTrend');
            setKpiError('avgSessionDuration', 'avgSessionDurationTrend');
            renderSessions([]);
            setText('sessionsTableSummary', 'Failed to load sessions.');
        }
    }

    async function expireIdleSessions() {
        try {
            const response = await fetch(`${API_BASE}/sessions/expire-idle`, { method: 'POST' });
            await parseJsonResponse(response);
            currentOffset = 0;
            await loadSessions();
        } catch (error) {
            console.error('Error expiring idle sessions:', error);
            alert('Failed to expire idle sessions');
        }
    }

    function renderCounts(counts, avgDurationSeconds) {
        const active = Number(counts.active || 0);
        const idle = Number(counts.idle || 0);
        const expired = Number(counts.expired || 0);

        setText('activeSessionCount', active.toLocaleString());
        setText('idleSessionCount', idle.toLocaleString());
        setText('expiredSessionCount', expired.toLocaleString());
        setText('avgSessionDuration', formatDuration(avgDurationSeconds));

        setKpiTrend('activeSessionTrend', active ? 'Currently signed in' : 'No active sessions', active ? 'up' : 'flat', active ? 'bi-broadcast' : 'bi-dash');
        setKpiTrend('idleSessionTrend', idle ? 'Eligible for expiry review' : 'No idle sessions', idle ? 'down' : 'flat', idle ? 'bi-hourglass-split' : 'bi-dash');
        setKpiTrend('expiredSessionTrend', expired ? 'Closed sessions recorded' : 'No expired sessions', expired ? 'flat' : 'flat', 'bi-check2');
        setKpiTrend('avgSessionDurationTrend', totalSessions ? 'Calculated from session history' : 'No session history');
    }

    function renderSessions(sessions) {
        const tbody = document.getElementById('sessionsTableBody');
        if (!tbody) return;

        if (!sessions.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-tertiary">No sessions returned by the backend.</td></tr>';
            return;
        }

        tbody.innerHTML = sessions.map(function (session) {
            const status = session.status || 'UNKNOWN';
            return `
                <tr>
                    <td class="cell-id">SES-${escapeHtml(session.id)}</td>
                    <td>
                        <div class="cell-main">${escapeHtml(session.username || 'Unknown')}</div>
                        <div class="cell-sub">${escapeHtml(session.email || '')}</div>
                    </td>
                    <td class="mono cell-sub">${formatDate(session.loginTime)}</td>
                    <td class="mono cell-sub">${formatDate(session.lastSeenAt)}</td>
                    <td><span class="badge-tm ${getStatusBadgeClass(status)}">${escapeHtml(formatStatus(status))}</span></td>
                    <td class="cell-sub">${escapeHtml(formatDuration(session.durationSeconds))}</td>
                </tr>
            `;
        }).join('');
    }

    function renderPagination() {
        const pageNumber = Math.floor(currentOffset / PAGE_SIZE) + 1;
        const start = lastLoadedCount ? currentOffset + 1 : 0;
        const end = currentOffset + lastLoadedCount;
        const summary = currentStatus
            ? `Showing ${start}-${end} ${currentStatus.toLowerCase()} session${lastLoadedCount === 1 ? '' : 's'}`
            : `Showing ${start}-${end} of ${Number(totalSessions || 0).toLocaleString()} sessions`;

        setText('sessionsTableSummary', summary);
        setText('sessionsPageBtn', String(pageNumber));

        const prev = document.getElementById('sessionsPrevBtn');
        const next = document.getElementById('sessionsNextBtn');
        if (prev) prev.disabled = currentOffset === 0;
        if (next) next.disabled = lastLoadedCount < PAGE_SIZE || (!currentStatus && currentOffset + lastLoadedCount >= totalSessions);
    }

    function bindControls() {
        const filter = document.getElementById('sessionStateFilter');
        if (filter) {
            filter.addEventListener('change', function () {
                currentStatus = filter.value === 'all' ? null : filter.value;
                currentOffset = 0;
                loadSessions();
            });
        }

        const refresh = document.getElementById('sessionsRefreshBtn');
        if (refresh) refresh.addEventListener('click', loadSessions);

        const expire = document.getElementById('expireIdleSessionsBtn');
        if (expire) expire.addEventListener('click', expireIdleSessions);

        const prev = document.getElementById('sessionsPrevBtn');
        if (prev) {
            prev.addEventListener('click', function () {
                currentOffset = Math.max(0, currentOffset - PAGE_SIZE);
                loadSessions();
            });
        }

        const next = document.getElementById('sessionsNextBtn');
        if (next) {
            next.addEventListener('click', function () {
                currentOffset += PAGE_SIZE;
                loadSessions();
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

    function getStatusBadgeClass(status) {
        switch (status) {
            case 'ACTIVE': return 'badge-tm-success';
            case 'IDLE': return 'badge-tm-warning';
            case 'EXPIRED': return 'badge-tm-neutral';
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

    function formatDuration(seconds) {
        seconds = Math.max(0, Number(seconds || 0));
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const remainingSeconds = Math.floor(seconds % 60);
        if (hours > 0) return `${hours}h ${minutes}m`;
        if (minutes > 0) return `${minutes}m ${remainingSeconds}s`;
        return `${remainingSeconds}s`;
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
        bindControls();
        loadSessions();
        setInterval(loadSessions, 30000);
    });
})();
