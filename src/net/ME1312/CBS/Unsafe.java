package net.ME1312.CBS;

import java.util.function.Supplier;

@SuppressWarnings({"unchecked", "unused"})
final class Unsafe {
    // This class isn't used at the moment, but one would use it to store and load types that didn't exist in 1.7.10
    // This text will be replaced by static constants for the magic number accessors once we start using it
    private static final int length = 0;
    final Object[] data = new Object[length];
    Unsafe() {}

    <T> T get(int entry) {
        return (T) data[entry];
    }

    <T> T get(int entry, Supplier<? extends T> supplier) {
        T unsafe = (T) data[entry];
        if (unsafe == null) data[entry] = unsafe = supplier.get();
        return unsafe;
    }
}
