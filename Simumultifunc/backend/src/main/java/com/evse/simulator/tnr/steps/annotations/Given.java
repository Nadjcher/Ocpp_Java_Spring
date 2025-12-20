package com.evse.simulator.tnr.steps.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour définir un step Given.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Given {
    /**
     * Pattern Gherkin du step.
     * Supporte les placeholders: {word}, {string}, {int}, {float}, {any}
     */
    String value();

    /**
     * Description du step.
     */
    String description() default "";
}
