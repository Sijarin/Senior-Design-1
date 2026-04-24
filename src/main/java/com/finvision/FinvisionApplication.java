package com.finvision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Entry point for the Finvision personal finance application.
 *
 * <p>Finvision is a Spring Boot web application that helps users track budgets,
 * bills, scanned receipts, and spending trends. It uses MongoDB as its primary
 * data store and Thymeleaf for server-side HTML rendering.</p>
 *
 * @author Finvision Team
 * @version 1.0.0
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FinvisionApplication {

    /**
     * Bootstraps the Spring application context and starts the embedded web server.
     *
     * @param args command-line arguments (none required)
     */
    public static void main(String[] args) {
        SpringApplication.run(FinvisionApplication.class, args);
    }
}
