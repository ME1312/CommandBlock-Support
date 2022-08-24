package net.ME1312.CBS.ASM;

import java.net.URL;
import java.net.URLClassLoader;

public final class LibraryClassLoader extends ClassLoader {
    private final Reloaded child;

    public LibraryClassLoader(URL[] urls, String[] overrides) {
        super(LibraryClassLoader.class.getClassLoader());
        child = new Reloaded(urls, overrides, new Wrapped(this.getParent()));
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            // Search the child classloaders
            return child.findClass(name);
        } catch(ClassNotFoundException e) {
            // Fallback to the parent classloader
            return super.loadClass(name, resolve);
        }
    }

    private static final class Reloaded extends URLClassLoader {
        private final String[] whitelist;
        private final Wrapped next;

        private Reloaded(URL[] urls, String[] overrides, Wrapped next) {
            super(urls, null);
            this.next = next;
            whitelist = overrides;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                // First, try to load from the URLClassLoader
                for (String n : whitelist) if (name.startsWith(n)) return super.findClass(name);
            } catch (ClassNotFoundException e) {}

            // If that fails, we ask our real parent classloader to load the class (we give up)
            return next.loadClass(name);
        }
    }

    private static final class Wrapped extends ClassLoader {
        private Wrapped(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            return super.findClass(name);
        }
    }
}