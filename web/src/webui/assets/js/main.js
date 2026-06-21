/* ==========================================================================
   TechMart Online — Operations Console
   Shared JS: sidebar, topbar clock, chart defaults, table/utility helpers
   ========================================================================== */

(function () {
  "use strict";

  var TM = window.TM || {};

  /* ---------------- Sidebar collapse / mobile drawer ---------------- */

  function initSidebar() {
    var sidebar = document.getElementById("sidebar");
    var toggleBtn = document.getElementById("sidebarToggle");
    if (!sidebar || !toggleBtn) return;

    var collapsed = localStorage.getItem("tm-sidebar-collapsed") === "1";
    if (collapsed && window.innerWidth > 991) sidebar.classList.add("collapsed");

    toggleBtn.addEventListener("click", function () {
      if (window.innerWidth <= 991) {
        sidebar.classList.toggle("mobile-open");
      } else {
        sidebar.classList.toggle("collapsed");
        localStorage.setItem("tm-sidebar-collapsed", sidebar.classList.contains("collapsed") ? "1" : "0");
      }
    });

    document.addEventListener("click", function (e) {
      if (window.innerWidth > 991) return;
      if (!sidebar.classList.contains("mobile-open")) return;
      if (sidebar.contains(e.target) || e.target === toggleBtn || toggleBtn.contains(e.target)) return;
      sidebar.classList.remove("mobile-open");
    });
  }

  /* ---------------- Live clock (topbar / footer use) ---------------- */

  function initClock() {
    var els = document.querySelectorAll("[data-tm-clock]");
    if (!els.length) return;
    function tick() {
      var now = new Date();
      var str = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
      els.forEach(function (el) { el.textContent = str; });
    }
    tick();
    setInterval(tick, 1000);
  }

  /* ---------------- Animate progress bars on load ---------------- */

  function initProgressBars() {
    var bars = document.querySelectorAll(".progress-tm-bar[data-value]");
    bars.forEach(function (bar) {
      var target = bar.getAttribute("data-value");
      bar.style.width = "0%";
      requestAnimationFrame(function () {
        setTimeout(function () { bar.style.width = target + "%"; }, 60);
      });
    });
  }

  /* ---------------- Table search filter ---------------- */
  /* Usage: <input data-tm-search-target="#myTableBody"> */

  function initTableSearch() {
    var inputs = document.querySelectorAll("[data-tm-search-target]");
    inputs.forEach(function (input) {
      var target = document.querySelector(input.getAttribute("data-tm-search-target"));
      if (!target) return;
      input.addEventListener("input", function () {
        var q = input.value.trim().toLowerCase();
        Array.prototype.forEach.call(target.rows, function (row) {
          var text = row.textContent.toLowerCase();
          row.style.display = text.indexOf(q) > -1 ? "" : "none";
        });
      });
    });
  }

  /* ---------------- Simple select-based filter ---------------- */
  /* Usage: <select data-tm-filter-target="#myTableBody" data-tm-filter-col="5"> */

  function initSelectFilter() {
    var selects = document.querySelectorAll("[data-tm-filter-target]");
    selects.forEach(function (select) {
      var target = document.querySelector(select.getAttribute("data-tm-filter-target"));
      var col = parseInt(select.getAttribute("data-tm-filter-col"), 10);
      if (!target) return;
      select.addEventListener("change", function () {
        var val = select.value.toLowerCase();
        Array.prototype.forEach.call(target.rows, function (row) {
          if (val === "all" || val === "") { row.style.display = ""; return; }
          var cell = row.cells[col];
          var text = cell ? cell.textContent.toLowerCase() : "";
          row.style.display = text.indexOf(val) > -1 ? "" : "none";
        });
      });
    });
  }

  /* ---------------- Chart.js shared defaults ---------------- */

  function chartDefaults() {
    if (window.Chart) {
      Chart.defaults.color = "#B0B0B0";
      Chart.defaults.font.family = "Inter, sans-serif";
      Chart.defaults.font.size = 11;
      Chart.defaults.borderColor = "rgba(255,255,255,0.06)";
    }
  }

  var palette = {
    accent: "#3B82F6",
    accentSoft: "rgba(59,130,246,0.12)",
    success: "#10B981",
    successSoft: "rgba(16,185,129,0.12)",
    warning: "#F59E0B",
    warningSoft: "rgba(245,158,11,0.12)",
    danger: "#EF4444",
    dangerSoft: "rgba(239,68,68,0.12)",
    grid: "rgba(255,255,255,0.06)",
    text: "#B0B0B0"
  };

  function baseLineOptions(overrides) {
    var opts = {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: "#1a1a1a",
          borderColor: "rgba(255,255,255,0.1)",
          borderWidth: 1,
          titleColor: "#fff",
          bodyColor: "#B0B0B0",
          padding: 10,
          displayColors: true
        }
      },
      scales: {
        x: { grid: { color: palette.grid, drawTicks: false }, border: { display: false }, ticks: { color: palette.text } },
        y: { grid: { color: palette.grid, drawTicks: false }, border: { display: false }, ticks: { color: palette.text } }
      }
    };
    return Object.assign(opts, overrides || {});
  }

  /* ---------------- Tiny "live" number ticker for demo metrics ---------------- */
  /* Usage: <span data-tm-tick data-tm-base="1280" data-tm-variance="40">1,280</span> */

  function initLiveTickers() {
    var els = document.querySelectorAll("[data-tm-tick]");
    if (!els.length) return;
    setInterval(function () {
      els.forEach(function (el) {
        var base = parseFloat(el.getAttribute("data-tm-base")) || 0;
        var variance = parseFloat(el.getAttribute("data-tm-variance")) || 0;
        var val = base + (Math.random() * variance * 2 - variance);
        var decimals = el.getAttribute("data-tm-decimals");
        val = decimals ? val.toFixed(parseInt(decimals, 10)) : Math.round(val);
        el.textContent = Number(val).toLocaleString();
      });
    }, 2500);
  }

  TM.palette = palette;
  TM.chartDefaults = chartDefaults;
  TM.baseLineOptions = baseLineOptions;

  window.TM = TM;

  document.addEventListener("DOMContentLoaded", function () {
    initSidebar();
    initClock();
    initProgressBars();
    initTableSearch();
    initSelectFilter();
    initLiveTickers();
    chartDefaults();
  });
})();
