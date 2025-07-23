package com.github.szsalyi.cassandra;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.List;

@Table("ui_customizations")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CassandraUICustomization {
    @PrimaryKey
    @Column("user_id")
    @CassandraType(type = CassandraType.Name.TEXT)
    private String userId;

    @Column("profile_name")
    @CassandraType(type = CassandraType.Name.TEXT)
    private String profileName;

    @Column("created_at")
    @CassandraType(type = CassandraType.Name.TIMESTAMP)
    private Instant createdAt;

    @Column("updated_at")
    @CassandraType(type = CassandraType.Name.TIMESTAMP)
    private Instant updatedAt;

    @CassandraType(type = CassandraType.Name.TEXT)
    private String version;

    @CassandraType(type = CassandraType.Name.LIST, typeArguments = CassandraType.Name.UDT, userTypeName = "ui_component")
    private List<CassandraUIComponent> components;
}