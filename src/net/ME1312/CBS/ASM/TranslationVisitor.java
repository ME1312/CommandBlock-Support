package net.ME1312.CBS.ASM;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static net.ME1312.CBS.ASM.PlayerVisitor.DEBUG;
import static org.objectweb.asm.Opcodes.*;

class TranslationVisitor extends ClassVisitor {
    private static final String TRANSLATION = Type.getDescriptor(net.ME1312.CBS.ASM.Translation.class);
    private static final String DEBUG_DESC = Type.getDescriptor(SuppressDebugging.class);

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

    private static final class Translate extends AnnotationVisitor {
        private final Multimap<String, String> map;
        private final String name, desc, returns;
        private String $name, $desc;

        private Translate(Multimap<String, String> map, String name, String descriptor, String returns) {
            super(ASM9);
            this.map = map;
            this.name = name;
            this.desc = descriptor;
            this.returns = returns;
        }

        @Override
        public void visit(String name, Object value) {
            $name = value.toString();
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("params".equals(name)) {
                return new AnnotationVisitor(ASM9) {
                    private final StringBuilder desc = new StringBuilder("(");

                    @Override
                    public void visit(String name, Object descriptor) {
                        desc.append(descriptor);
                    }

                    @Override
                    public void visitEnd() {
                        $desc = desc.append(')').append(returns).toString();
                    }
                };
            } else {
                return new AnnotationVisitor(ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                        return new Translate(map, Translate.this.name, desc, returns);
                    }
                };
            }
        }

        @Override
        public void visitEnd() {
            if ($name != null || $desc != null) {
                map.put(($name != null)? $name : name, ($desc != null)? $desc : desc);
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if ((access & ACC_PUBLIC) != 0 && (access & ACC_STATIC) == 0 && Character.isJavaIdentifierStart(name.charAt(0))) {
            return new MethodVisitor(ASM9) {
                private final Multimap<String, String> map = LinkedListMultimap.create();
                private boolean debug = true;

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (desc.equals(DEBUG_DESC)) {
                        debug = false;
                    } else if (desc.equals(TRANSLATION)) {
                        return new Translate(map, name, descriptor, Type.getReturnType(descriptor).getDescriptor());
                    }
                    return null;
                }

                @Override
                public void visitEnd() {
                    final Translation translation;
                    if ((access & ACC_ABSTRACT) == 0) {
                        translation = new Translation(name, descriptor, debug, null);
                    } else if (name.equals("getServer") && descriptor.startsWith("()")) {
                        translation = new Translation(name, descriptor, debug, (mv, returns) -> {
                            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Bukkit.class), "getServer", "()" + Type.getDescriptor(Server.class), false);
                            mv.visitInsn(ARETURN);
                        });
                    } else if (name.equals("getPlayer") && descriptor.startsWith("()")) {
                        translation = new Translation(name, descriptor, debug, (mv, returns) -> {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitInsn(ARETURN);
                        });
                    } else {
                        translation = null;
                    }

                    if (translation != null) {
                        for (Map.Entry<String, String> e : map.entries()) add(e.getKey(), e.getValue(), true, translation);
                        add(name, descriptor, false, translation);
                    }
                }
            };
        } else {
            return null;
        }
    }

    private void add(String name, String descriptor, boolean annotated, Translation translation) {
        TranslationVisitor.this.translations.compute(identify(name, descriptor), (key, value) -> {
            if (value == null) {
                if (DEBUG) log.info(((annotated)? "@Found: " : "Found:  ") + name + descriptor);
                return translation;
            } else {
                if (DEBUG && !annotated) log.info("Merged: " + name + descriptor);
                return value;
            }
        });
    }

    final static class Translation {
        final BiConsumer<MethodVisitor, Type> special;
        final String name, desc;
        private Class<?> returns;
        final boolean debug;

        private Translation(String name, String descriptor, boolean debug, BiConsumer<MethodVisitor, Type> special) {
            this.name = name;
            this.desc = descriptor;
            this.debug = debug;
            this.special = special;
        }

        boolean checkcast(Type type) {
            try {
                if (returns == null) returns = Class.forName(Type.getReturnType(desc).getClassName());
                return !Class.forName(type.getClassName()).isAssignableFrom(returns);
            } catch (ClassNotFoundException e) {
                return true;
            }
        }
    }
}
