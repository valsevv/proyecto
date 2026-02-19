package com.example.proyect.auth.Exceptions;


public class EmailAlreadyExistsException extends RuntimeException {
     public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
