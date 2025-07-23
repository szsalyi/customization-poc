package com.github.szsalyi.oracle;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("oracle")
public interface OracleUIComponentRepository extends JpaRepository<OracleUIComponent, Long> {
    List<OracleUIComponent> findByCustomization_UserIdOrderByDisplayOrder(String userId);
    List<OracleUIComponent> findByComponentTypeAndCustomization_UserId(String componentType, String userId);

    @Modifying
    @Transactional
    @Query("UPDATE OracleUIComponent c SET c.displayOrder = :newOrder WHERE c.componentId = :componentId")
    void updateDisplayOrder(@Param("componentId") String componentId, @Param("newOrder") Integer newOrder);
}