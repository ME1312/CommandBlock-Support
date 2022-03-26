package net.ME1312.CBS.ASM;

public final class MemoryClassLoader extends ClassLoader {
    private final String name;
    private final byte[] data;

    public MemoryClassLoader(ClassLoader parent, String name, byte[] data) {
        super(parent);
        this.name = name;
        this.data = data;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (this.name.equals(name)) {
            return super.defineClass(name, data, 0, data.length);
        } else return super.findClass(name);
    }
}
