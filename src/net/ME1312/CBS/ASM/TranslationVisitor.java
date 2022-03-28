package net.ME1312.CBS.ASM;

import org.bukkit.Bukkit;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import static net.ME1312.CBS.ASM.PlayerVisitor.DEBUG;
import static org.objectweb.asm.Opcodes.*;

class TranslationVisitor extends ClassVisitor {
    private static final String TRANSLATION = Type.getDescriptor(net.ME1312.CBS.ASM.Translation.class);
    static final String DEBUG_DESC = Type.getDescriptor(SuppressDebugging.class);
    static final String HIDDEN_METHOD = "$";

    final HashMap<String, Translation> translations = new HashMap<>();
    final HashSet<String> classes = new HashSet<>();
    final Logger log;
    boolean spaced;
    String stage;
    public TranslationVisitor() {
        super(ASM9);
        log = Bukkit.getLogger();
        stage = "translations";
    }

    void log(String msg, String clazz) {
        log.warning("CBS > " + msg + clazz);
        spaced = false;
    }

    void scan(Class<?> clazz) throws IOException {
        String path = clazz.getCanonicalName().replace('.', '/');
        if (classes.add(path)) {
            scan(clazz, path);
        }
    }

    private void scan(String path) throws IOException {
        if (classes.add(path)) {
            try {
                scan(Class.forName(path.replace('/', '.')), path);
            } catch (ClassNotFoundException e) {
                if (DEBUG) log("Failed to locate class: ", path.replace('/', '.'));
            }
        }
    }

    private void scan(Class<?> clazz, String path) throws IOException {
        InputStream stream = clazz.getResourceAsStream('/' + path + ".class");
        if (stream != null) {
            ClassReader reader = new ClassReader(stream);
            reader.accept(this, 0);
        } else if (DEBUG) {
            log("Failed to locate classfile: ", clazz.getCanonicalName());
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String extended, String[] implemented) {
        try {
            if (implemented != null) for (String s : implemented) scan(s);
            if (extended != null) scan(extended);
            if (DEBUG) {
                if (!spaced) {
                    log.info("");
                    log.info("");
                }
                log.info("CBS > Scanning class for " + stage + ": " + name.replace('/', '.'));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void visitEnd() {
        if (DEBUG) {
            log.info("");
            log.info("");
            spaced = true;
        }
    }

    static String identify(String name, String descriptor) {
        int index = descriptor.indexOf(')');
        if (index == -1) throw new IllegalArgumentException(name + descriptor);
        return name + descriptor.substring(0, index + 1);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if ((access & ACC_PUBLIC) != 0 && (access & ACC_STATIC) == 0 && (access & ACC_ABSTRACT) == 0 && !name.equals(HIDDEN_METHOD) && Character.isJavaIdentifierStart(name.charAt(0))) {
            return new MethodVisitor(ASM9) {
                private final ArrayList<String> translations = new ArrayList<>();
                private boolean debugging = true;

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (descriptor.equals(DEBUG_DESC)) debugging = false;
                    else if (descriptor.equals(TRANSLATION)) {
                        final String returns = Type.getReturnType(descriptor).getDescriptor();
                        return new AnnotationVisitor(ASM9) {
                            @Override
                            public AnnotationVisitor visitArray(String key) {
                                return new AnnotationVisitor(ASM9) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String key, String descriptor) {
                                        return new AnnotationVisitor(ASM9) {
                                            @Override
                                            public AnnotationVisitor visitArray(String key) {
                                                return new AnnotationVisitor(ASM9) {
                                                    private final StringBuilder descriptor = new StringBuilder("(");

                                                    @Override
                                                    public void visit(String key, Object descriptor) {
                                                        this.descriptor.append(descriptor);
                                                    }

                                                    @Override
                                                    public void visitEnd() {
                                                        translations.add(descriptor.append(')').append(returns).toString());
                                                    }
                                                };
                                            }
                                        };
                                    }
                                };
                            }
                        };
                    }
                    return null;
                }

                @Override
                public void visitEnd() {
                    translations.add(descriptor);
                    for (String descriptor : translations) TranslationVisitor.this.translations.compute(identify(name, descriptor), (key, value) -> {
                        if (value == null) {
                            if (DEBUG) log.info("Found:  " + name + descriptor);
                            return new Translation(name, descriptor, debugging);
                        } else {
                            if (DEBUG) log.info("Merged: " + name + descriptor);
                            return value;
                        }
                    });
                }
            };
        } else {
            return null;
        }
    }

    static final class Translation {
        final String name, descriptor;
        final boolean debugging;
        private Class<?> returns;

        public Translation(String name, String descriptor, boolean debugging) {
            this.name = name;
            this.descriptor = descriptor;
            this.debugging = debugging;
        }

        boolean checkcast(Type type) {
            try {
                if (returns == null) returns = Class.forName(Type.getReturnType(descriptor).getClassName());
                return !Class.forName(type.getClassName()).isAssignableFrom(returns);
            } catch (ClassNotFoundException e) {
                return true;
            }
        }
    }
}
