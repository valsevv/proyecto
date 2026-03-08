-PostgreSQL 18.1

-Java 17

-Maven 3.3.4

# Juego local

Cambiar la URL del ws en /game.js a

> ws://localhost:8080/ws


 Facción Aéreo (game.units.aereo.*) : Daño medio - Largo Alcance - Movimiento Rapido
 
Vida: game.units.aereo.max-hp
Movimiento (lugares que se puede mover): game.units.aereo.movement-range
Combustibe: game.units.aereo.max-fuel
Visión: game.units.aereo.vision-range
Munición (bombas de drone aéreo): game.units.aereo.ammo
(Extra carrier) vida/movimiento de carrier aéreo:
game.units.aereo.carrier-hp
game.units.aereo.carrier-movement-range

Facción Naval (game.units.naval.*) - Alto Daño - Corto Alcance - Movimiento lento

Vida: game.units.naval.max-hp
Movimiento: game.units.naval.movement-range
Combustible: game.units.naval.max-fuel
Visión: game.units.naval.vision-range
Munición del arma: game.units.naval.weapon-ammo
Misiles navales: game.units.naval.missiles
(Extra carrier) vida/movimiento de carrier naval:
game.units.naval.carrier-hp
game.u



# Proyecto 2 - Manual de instalacion

Este proyecto usa:

- `back/`: API REST + WebSocket con Spring Boot.
- `front/`: cliente web del juego (HTML/JS + Phaser).
- `SQL/Create_tables.sql`: script base para crear la BD y tablas.

## 1. Requisitos

-PostgreSQL 18.1
-Java 17
-Maven 3.3.4
- Git (opcional, para clonar)

## 2. Clonar y ubicarse en el proyecto

```bash
git clone <URL_DEL_REPO>
cd proyecto2
```

## 3. Configurar base de datos

Por defecto, el backend espera esta conexion en `back/src/main/resources/application.properties`:

- `spring.datasource.url=jdbc:postgresql://localhost:5432/proyect`
- `spring.datasource.username=admin`
- `spring.datasource.password=admin`

Tenes dos opciones:

1. Crear la base/usuario con esos mismos valores (`proyect`, `admin/admin`).
2. Editar `back/src/main/resources/application.properties` con tus credenciales reales.

### Ejecutar script SQL

El script se encuentra en `SQL/Create_tables.sql`.

Importante:

- El archivo incluye `CREATE DATABASE proyect ... OWNER = postgres`.
- Si usas otro owner/usuario, ajusta ese bloque o crea la base manualmente y ejecuta solo las tablas.

Ejemplo rapido con `psql` (si ya existe la BD `proyect`):

```bash
psql -U admin -d proyect -f SQL/Create_tables.sql
```

## 4. Configurar frontend para correr en local

Editar `front/shared/constants.js` y dejar:

```js
export const API_BASE = 'http://localhost:8080/api';
export const WS_URL = 'ws://localhost:8080/ws';
```

Nota: actualmente ese archivo viene apuntando a una URL `ngrok`.

## 5. Levantar la aplicacion

Ejecutar desde la carpeta raiz del proyecto (`proyecto`), para que Spring pueda servir correctamente el contenido de `front/`.

### Windows (PowerShell)

```powershell
.\back\mvnw.cmd -f .\back\pom.xml spring-boot:run
```

### Linux/macOS

```bash
./back/mvnw -f ./back/pom.xml spring-boot:run
```

Cuando arranca bien, por defecto queda en `http://localhost:8080`.

## 6. Accesos utiles

- Inicio: `http://localhost:8080/` (redirige a login)
- Login: `http://localhost:8080/login`
- Menu: `http://localhost:8080/menu`
- Lobby browser: `http://localhost:8080/lobby-browser`
- Juego: `http://localhost:8080/game`

## 7. Verificacion rapida

1. Abrir `http://localhost:8080/login`.
2. Registrar usuario nuevo.
3. Iniciar sesion.
4. Crear o unirse a una lobby.
5. Confirmar que no haya errores de API/WS en consola del navegador.

## 8. Troubleshooting

- Error de conexion a BD:
  revisar `spring.datasource.*` en `back/src/main/resources/application.properties`.
- Front no carga recursos:
  confirmar que arrancaste desde la raiz del repo y no desde `back/`.
- Error WebSocket:
  validar `WS_URL` en `front/shared/constants.js`.
- Error CORS o llamadas a ngrok:
  revisar `API_BASE` y `WS_URL` para que ambos apunten a `localhost`.

## 9. Parametros de balance (opcional)

Se pueden ajustar en `back/src/main/resources/application.properties`:

- Aereo: `game.units.aereo.*`
- Naval: `game.units.naval.*`
- Misiles: `game.missile.*`
- Acciones por turno: `game.actions-per-turn`
