package com.springboot.demo.scheduled;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling()
public class DemoScheduledApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoScheduledApplication.class, args);
    }

}
