CREATE TABLE user (
    userID SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    hash_password TEXT NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    creation_date TIMESTAMP  NOT NULL DEFAULT now(),
    last_connection TIMESTAMP NOT NULL DEFAULT now()
); 
 