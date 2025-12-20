package com.evse.simulator.tnr.steps.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une classe comme contenant des définitions de steps TNR.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface StepDefinitions {
    /**
     * Catégorie des steps (ex: "ocpp", "session", "smartcharging").
     */
    String category() default "general";

    /**
     * Description de ce groupe de steps.
     */
    String description() default "";
}
