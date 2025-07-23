package com.github.szsalyi.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.NumericJdbcType;

import java.time.LocalDateTime;

@Entity
@Table(name = "ui_components")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OracleUIComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "component_id", nullable = false)
    private String componentId;

    @Column(name = "component_type", nullable = false)
    private String componentType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "visible", nullable = false, columnDefinition = "NUMBER(1)")
//    @JdbcType(NumericJdbcType.class)
    private Boolean visible;

    @Column(name = "properties", columnDefinition = "CLOB")
    private String properties; // JSON string

    @Column(name = "last_modified", nullable = false)
    private LocalDateTime lastModified;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private OracleUICustomization customization;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastModified = LocalDateTime.now();
    }
}