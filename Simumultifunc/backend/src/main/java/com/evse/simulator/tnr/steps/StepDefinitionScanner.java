package com.evse.simulator.tnr.steps;

import com.evse.simulator.tnr.steps.annotations.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Scanner qui découvre et enregistre automatiquement les step definitions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StepDefinitionScanner {

    private final ApplicationContext applicationContext;
    private final StepRegistry stepRegistry;

    @PostConstruct
    public void scanAndRegister() {
        log.info("Scanning for step definitions...");

        // Trouver tous les beans annotés avec @StepDefinitions
        Map<String, Object> stepBeans = applicationContext.getBeansWithAnnotation(StepDefinitions.class);

        int totalSteps = 0;
        for (Map.Entry<String, Object> entry : stepBeans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> clazz = bean.getClass();

            StepDefinitions annotation = clazz.getAnnotation(StepDefinitions.class);
            String category = annotation != null ? annotation.category() : "general";

            log.debug("Scanning step definitions in: {} (category: {})", clazz.getSimpleName(), category);

            for (Method method : clazz.getDeclaredMethods()) {
                // Given steps
                Given given = method.getAnnotation(Given.class);
                if (given != null) {
                    stepRegistry.registerGiven(given.value(), bean, method, given.description());
                    totalSteps++;
                }

                // When steps
                When when = method.getAnnotation(When.class);
                if (when != null) {
                    stepRegistry.registerWhen(when.value(), bean, method, when.description());
                    totalSteps++;
                }

                // Then steps
                Then then = method.getAnnotation(Then.class);
                if (then != null) {
                    stepRegistry.registerThen(then.value(), bean, method, then.description());
                    totalSteps++;
                }
            }
        }

        log.info("Registered {} step definitions from {} classes", totalSteps, stepBeans.size());
    }
}
