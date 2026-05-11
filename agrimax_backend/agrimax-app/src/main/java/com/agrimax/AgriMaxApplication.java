package com.agrimax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AgriMaxApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgriMaxApplication.class, args);
    }
}
