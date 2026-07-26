const loginForm = document.getElementById("login-form");
const messageBox = document.getElementById("message");
const dashboard = document.getElementById("dashboard");

function showMessage(text, type) {
  messageBox.textContent = text;
  messageBox.className = `message ${type}`;
}

async function loadVersionInfo() {
  try {
    const res = await fetch("/api/version");
    const data = await res.json();
    document.getElementById("app-version").textContent = data.version;
    document.getElementById("app-host").textContent = data.hostname;
    document.getElementById("dashboard-welcome").textContent = data.welcomeMessage;
  } catch (err) {
    console.error("Failed to load version info", err);
  }
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const username = document.getElementById("username").value;
  const password = document.getElementById("password").value;

  try {
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    const data = await res.json();

    if (res.ok && data.success) {
      showMessage("", "hidden");
      messageBox.className = "message hidden";
      loginForm.classList.add("hidden");
      dashboard.classList.remove("hidden");
      document.getElementById("dashboard-user").textContent = data.username;
      await loadVersionInfo();
    } else {
      showMessage(data.message || "Login failed", "error");
    }
  } catch (err) {
    showMessage("Unable to reach the server", "error");
  }
});

document.getElementById("logout-btn").addEventListener("click", () => {
  dashboard.classList.add("hidden");
  loginForm.classList.remove("hidden");
  loginForm.reset();
  messageBox.className = "message hidden";
});
