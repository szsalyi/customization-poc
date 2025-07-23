package com.github.szsalyi.cassandra;

import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CassandraUICustomizationRepository extends ReactiveCassandraRepository<CassandraUICustomization, String> {
    @Query("SELECT * FROM ui_customizations WHERE user_id = ?0")
    Flux<CassandraUICustomization> findByUserId(String userId);

    @Query("DELETE FROM ui_customizations WHERE user_id = ?0")
    Mono<Void> deleteByUserId(String userId);

    @Query("SELECT * FROM ui_customizations WHERE profile_name = ?0 ALLOW FILTERING")
    Flux<CassandraUICustomization> findByProfileName(String profileName);
}
