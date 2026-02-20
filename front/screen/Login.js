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
      body: JSON.stringify(body)
      // credentials: "include" // solo si usas cookies/sesiones; para JWT via header no hace falta
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

    if (data.token) {
      sessionStorage.setItem("token", data.token);
    }

    statusText.textContent = isLogin ? "Login exitoso" : "Registro exitoso";

    setTimeout(() => {
      window.location.href = "/front/screen/menu.html";
    }, 800);

  } catch (err) {
    statusText.textContent = err.message || "Error de autenticación";
  }
};
