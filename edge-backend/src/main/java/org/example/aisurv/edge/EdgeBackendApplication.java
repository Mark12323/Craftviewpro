package org.example.aisurv.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EdgeBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(EdgeBackendApplication.class, args);
    }
}
