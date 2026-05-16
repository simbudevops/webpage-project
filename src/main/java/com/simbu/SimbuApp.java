package com.simbu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication = marks this as the main Spring Boot class
@SpringBootApplication
public class SimbuApp {

    public static void main(String[] args) {
        // This starts the embedded Tomcat server on port 8080
        SpringApplication.run(SimbuApp.class, args);
        System.out.println("===========================================");
        System.out.println("  SIMBU App Started! Open http://localhost:8080");
        System.out.println("===========================================");
    }
}
