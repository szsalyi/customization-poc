package com.github.szsalyi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UICustomization {
    private String userId;
    private String profileName;
    private List<UIComponent> components;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String version;
}
