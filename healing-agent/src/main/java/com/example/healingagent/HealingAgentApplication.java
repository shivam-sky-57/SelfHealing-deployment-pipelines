package com.example.healingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties
public class HealingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealingAgentApplication.class, args);
    }
}
