package cbs.plugin;

import com.google.common.primitives.Primitives;

import java.lang.reflect.Array;
import java.util.function.Supplier;

@SuppressWarnings({"unchecked", "unused"})
final class Unsafe {
    // The main function of this class isn't used at the moment, but one would use it to store and load types that didn't exist in 1.7.10
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

    // Only misc utility methods beyond this point
    public String toString() {
        return toString(data);
    }

    static String toString(Object[] array) {
        StringBuilder builder = new StringBuilder();
        toString(builder, array);
        return builder.toString();
    }

    static void toString(StringBuilder str, Object arr) {
        str.append('{');
        int length = Array.getLength(arr);
        if (length != 0) for (int i = 0;;) {
            str.append(' ');
            Object obj = Array.get(arr, i);
            if (obj == null) {
                str.append("null");
            } else if (obj instanceof Character) {
                str.append('\'').append((char) obj).append('\'');
            } else if (obj instanceof String) {
                str.append('\"').append(obj.toString()).append('\"');
            } else if (obj instanceof Class) {
                str.append(((Class<?>) obj).getTypeName()).append(".class");
            } else {
                Class<?> type = obj.getClass();
                if (type.isArray()) {
                    if (i == 0) str.delete(str.length() - 1, str.length());
                    toString(str, obj);
                } else if (Primitives.isWrapperType(type)) {
                    str.append(obj.toString());
                } else {
                    str.append(type.getTypeName()).append('@').append(Integer.toHexString(obj.hashCode()));
                }
            }

            if (++i < length) {
                str.append(',');
            } else {
                if (str.codePointAt(str.length() - 1) != '}')
                    str.append(' ');
                break;
            }
        }
        str.append('}');
    }
}
