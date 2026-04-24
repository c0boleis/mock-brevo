package org.enoria.mockbrevo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MockBrevoApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockBrevoApplication.class, args);
    }
}
