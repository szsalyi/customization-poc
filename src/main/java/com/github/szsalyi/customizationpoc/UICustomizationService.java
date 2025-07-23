package com.github.szsalyi.customizationpoc;

import com.github.szsalyi.dto.UIComponent;
import com.github.szsalyi.dto.UICustomization;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UICustomizationService {
    Mono<UICustomization> getCustomization(String userId);
    Mono<UICustomization> saveCustomization(UICustomization customization);
    Mono<UICustomization> updateComponentOrder(String userId, String componentId, Integer newOrder);
    Mono<UICustomization> toggleComponentVisibility(String userId, String componentId);
    Mono<Void> deleteCustomization(String userId);
    Flux<UIComponent> getComponentsByType(String userId, String componentType);
}
