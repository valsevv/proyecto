document.addEventListener("DOMContentLoaded", loadRanking);

async function loadRanking() {
    const loading = document.getElementById("loading");
    const errorDiv = document.getElementById("error");
    const table = document.getElementById("rankingTable");
    const tbody = table.querySelector("tbody");

    try {
        const response = await fetch("/api/ranking/top", {
            method: "GET",
            credentials: "include"
        });

        if (!response.ok) {
            throw new Error("Error al obtener ranking");
        }

        const data = await response.json();

        loading.style.display = "none";
        table.style.display = "table";

        if (data.length === 0) {
            errorDiv.textContent = "No hay datos disponibles.";
            table.style.display = "none";
            return;
        }

        data.forEach((player, index) => {
            const row = document.createElement("tr");

            row.innerHTML = `
                <td>${index + 1}</td>
                <td>${player.username}</td>>
                <td>${player.points}</td>
            `;

            tbody.appendChild(row);
        });

    } catch (err) {
        loading.style.display = "none";
        errorDiv.textContent = err.message;
    }
}