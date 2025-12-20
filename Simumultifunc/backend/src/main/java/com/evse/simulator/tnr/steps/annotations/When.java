package com.evse.simulator.tnr.steps.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour définir un step When.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface When {
    /**
     * Pattern Gherkin du step.
     */
    String value();

    /**
     * Description du step.
     */
    String description() default "";
}
