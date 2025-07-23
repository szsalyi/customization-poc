package com.github.szsalyi.customizationpoc;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Profile({"oracle", "h2"})
@EntityScan("com.github.szsalyi.oracle")
@EnableJpaRepositories(basePackages = "com.github.szsalyi.oracle")
public class JpaRepositoryConfig {
    // JPA repositories enabled only for Oracle and H2 profiles
}