
package com.example.proyect.persistencia;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class Usuario {

    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hashPassword(String pass) {
        return encoder.encode(pass);
    }
    
//Luego la funcion validar password toma lo que ingresa el usuario y le hace el hash y lo compara 
//con el pass hasheado que esta en la base, hacer esto en el controlador
}
