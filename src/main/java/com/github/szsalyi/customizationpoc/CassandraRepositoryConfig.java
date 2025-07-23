package com.github.szsalyi.customizationpoc;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories;

@Configuration
@Profile("cassandra")
@EnableReactiveCassandraRepositories(basePackages = "com.github.szsalyi.cassandra")
public class CassandraRepositoryConfig {
    // Cassandra repositories enabled only for Cassandra profile
}