package com.github.szsalyi.cassandra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.szsalyi.customizationpoc.UICustomizationService;
import com.github.szsalyi.dto.UIComponent;
import com.github.szsalyi.dto.UICustomization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("cassandra")
public class CassandraUICustomizationService implements UICustomizationService {

    private final CassandraUICustomizationRepository repository;
    private final ObjectMapper objectMapper;

    public CassandraUICustomizationService(
            CassandraUICustomizationRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<UICustomization> getCustomization(String userId) {
        return repository.findByUserId(userId)
                .singleOrEmpty().map(this::mapToUICustomization);
    }

    @Override
    public Mono<UICustomization> saveCustomization(UICustomization customization) {
        CassandraUICustomization entity = mapToCassandraEntity(customization);

        return repository.save(entity)
                .map(this::mapToUICustomization);
    }

    @Override
    public Mono<UICustomization> updateComponentOrder(String userId, String componentId, Integer newOrder) {
        return repository.findByUserId(userId)
                .flatMap(entity -> {
                    entity.getComponents().stream()
                            .filter(c -> c.getComponentId().equals(componentId))
                            .findFirst()
                            .ifPresent(c -> c.setDisplayOrder(newOrder));
                    entity.setUpdatedAt(Instant.now());
                    entity.setVersion(UUID.randomUUID().toString());
                    return repository.save(entity);
                })
                .singleOrEmpty().map(this::mapToUICustomization);
    }

    @Override
    public Mono<UICustomization> toggleComponentVisibility(String userId, String componentId) {
        return repository.findByUserId(userId)
                .flatMap(entity -> {
                    entity.getComponents().stream()
                            .filter(c -> c.getComponentId().equals(componentId))
                            .findFirst()
                            .ifPresent(c -> c.setVisible(!c.getVisible()));
                    entity.setUpdatedAt(Instant.now());
                    entity.setVersion(UUID.randomUUID().toString());
                    return repository.save(entity);
                })
                .singleOrEmpty().map(this::mapToUICustomization);
    }

    @Override
    public Mono<Void> deleteCustomization(String userId) {
        return repository.deleteByUserId(userId);
    }

    @Override
    public Flux<UIComponent> getComponentsByType(String userId, String componentType) {
        return repository.findByUserId(userId)
                .flatMap(entity -> Flux.fromIterable(entity.getComponents()))
                .filter(c -> c.getComponentType().equals(componentType))
                .map(this::mapToUIComponent);
    }

    private UICustomization mapToUICustomization(CassandraUICustomization entity) {
        List<UIComponent> components = entity.getComponents() != null ?
                entity.getComponents().stream()
                        .map(this::mapToUIComponent)
                        .collect(Collectors.toList()) : new ArrayList<>();

        return UICustomization.builder()
                .userId(entity.getUserId())
                .profileName(entity.getProfileName())
                .components(components)
                .createdAt(entity.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .updatedAt(entity.getUpdatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .version(entity.getVersion())
                .build();
    }

    private UIComponent mapToUIComponent(CassandraUIComponent entity) {
        Map<String, Object> properties = new HashMap<>();
        try {
            if (entity.getProperties() != null) {
                properties = objectMapper.readValue(entity.getProperties(), Map.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse component properties", e);
        }

        return UIComponent.builder()
                .componentId(entity.getComponentId())
                .componentType(entity.getComponentType())
                .name(entity.getName())
                .displayOrder(entity.getDisplayOrder())
                .visible(entity.getVisible())
                .properties(properties)
                .lastModified(entity.getLastModified().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
    }

    private CassandraUICustomization mapToCassandraEntity(UICustomization customization) {
        List<CassandraUIComponent> components = customization.getComponents().stream()
                .map(this::mapToCassandraComponent)
                .toList();

        return new CassandraUICustomization(
                customization.getUserId(),
                customization.getProfileName(),
                customization.getCreatedAt() != null ?
                        customization.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant() : Instant.now(),
                Instant.now(),
                UUID.randomUUID().toString(),
                components
        );
    }

    private CassandraUIComponent mapToCassandraComponent(UIComponent component) {
        String properties = "{}";

        try {
            properties = objectMapper.writeValueAsString(component.getProperties());
        } catch (Exception e) {
            log.warn("Failed to serialize component properties", e);
        }

        return new CassandraUIComponent(
                component.getComponentId(),
                component.getComponentType(),
                component.getName(),
                component.getDisplayOrder(),
                component.isVisible(),
                properties,
                Instant.now()
        );
    }
}
