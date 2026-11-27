package com.velan.production;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Velan Production Backend Starter Application.
 * Entry point for the modular monolith manufacturing execution system.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VelanProductionApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(VelanProductionApplication.class, args);
    }
}
