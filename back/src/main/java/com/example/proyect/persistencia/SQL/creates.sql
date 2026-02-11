CREATE TABLE [IF NOT EXISTS] usuario (
    id_usuario SERIAL PRIMARY KEY,
    nombre_usuario VARCHAR(50) UNIQUE NOT NULL,
    contrasenia_hash TEXT NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    fecha_creacion TIMESTAMP  NOT NULL DEFAULT now(),
    ultima_conexion TIMESTAMP NOT NULL DEFAULT now()
);