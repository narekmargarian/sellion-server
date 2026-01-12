package com.sellion.sellionserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SellionServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SellionServerApplication.class, args);
    }

}
