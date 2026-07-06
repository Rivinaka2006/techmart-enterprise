
(function () {
    "use strict";
    var API_BASE = (window.getTmApiBase ? window.getTmApiBase() : "/techmart-web/api") + "/auth";
    var INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    var AUTH_USER_STORAGE_KEY = "tm.currentUser";

    function checkSession() {
        return fetch(API_BASE + "/me").then(function (res) { return res.ok ? res.json() : null; });
    }

    function showAuthModal() {
        if (document.getElementById("authModal")) return;

        var modal = document.createElement("div");
        modal.id = "authModal";
        modal.className = "auth-modal-overlay";
        modal.innerHTML =
            '<div class="auth-modal">' +
            '<div class="auth-tabs">' +
            '<button class="auth-tab active" data-tab="login">Log In</button>' +
            '<button class="auth-tab" data-tab="register">Sign Up</button>' +
            '</div>' +
            '<form id="authForm" class="auth-form">' +
            '<input type="text" id="authIdentifier" class="form-control-tm" placeholder="Username or email" required>' +
            '<input type="email" id="authEmail" class="form-control-tm" placeholder="Email" style="display:none;">' +
            '<input type="password" id="authPassword" class="form-control-tm" placeholder="Password" required>' +
            '<div class="auth-error" id="authError"></div>' +
            '<button type="submit" class="btn-tm btn-tm-primary w-100" id="authSubmitBtn">Log In</button>' +
            '</form>' +
            '</div>';
        document.body.appendChild(modal);

        var mode = "login";
        modal.querySelectorAll(".auth-tab").forEach(function (tab) {
            tab.addEventListener("click", function () {
                mode = tab.getAttribute("data-tab");
                modal.querySelectorAll(".auth-tab").forEach(function (t) { t.classList.remove("active"); });
                tab.classList.add("active");
                document.getElementById("authEmail").style.display = mode === "register" ? "" : "none";
                document.getElementById("authSubmitBtn").textContent = mode === "register" ? "Sign Up" : "Log In";
                document.getElementById("authIdentifier").placeholder = mode === "register" ? "Username" : "Username or email";
                document.getElementById("authError").textContent = "";
            });
        });

        document.getElementById("authForm").addEventListener("submit", function (e) {
            e.preventDefault();
            var identifier = document.getElementById("authIdentifier").value.trim();
            var password = document.getElementById("authPassword").value;
            var errorEl = document.getElementById("authError");
            errorEl.textContent = "";

            var url = mode === "register" ? API_BASE + "/register" : API_BASE + "/login";
            var payload = mode === "register"
                ? { username: identifier, email: document.getElementById("authEmail").value.trim(), password: password }
                : { usernameOrEmail: identifier, password: password };

            fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) })
                .then(function (res) {
                    return res.json()
                        .catch(function () { return {}; })
                        .then(function (b) { return { ok: res.ok, status: res.status, body: b }; });
                })
                .then(function (result) {
                    if (!result.ok) {
                        errorEl.textContent = result.body.error ||
                            (mode === "login" && result.status === 401 ? INVALID_CREDENTIALS_MESSAGE : "Something went wrong");
                        return;
                    }
                    onAuthenticated(result.body);
                    saveCachedUser(result.body);
                    modal.remove();
                })
                .catch(function () { errorEl.textContent = "Network error - please try again"; });
        });
    }

    function onAuthenticated(user) {
        if (!user) return;
        window.TM_CURRENT_USER = user;
        var nameEls = document.querySelectorAll(".admin-name");
        var roleEls = document.querySelectorAll(".admin-role");
        var avatarEls = document.querySelectorAll(".admin-chip .avatar");
        nameEls.forEach(function (nameEl) {
            nameEl.textContent = user.username || user.email || "User";
        });
        roleEls.forEach(function (roleEl) {
            roleEl.textContent = user.roleName || "User";
        });
        avatarEls.forEach(function (avatarEl) {
            avatarEl.textContent = getInitials(user.username || user.email);
        });
        document.dispatchEvent(new CustomEvent("tm:authenticated", { detail: user }));
    }

    function saveCachedUser(user) {
        try {
            localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user));
        } catch (e) {}
    }

    function getCachedUser() {
        try {
            var raw = localStorage.getItem(AUTH_USER_STORAGE_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch (e) {
            return null;
        }
    }

    function clearCachedUser() {
        try {
            localStorage.removeItem(AUTH_USER_STORAGE_KEY);
        } catch (e) {}
    }

    function getInitials(value) {
        var text = String(value || "").trim();
        if (!text) return "U";

        var namePart = text.indexOf("@") === -1 ? text : text.split("@")[0];
        var parts = namePart
            .split(/[\s._-]+/)
            .filter(function (part) { return part.length > 0; });

        if (parts.length >= 2) {
            return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }

        return namePart.slice(0, 2).toUpperCase();
    }

    function initLogoutButton() {
        var btn = document.querySelector(".btn-logout");
        if (!btn) return;
        btn.addEventListener("click", function () {
            clearCachedUser();
            fetch(API_BASE + "/logout", { method: "POST" }).then(function () { window.location.reload(); });
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        initLogoutButton();
        onAuthenticated(getCachedUser());
        checkSession().then(function (user) {
            if (user) {
                saveCachedUser(user);
                onAuthenticated(user);
            } else {
                clearCachedUser();
                showAuthModal();
            }
        });
    });
})();
