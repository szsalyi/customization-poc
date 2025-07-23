package com.github.szsalyi.oracle;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("oracle")
public interface OracleUICustomizationRepository extends JpaRepository<OracleUICustomization, String> {
    @EntityGraph(attributePaths = {"components"})
    Optional<OracleUICustomization> findById(String id);
    List<OracleUICustomization> findByProfileNameContaining(String profileName);

    @Modifying
    @Query("DELETE FROM OracleUICustomization u WHERE u.id = :id")
    void deleteByUserId(@Param("userId") String userId);
}
