package com.github.szsalyi.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.szsalyi.customizationpoc.UICustomizationService;
import com.github.szsalyi.dto.UIComponent;
import com.github.szsalyi.dto.UICustomization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("oracle")
public class OracleUICustomizationService implements UICustomizationService {

    private final OracleUICustomizationRepository customizationRepository;
    private final OracleUIComponentRepository componentRepository;
    private final ObjectMapper objectMapper;

    public OracleUICustomizationService(
            OracleUICustomizationRepository customizationRepository,
            OracleUIComponentRepository componentRepository,
            ObjectMapper objectMapper) {
        this.customizationRepository = customizationRepository;
        this.componentRepository = componentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<UICustomization> getCustomization(String userId) {
        return Mono.fromCallable(() -> customizationRepository.findByUserId(userId))
                .flatMap(optional ->
                        optional.map(this::mapToUICustomization)
                                .map(Mono::just)
                                .orElse(Mono.empty())
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    @Transactional
    public Mono<UICustomization> saveCustomization(UICustomization customization) {
        return Mono.fromCallable(() -> {
            OracleUICustomization entity = mapToOracleEntity(customization);
            OracleUICustomization saved = customizationRepository.save(entity);
            return mapToUICustomization(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<UICustomization> updateComponentOrder(String userId, String componentId, Integer newOrder) {
        return Mono.fromCallable(() -> {
            Optional<OracleUICustomization> customizationOpt = customizationRepository.findByUserId(userId);

            if (customizationOpt.isEmpty()) {
                return null;
            }

            // Update the component order
            componentRepository.updateDisplayOrder(componentId, newOrder);

            // Return the updated customization
            return mapToUICustomization(customizationOpt.get());
        })
        .subscribeOn(Schedulers.boundedElastic())
        .filter(Objects::nonNull); // Filter out null results - will emit empty Mono

    }

    @Override
    @Transactional
    public Mono<UICustomization> toggleComponentVisibility(String userId, String componentId) {
        return Mono.fromCallable(() -> {
            Optional<OracleUICustomization> customization = customizationRepository.findByUserId(userId);
            if (customization.isPresent()) {
                OracleUIComponent component = customization.get().getComponents().stream()
                        .filter(c -> c.getComponentId().equals(componentId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Component not found"));

                component.setVisible(!component.getVisible());
                OracleUICustomization saved = customizationRepository.save(customization.get());
                return mapToUICustomization(saved);
            }
            throw new RuntimeException("Customization not found");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    @Transactional
    public Mono<Void> deleteCustomization(String userId) {
        return Mono.fromRunnable(() -> customizationRepository.deleteByUserId(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Flux<UIComponent> getComponentsByType(String userId, String componentType) {
        return Mono.fromCallable(() -> componentRepository.findByComponentTypeAndCustomization_UserId(componentType, userId))
                .flatMapMany(Flux::fromIterable)
                .map(this::mapToUIComponent)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private UICustomization mapToUICustomization(OracleUICustomization entity) {
        List<UIComponent> components = entity.getComponents().stream()
                .map(this::mapToUIComponent)
                .collect(Collectors.toList());

        UICustomization build = UICustomization.builder()
                .userId(entity.getUserId())
                .profileName(entity.getProfileName())
                .components(components)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
        return build;
    }

    private UIComponent mapToUIComponent(OracleUIComponent entity) {
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
                .lastModified(entity.getLastModified())
                .build();
    }

    private OracleUICustomization mapToOracleEntity(UICustomization customization) {
        OracleUICustomization entity = new OracleUICustomization();
        entity.setUserId(customization.getUserId());
        entity.setProfileName(customization.getProfileName());
        entity.setVersion(customization.getVersion());

        List<OracleUIComponent> components = customization.getComponents().stream()
                .map(c -> mapToOracleComponent(c, entity))
                .collect(Collectors.toList());
        entity.setComponents(components);

        return entity;
    }

    private OracleUIComponent mapToOracleComponent(UIComponent component, OracleUICustomization customization) {
        OracleUIComponent entity = new OracleUIComponent();
        entity.setComponentId(component.getComponentId());
        entity.setComponentType(component.getComponentType());
        entity.setName(component.getName());
        entity.setDisplayOrder(component.getDisplayOrder());
        entity.setVisible(component.isVisible());
        entity.setCustomization(customization);

        try {
            entity.setProperties(objectMapper.writeValueAsString(component.getProperties()));
        } catch (Exception e) {
            log.warn("Failed to serialize component properties", e);
            entity.setProperties("{}");
        }

        return entity;
    }
}
