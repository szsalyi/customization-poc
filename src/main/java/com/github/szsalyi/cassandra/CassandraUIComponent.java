package com.github.szsalyi.cassandra;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;
import org.springframework.data.cassandra.core.mapping.Column;


import java.time.Instant;
import java.util.List;

/**
 * Represents a UI component that may contain subcomponents.
 * Example: Bank Accounts (carousel) with Main account, Euro account, Saving account as subcomponents.
 * The subComponentOrder field allows users to customize the order of subcomponents.
 */
@UserDefinedType("ui_component")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CassandraUIComponent {

    @Column(value = "component_id")
    @CassandraType(type = CassandraType.Name.TEXT)
    private String componentId;

    @Column(value = "component_type")
    @CassandraType(type = CassandraType.Name.TEXT)
    private String componentType;

    @CassandraType(type = CassandraType.Name.TEXT)
    private String name;

    @Column(value = "display_order")
    @CassandraType(type = CassandraType.Name.INT)
    private Integer displayOrder;

    @CassandraType(type = CassandraType.Name.BOOLEAN)
    private Boolean visible;

    @CassandraType(type = CassandraType.Name.TEXT)
    private String properties;

    @Column(value = "last_modified")
    @CassandraType(type = CassandraType.Name.TIMESTAMP)
    private Instant lastModified;

    /**
     * List of subcomponents belonging to this component.
     * Example: [Main account, Euro account, Saving account]
     */
    @CassandraType(type = CassandraType.Name.LIST, typeArguments = CassandraType.Name.UDT, userTypeName = "ui_component")
    private List<CassandraUIComponent> subComponents;
}
