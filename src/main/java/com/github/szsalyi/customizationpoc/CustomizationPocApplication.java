package com.github.szsalyi.customizationpoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.github.szsalyi")
public class CustomizationPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomizationPocApplication.class, args);
    }

}
