package com.github.szsalyi.oracle;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("oracle")
public interface OracleUIComponentRepository extends JpaRepository<OracleUIComponent, Long> {
    List<OracleUIComponent> findByComponentTypeAndCustomization_Id(String componentType, String id);

    @Modifying
    @Transactional
    @Query("UPDATE OracleUIComponent c SET c.displayOrder = :newOrder WHERE c.componentId = :componentId")
    void updateDisplayOrder(@Param("componentId") String componentId, @Param("newOrder") Integer newOrder);
}