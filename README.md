-PostgreSQL 18.1

-Java 17

-Maven 3.3.4

# Estándar de codificación (opcional)

Se agregó configuración mínima para mantener un estilo consistente sin afectar la ejecución:

- `.editorconfig`: indentación y finales de línea.
- Checkstyle (Java, backend): ejecutable bajo demanda.
- Prettier (JS/HTML/CSS, front): ejecutable bajo demanda.

## Backend (Java) — Checkstyle

Desde la carpeta `back/`:

- `./mvnw checkstyle:check` (o `mvn checkstyle:check` si tenés Maven instalado)

## Frontend — Prettier

En la raíz del proyecto:

- `npm install`
- `npm run format` (aplica formato)
- `npm run format:check` (solo verifica)

# Juego local

Cambiar la URL del ws en /game.js a

> ws://localhost:8080/ws