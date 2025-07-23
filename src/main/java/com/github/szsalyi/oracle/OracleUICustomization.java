package com.github.szsalyi.oracle;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "UI_CUSTOMIZATIONS")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OracleUICustomization {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "profile_name", nullable = false)
    private String profileName;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "version", nullable = false)
    private String version;

    @OneToMany(mappedBy = "customization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OracleUIComponent> components = new ArrayList<>();
}