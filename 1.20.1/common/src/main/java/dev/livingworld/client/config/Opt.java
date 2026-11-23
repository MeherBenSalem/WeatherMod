package dev.livingworld.client.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link ClientConfig} field as a user-facing option. The settings screen is generated from these
 * annotations, so adding a field here is all it takes to expose a new option (plus its lang entries
 * {@code livingworld.option.<field>} and {@code livingworld.option.<field>.tooltip}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Opt {
    Category value();

    float min() default 0f;

    float max() default 1f;

    float step() default 0.05f;

    /** Show the numeric value as a percentage. */
    boolean percent() default true;

    enum Category {
        GENERAL, CLIMATE, RAIN, SNOW, SAND, FOOTPRINTS, VEGETATION, FOG, WATER, SOUND, PLAYER, GRAPHICS, COMPAT, ACCESSIBILITY;

        public String key() {
            return "livingworld.category." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
