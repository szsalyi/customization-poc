package com.github.szsalyi.customizationpoc;

import com.github.szsalyi.dto.UIComponent;
import com.github.szsalyi.dto.UICustomization;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ui-customization")
@Slf4j
@Validated
public class UICustomizationController {

    private final UICustomizationService customizationService;

    public UICustomizationController(UICustomizationService customizationService) {
        this.customizationService = customizationService;
    }

    @GetMapping("/{userId}")
    public Mono<ResponseEntity<UICustomization>> getCustomization(@PathVariable String userId) {
        return customizationService.getCustomization(userId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping
    public Mono<ResponseEntity<UICustomization>> createCustomization(@RequestBody @Valid UICustomization customization) {
        return customizationService.saveCustomization(customization)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .onErrorResume(e -> {
                    log.error("Failed to create customization", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PutMapping("/{userId}")
    public Mono<ResponseEntity<UICustomization>> updateCustomization(
            @PathVariable String userId,
            @RequestBody @Valid UICustomization customization) {
        customization.setUserId(userId);
        return customizationService.saveCustomization(customization)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to update customization for user: {}", userId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PatchMapping("/{userId}/components/{componentId}/order")
    public Mono<ResponseEntity<UICustomization>> updateComponentOrder(
            @PathVariable String userId,
            @PathVariable String componentId,
            @RequestParam Integer newOrder) {
        return customizationService.updateComponentOrder(userId, componentId, newOrder)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to update component order", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PatchMapping("/{userId}/components/{componentId}/visibility")
    public Mono<ResponseEntity<UICustomization>> toggleComponentVisibility(
            @PathVariable String userId,
            @PathVariable String componentId) {
        return customizationService.toggleComponentVisibility(userId, componentId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to toggle component visibility", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @DeleteMapping("/{userId}")
    public Mono<ResponseEntity<Void>> deleteCustomization(@PathVariable String userId) {
        return customizationService.deleteCustomization(userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    log.error("Failed to delete customization for user: {}", userId, e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/{userId}/components")
    public Flux<UIComponent> getComponentsByType(
            @PathVariable String userId,
            @RequestParam(required = false) String componentType) {
        if (componentType != null) {
            return customizationService.getComponentsByType(userId, componentType);
        }
        return customizationService.getCustomization(userId)
                .flatMapMany(customization -> Flux.fromIterable(customization.getComponents()));
    }
}
