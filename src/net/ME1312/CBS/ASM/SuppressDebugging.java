package net.ME1312.CBS.ASM;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
public @interface SuppressDebugging {
    // Use this in EmulatedPlayer to uninstall runtime debugging for specific methods
}
