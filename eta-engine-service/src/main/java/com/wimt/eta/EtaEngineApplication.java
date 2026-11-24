package com.wimt.eta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EtaEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(EtaEngineApplication.class, args);
    }
}
