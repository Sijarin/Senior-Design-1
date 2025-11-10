package com.finvision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FinvisionApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinvisionApplication.class, args);
    }

}
