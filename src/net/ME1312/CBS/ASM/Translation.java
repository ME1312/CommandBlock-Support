package net.ME1312.CBS.ASM;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
public @interface Translation {

    // Use this to specify a different method name
    String name() default "";

    // Use this to specify different method parameters
    // The amount and position of parameters in the translation must be the same as those in the @annotated method
    Class<?>[] params() default {};

    // Use this to specify multiple additional translation mappings
    // To preserve code clarity, this should not be combined with the fields above
    For[] value() default {};


    @Target(ElementType.ANNOTATION_TYPE)
    @interface For {

        // These function the same as the fields above
        String name() default "";
        Class<?>[] params() default {};
    }
}
