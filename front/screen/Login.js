const actionBtn = document.getElementById("actionBtn");
const toggleMode = document.getElementById("toggleMode");
const statusText = document.getElementById("status");
const emailInput = document.getElementById("email");

// ⚠️ Ajustá este valor al puerto/host real de tu backend
const BASE_URL = "http://localhost:8080";

let isLogin = true;

toggleMode.onclick = () => {
  isLogin = !isLogin;

  actionBtn.textContent = isLogin ? "LOGEARSE" : "REGISTRARSE";
  toggleMode.textContent = isLogin
    ? "¿No tenés cuenta? Registrarse"
    : "¿Ya tenés cuenta? Logearse";

  emailInput.style.display = isLogin ? "none" : "block";
  statusText.textContent = "";
};

actionBtn.onclick = async () => {
  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value.trim();
  const email = emailInput.value.trim();

  if (!username || !password || (!isLogin && !email)) {
    statusText.textContent = "Completar todos los campos";
    return;
  }



  const endpointPath = isLogin ? "/api/auth/login" : "/api/auth/register";
  const endpoint = `${BASE_URL}${endpointPath}`;

  const body = isLogin
    ? { username, password }
    : { username, email, password };

  try {
    statusText.textContent = "Conectando...";

    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(body),
      credentials: "include" // Enable cookie support
    });

    if (!response.ok) {
      const errorText = await response.text();

      let message = `Error ${response.status}`;
      try {
        const data = JSON.parse(errorText);
        // Si el backend manda { message: "...", ... }
        message = data?.message || message;
      } catch {
        // Si no es JSON, usamos el texto tal cual (si existe)
        message = errorText || message;
      }

      throw new Error(message);
    }

    const data = await response.json();

    // Cookie is set automatically by browser from Set-Cookie header
    // No need to manually store token in sessionStorage

    statusText.textContent = isLogin ? "Login exitoso" : "Registro exitoso";

    setTimeout(() => {
      window.location.href = "/menu";
    }, 1200);

  } catch (err) {
    statusText.textContent = err.message || "Error de autenticación";
  }
};

  window.addEventListener("DOMContentLoaded", async () => {

    const statusText = document.getElementById("status");

    try {
      const response = await fetch("http://localhost:8080/api/auth/me", {
        method: "GET",
        credentials: "include"
      });

      if (response.ok) {
        statusText.textContent = "Sesión detectada...";
        statusText.style.color = "#00ff88";
        statusText.style.opacity = "1";

        setTimeout(() => {
          document.body.style.transition = "opacity 0.6s";
          document.body.style.opacity = "0";
        }, 800);

        setTimeout(() => {
          window.location.href = "/menu";
        }, 1200);

        return;
      }

    } catch (e) {
      // No autenticado → no hacer nada
    }

  });