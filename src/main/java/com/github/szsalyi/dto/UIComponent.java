package com.github.szsalyi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class UIComponent {
    private String componentId;
    private String componentType; // HEADER, SIDEBAR, WIDGET, etc.
    private String name;
    private Integer displayOrder;
    private boolean visible;
    private Map<String, Object> properties;
    private LocalDateTime lastModified;
}
