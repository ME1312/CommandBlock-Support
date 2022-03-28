package net.ME1312.CBS.ASM;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Translation {

    // Use this in EmulatedPlayer to specify additional translations provided by the @annotated method
    For[] value();

    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.ANNOTATION_TYPE)
    @interface For {

        // Use this to specify the actual method parameters
        // The amount and position of parameters in the translation must be the same as the @annotated method
        Class<?>[] value() default {};
    }
}
