package com.example.proyect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProyectApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProyectApplication.class, args);
	}

}
 