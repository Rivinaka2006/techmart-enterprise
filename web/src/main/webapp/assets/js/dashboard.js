(function () {
  "use strict";

  var API_BASE = window.getTmApiBase ? window.getTmApiBase() : "/techmart-web/api";
  var charts = {};

  function parseJsonResponse(response) {
    return response.text().then(function (text) {
      if (!response.ok) {
        var message = "Request failed: " + response.status + " " + response.statusText;
        if (text) {
          try {
            var body = JSON.parse(text);
            if (body && body.error) message += " - " + body.error;
          } catch (e) {
            message += " - " + text.trim().slice(0, 120);
          }
        }
        throw new Error(message);
      }
      return text ? JSON.parse(text) : {};
    });
  }

  function numberValue(value) {
    var parsed = Number(value || 0);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function setText(id, value) {
    var el = document.getElementById(id);
    if (el) el.textContent = value;
  }

  function setStatus(id, text, isError) {
    var el = document.getElementById(id);
    if (!el) return;
    el.className = "kpi-trend " + (isError ? "down" : "flat");
    el.innerHTML = '<i class="bi ' + (isError ? "bi-exclamation-triangle" : "bi-check2") + '"></i> ' + text;
  }

  function setBadge(id, text, isError) {
    var el = document.getElementById(id);
    if (!el) return;
    el.className = "badge-tm " + (isError ? "badge-tm-danger" : "badge-tm-success");
    el.textContent = text;
  }

  function formatCount(value) {
    return numberValue(value).toLocaleString();
  }

  function formatMs(value) {
    return formatCount(value) + " ms";
  }

  function formatUptime(seconds) {
    seconds = Math.max(0, numberValue(seconds));
    var days = Math.floor(seconds / 86400);
    var hours = Math.floor((seconds % 86400) / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    if (days > 0) return days + "d " + hours + "h";
    if (hours > 0) return hours + "h " + minutes + "m";
    return minutes + "m";
  }

  function setKpi(cardId, statusId, value, statusText, formatter) {
    setText(cardId, formatter ? formatter(value) : formatCount(value));
    setStatus(statusId, statusText, false);
  }

  function setHealth(prefix, value, meta) {
    var pct = Math.min(100, Math.max(5, numberValue(value)));
    setText(prefix + "Value", formatCount(value));
    setText(prefix + "Meta", meta);

    var bar = document.getElementById(prefix + "Bar");
    if (bar) {
      bar.setAttribute("data-value", String(pct));
      bar.style.width = pct + "%";
    }
  }

  function loadSummary() {
    return fetch(API_BASE + "/dashboard/summary")
      .then(parseJsonResponse)
      .then(function (data) {
        setKpi("dashboardTotalProducts", "dashboardTotalProductsStatus", data.totalProducts, "Loaded from products");
        setKpi("dashboardTotalOrders", "dashboardTotalOrdersStatus", data.totalOrders, "Loaded from orders");
        setKpi("dashboardActiveSessions", "dashboardActiveSessionsStatus", data.activeSessions, "Loaded from sessions");
        setKpi("dashboardMessagesProcessed", "dashboardMessagesProcessedStatus", data.messagesProcessed, "Loaded from JMS logs");
        setKpi("dashboardAvgResponseTime", "dashboardAvgResponseTimeStatus", data.avgResponseTimeMs, "Loaded from metrics", formatMs);
        setKpi("dashboardSystemUptime", "dashboardSystemUptimeStatus", data.systemUptimeSeconds, "Loaded from health monitor", formatUptime);

        var health = data.health || {};
        setHealth("healthDatabase", health.dbQueries, "Database queries recorded");
        setHealth("healthJms", health.mdbProcessing, "MDB messages processed");
        setHealth("healthEjb", health.ejbInvocations, "EJB invocations recorded");
        setHealth("healthNotification", health.asyncTasks, "Async tasks recorded");

        updateSessionChart(data.sessionCounts || {});
        updateOrdersChart(data.orderStats || {});
      })
      .catch(function (error) {
        console.error("Error loading dashboard summary:", error);
        [
          "dashboardTotalProductsStatus",
          "dashboardTotalOrdersStatus",
          "dashboardActiveSessionsStatus",
          "dashboardMessagesProcessedStatus",
          "dashboardAvgResponseTimeStatus",
          "dashboardSystemUptimeStatus"
        ].forEach(function (id) { setStatus(id, "Load failed", true); });
      });
  }

  function loadCharts() {
    return fetch(API_BASE + "/dashboard/charts")
      .then(parseJsonResponse)
      .then(function (data) {
        updateLineChart("chartResponseTime", data.responseTime, "Response time", window.TM.palette.success);
        updateLineChart("chartJmsThroughput", data.jmsThroughput, "JMS processing time", window.TM.palette.warning);
        updateDbChart(data.systemCounters || {});

        setBadge("chartResponseTimeStatus", hasValues(data.responseTime) ? "Live data" : "No data", !hasValues(data.responseTime));
        setBadge("chartJmsThroughputStatus", hasValues(data.jmsThroughput) ? "Live data" : "No data", !hasValues(data.jmsThroughput));
        setBadge("chartDbPoolStatus", hasCounterValues(data.systemCounters) ? "Live data" : "No data", !hasCounterValues(data.systemCounters));
      })
      .catch(function (error) {
        console.error("Error loading dashboard charts:", error);
        ["chartResponseTimeStatus", "chartJmsThroughputStatus", "chartDbPoolStatus"].forEach(function (id) {
          setBadge(id, "Load failed", true);
        });
      });
  }

  function hasValues(series) {
    return !!(series && Array.isArray(series.values) && series.values.length);
  }

  function hasCounterValues(counters) {
    if (!counters) return false;
    return Object.keys(counters).some(function (key) {
      return Array.isArray(counters[key]) && counters[key].length;
    });
  }

  function updateLineChart(canvasId, series, label, color) {
    var canvas = document.getElementById(canvasId);
    if (!canvas || !window.Chart) return;

    var labels = series && series.labels ? series.labels : [];
    var values = series && series.values ? series.values.map(numberValue) : [];

    renderChart(canvasId, canvas, {
      type: "line",
      data: {
        labels: labels,
        datasets: [{
          label: label,
          data: values,
          borderColor: color,
          backgroundColor: color + "22",
          borderWidth: 2,
          pointRadius: 2,
          tension: 0.3,
          fill: true
        }]
      },
      options: window.TM.baseLineOptions()
    });
  }

  function updateOrdersChart(orderStats) {
    var canvas = document.getElementById("chartOrdersPerMin");
    if (!canvas || !window.Chart) return;

    var labels = ["Today", "Processing", "Completed Today", "Cancelled"];
    var values = [
      numberValue(orderStats.ordersToday),
      numberValue(orderStats.processing),
      numberValue(orderStats.completedToday),
      numberValue(orderStats.cancelled)
    ];

    renderChart("chartOrdersPerMin", canvas, {
      type: "bar",
      data: {
        labels: labels,
        datasets: [{
          label: "Orders",
          data: values,
          backgroundColor: [
            window.TM.palette.accent,
            window.TM.palette.warning,
            window.TM.palette.success,
            window.TM.palette.danger
          ],
          borderWidth: 0,
          borderRadius: 6
        }]
      },
      options: window.TM.baseLineOptions()
    });

    setBadge("chartOrdersPerMinStatus", values.some(function (value) { return value > 0; }) ? "Live data" : "No data", false);
  }

  function updateDbChart(counters) {
    var canvas = document.getElementById("chartDbPool");
    if (!canvas || !window.Chart) return;

    var dbValues = (counters.dbQueries || []).map(numberValue);
    var labels = dbValues.map(function (_, index) { return String(index + 1); });

    renderChart("chartDbPool", canvas, {
      type: "line",
      data: {
        labels: labels,
        datasets: [{
          label: "Database queries",
          data: dbValues,
          borderColor: window.TM.palette.accent,
          backgroundColor: window.TM.palette.accentSoft,
          borderWidth: 2,
          pointRadius: 2,
          tension: 0.3,
          fill: true
        }]
      },
      options: window.TM.baseLineOptions()
    });
  }

  function updateSessionChart(sessionCounts) {
    var canvas = document.getElementById("chartConcurrentSessions");
    if (!canvas || !window.Chart) return;

    var labels = ["Active", "Idle", "Expired"];
    var values = [
      numberValue(sessionCounts.active),
      numberValue(sessionCounts.idle),
      numberValue(sessionCounts.expired)
    ];

    renderChart("chartConcurrentSessions", canvas, {
      type: "doughnut",
      data: {
        labels: labels,
        datasets: [{
          data: values,
          backgroundColor: [
            window.TM.palette.success,
            window.TM.palette.warning,
            window.TM.palette.danger
          ],
          borderColor: "#121212",
          borderWidth: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: "bottom", labels: { color: window.TM.palette.text } }
        }
      }
    });

    setBadge("chartConcurrentSessionsStatus", values.some(function (value) { return value > 0; }) ? "Live data" : "No data", false);
  }

  function renderChart(key, canvas, config) {
    if (charts[key]) charts[key].destroy();
    charts[key] = new Chart(canvas, config);
  }

  function loadDashboard() {
    return Promise.all([loadSummary(), loadCharts()]);
  }

  document.addEventListener("DOMContentLoaded", function () {
    loadDashboard();

    var refreshButton = document.querySelector(".page-actions .btn-tm");
    if (refreshButton) {
      refreshButton.addEventListener("click", loadDashboard);
    }
  });
})();
