package net.ME1312.CBS.ASM;

import java.net.URL;
import java.net.URLClassLoader;

public final class LibraryClassLoader extends ClassLoader {
    private final Reload child;

    public LibraryClassLoader(URL[] urls, String[] overrides) {
        super(LibraryClassLoader.class.getClassLoader());
        child = new Reload(urls, overrides, new Wrapper(this.getParent()));
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

    private static final class Reload extends URLClassLoader {
        private final String[] whitelist;
        private final Wrapper next;

        private Reload(URL[] urls, String[] overrides, Wrapper next) {
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

    private static final class Wrapper extends ClassLoader {
        private Wrapper(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            return super.findClass(name);
        }
    }
}