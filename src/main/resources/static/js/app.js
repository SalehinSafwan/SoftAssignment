// RESOURCES EACH ROLE IS ALLOWED TO MANAGE (mirrors SecurityConfig)

const RESOURCES = {
    admins:   { title: "Admins",   singular: "Admin" },
    teachers: { title: "Teachers", singular: "Teacher" },
    students: { title: "Students", singular: "Student" }
};

const ROLE_ACCESS = {
    ADMIN:   ["admins", "teachers", "students"],
    TEACHER: ["teachers"],
    STUDENT: ["students"]
};

let csrfToken = null;
let currentUser = null;
let currentResource = null;
let editingId = null;

const $ = (id) => document.getElementById(id);


// API HELPERS

async function fetchCsrfToken() {
    const response = await fetch("/api/auth/csrf");
    const data = await response.json();
    csrfToken = data.token;
}

async function api(method, url, body) {

    const options = {
        method,
        headers: { "Content-Type": "application/json" }
    };

    // Every state-changing request must carry the CSRF token
    if (method !== "GET") {
        options.headers["X-CSRF-TOKEN"] = csrfToken;
    }

    if (body !== undefined) {
        options.body = JSON.stringify(body);
    }

    const response = await fetch(url, options);

    // 204 No Content has no JSON body
    const data = response.status === 204 ? null : await response.json().catch(() => null);

    if (response.status === 401 && url !== "/api/auth/login") {
        showLogin();
    }

    return { ok: response.ok, status: response.status, data };
}

function errorText(result) {

    if (!result.data) {
        return "Request failed (HTTP " + result.status + ")";
    }

    // Validation errors come as { errors: { field: message } }
    if (result.data.errors) {
        return Object.values(result.data.errors).join(", ");
    }

    return result.data.message || "Request failed (HTTP " + result.status + ")";
}


// VIEW SWITCHING

function showLogin() {
    currentUser = null;
    $("dashboard-view").classList.add("hidden");
    $("modal").classList.add("hidden");
    $("login-view").classList.remove("hidden");
}

function showDashboard(user) {

    currentUser = user;

    $("current-user").textContent = user.name + " (" + user.email + ")";
    $("current-role").textContent = user.role;

    $("login-view").classList.add("hidden");
    $("dashboard-view").classList.remove("hidden");

    renderTabs();
    selectResource(ROLE_ACCESS[user.role][0]);
}


// LOGIN / LOGOUT

$("login-form").addEventListener("submit", async (event) => {

    event.preventDefault();

    $("login-error").classList.add("hidden");

    const result = await api("POST", "/api/auth/login", {
        email: $("login-email").value.trim(),
        password: $("login-password").value
    });

    if (!result.ok) {
        $("login-error").textContent = errorText(result);
        $("login-error").classList.remove("hidden");
        return;
    }

    $("login-form").reset();

    const me = await api("GET", "/api/auth/me");
    showDashboard(me.data);
});

$("logout-btn").addEventListener("click", async () => {

    await api("POST", "/api/auth/logout");

    // The old session (and its CSRF token) is gone, so get a new token
    await fetchCsrfToken();

    showLogin();
});


// TABS

function renderTabs() {

    const tabs = $("tabs");
    tabs.innerHTML = "";

    ROLE_ACCESS[currentUser.role].forEach((resource) => {

        const button = document.createElement("button");
        button.className = "tab";
        button.textContent = RESOURCES[resource].title;
        button.dataset.resource = resource;
        button.addEventListener("click", () => selectResource(resource));

        tabs.appendChild(button);
    });
}

function selectResource(resource) {

    currentResource = resource;

    document.querySelectorAll(".tab").forEach((tab) => {
        tab.classList.toggle("active", tab.dataset.resource === resource);
    });

    $("resource-title").textContent = RESOURCES[resource].title;
    $("add-btn").textContent = "+ Add " + RESOURCES[resource].singular;

    hideMessage();
    loadTable();
}


// TABLE (READ)

async function loadTable() {

    const result = await api("GET", "/api/" + currentResource);
    const body = $("table-body");
    body.innerHTML = "";

    if (!result.ok) {
        showMessage(errorText(result), false);
        return;
    }

    if (result.data.length === 0) {
        const row = body.insertRow();
        const cell = row.insertCell();
        cell.colSpan = 7;
        cell.className = "empty";
        cell.textContent = "No records yet.";
        return;
    }

    result.data.forEach((user) => {

        const row = body.insertRow();

        // textContent (not innerHTML) so user data can never inject HTML
        [
            user.id,
            user.name,
            user.email,
            user.department || "-",
            user.phone || "-",
            user.createdAt ? new Date(user.createdAt).toLocaleString() : "-"
        ].forEach((value) => {
            row.insertCell().textContent = value;
        });

        const actions = row.insertCell();

        const editButton = document.createElement("button");
        editButton.className = "btn btn-light btn-small";
        editButton.textContent = "Edit";
        editButton.addEventListener("click", () => openForm(user));

        const deleteButton = document.createElement("button");
        deleteButton.className = "btn btn-danger btn-small";
        deleteButton.textContent = "Delete";
        deleteButton.addEventListener("click", () => deleteUser(user));

        // Prevent users from deleting the account they are logged in with
        if (user.email === currentUser.email) {
            deleteButton.disabled = true;
            deleteButton.title = "You cannot delete your own account";
        }

        actions.append(editButton, deleteButton);
    });
}


// CREATE / UPDATE FORM

function openForm(user) {

    editingId = user ? user.id : null;

    const singular = RESOURCES[currentResource].singular;

    $("form-title").textContent = user ? "Edit " + singular : "Add " + singular;

    $("form-name").value = user ? user.name : "";
    $("form-email").value = user ? user.email : "";
    $("form-password").value = "";
    $("form-department").value = user && user.department ? user.department : "";
    $("form-phone").value = user && user.phone ? user.phone : "";

    // Password is required on create, optional on update
    $("form-password").required = !user;
    $("password-hint").textContent = user
        ? "Leave blank to keep the current password."
        : "At least 8 characters.";

    $("form-error").classList.add("hidden");
    $("modal").classList.remove("hidden");
    $("form-name").focus();
}

$("add-btn").addEventListener("click", () => openForm(null));

$("cancel-btn").addEventListener("click", () => $("modal").classList.add("hidden"));

$("user-form").addEventListener("submit", async (event) => {

    event.preventDefault();

    const payload = {
        name: $("form-name").value.trim(),
        email: $("form-email").value.trim(),
        password: $("form-password").value,
        department: $("form-department").value.trim(),
        phone: $("form-phone").value.trim()
    };

    const result = editingId
        ? await api("PUT", "/api/" + currentResource + "/" + editingId, payload)
        : await api("POST", "/api/" + currentResource, payload);

    if (!result.ok) {
        $("form-error").textContent = errorText(result);
        $("form-error").classList.remove("hidden");
        return;
    }

    $("modal").classList.add("hidden");

    showMessage(
        RESOURCES[currentResource].singular + (editingId ? " updated." : " created."),
        true
    );

    loadTable();
});


// DELETE

async function deleteUser(user) {

    if (!confirm("Delete " + user.name + " (" + user.email + ")?")) {
        return;
    }

    const result = await api("DELETE", "/api/" + currentResource + "/" + user.id);

    if (!result.ok) {
        showMessage(errorText(result), false);
        return;
    }

    showMessage(RESOURCES[currentResource].singular + " deleted.", true);
    loadTable();
}


// STATUS MESSAGES

function showMessage(text, success) {
    const message = $("message");
    message.textContent = text;
    message.className = success ? "success" : "failure";
}

function hideMessage() {
    $("message").className = "hidden";
}


// STARTUP: restore the session if the user is already logged in

(async function init() {

    await fetchCsrfToken();

    const me = await api("GET", "/api/auth/me");

    if (me.ok) {
        showDashboard(me.data);
    } else {
        showLogin();
    }
})();
