package com.toonmint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
// JobRecoveryService rulează periodic ca să prindă job-urile rămase fără worker.
@EnableScheduling
public class ToonMintApplication {

    /**
     * Pornește aplicația Spring Boot.
     */
    public static void main(String[] args) {
        SpringApplication.run(ToonMintApplication.class, args);
    }
}
