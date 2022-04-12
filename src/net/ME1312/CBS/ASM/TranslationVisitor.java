package net.ME1312.CBS.ASM;

import org.bukkit.Bukkit;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static net.ME1312.CBS.ASM.PlayerVisitor.DEBUG;
import static org.objectweb.asm.Opcodes.*;

class TranslationVisitor extends ClassVisitor {
    private static final String TRANSLATION = "Lnet/ME1312/CBS/ASM/Translation;";
    private static final String DEBUG_DESC = "Lnet/ME1312/CBS/ASM/SuppressDebugging;";

    final HashMap<String, Sender> translations = new HashMap<>();
    final HashSet<String> classes = new HashSet<>();
    boolean flip, spaced;
    final Logger log;
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

    void scan(String path) throws IOException {
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
        if (DEBUG) {
            if (!spaced) {
                log.info("");
                log.info("");
            }
            log.info("CBS > Scanning class for " + stage + ": " + name.replace('/', '.'));
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

    private static final class Parser extends AnnotationVisitor {
        private final List<Sender> senders;
        private final String name, desc, returns;
        private String $name, $desc;
        private int index;

        private Parser(List<Sender> senders, String name, String descriptor, String returns) {
            super(ASM9);
            this.senders = senders;
            this.name = name;
            this.desc = descriptor;
            this.returns = returns;
        }

        @Override
        public void visit(String name, Object value) {
            if ("index".equals(name)) {
                index = (int) (Integer) value;
            } else {
                $name = value.toString();
            }
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
                        return new Parser(senders, Parser.this.name, desc, returns);
                    }
                };
            }
        }

        @Override
        public void visitEnd() {
            if ($name != null || $desc != null) {
                senders.add(new Sender(($name != null)? $name : name, ($desc != null)? $desc : desc, index, true));
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (((access & ACC_PUBLIC) != 0 || (access & ACC_PROTECTED) != 0) && (access & (ACC_STATIC | ACC_SYNTHETIC)) == 0) {
            return new MethodVisitor(ASM9) {
                private final List<Sender> senders = new LinkedList<Sender>();
                private boolean debug = true;

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (desc.equals(DEBUG_DESC)) {
                        debug = false;
                    } else if (desc.equals(TRANSLATION)) {
                        return new Parser(senders, name, descriptor, Type.getReturnType(descriptor).getDescriptor());
                    }
                    return null;
                }

                @Override
                public void visitEnd() {
                    final Type[] params = Type.getArgumentTypes(descriptor);
                    final Receiver translation;
                    if ((access & ACC_ABSTRACT) == 0) {
                        translation = new Receiver(name, descriptor, params, debug, null);
                    } else if (name.equals("getServer") && params.length == 0) {
                        translation = new Receiver(name, descriptor, params, debug, (mv, returns) -> {
                            mv.visitMethodInsn(INVOKESTATIC, "org/bukkit/Bukkit", name, descriptor, false);
                            mv.visitInsn(ARETURN);
                        });
                    } else if (name.equals("getPlayer") && params.length == 0) {
                        translation = new Receiver(name, descriptor, params, debug, (mv, returns) -> {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitInsn(ARETURN);
                        });
                    } else {
                        return;
                    }

                    for (Sender sender : senders) add(sender, translation);
                    if ((access & ACC_PUBLIC) != 0) add(new Sender(name, descriptor, 0, false), translation);
                }
            };
        } else {
            return null;
        }
    }

    private void add(Sender sender, Receiver receiver) {
        translations.compute(identify(sender.name, sender.desc), (key, value) -> {
            if (value == null) {
                if (DEBUG) log.info(((sender.synthetic)? "@Found: " : "Found:  ") + sender.name + sender.desc);
                sender.translation = receiver;
                return sender;
            } else {
                if (DEBUG && !sender.synthetic) log.info("Merged: " + sender.name + sender.desc);
                return value;
            }
        });
    }

    final static class Sender {
        private Receiver translation;
        final String name, desc;
        final boolean synthetic;
        final int index;

        private Sender(String name, String descriptor, int index, boolean synthetic) {
            this.name = name;
            this.desc = descriptor;
            this.index = index;
            this.synthetic = synthetic;
        }

        Receiver translate() {
            return translation;
        }
    }

    final static class Receiver {
        final BiConsumer<MethodVisitor, Type> special;
        final String name, desc;
        final Type[] params;
        private final Class<?>[] args;
        private Class<?> type;
        final Type returns;
        final boolean debug;

        private Receiver(String name, String descriptor, Type[] params, boolean debug, BiConsumer<MethodVisitor, Type> special) {
            this.name = name;
            this.desc = descriptor;
            this.params = params;
            this.args = new Class[params.length];
            this.returns = Type.getReturnType(descriptor);
            this.debug = debug;
            this.special = special;
        }

        boolean checkcast(int param, Type type) {
            try {
                if (this.args[param] == null) this.args[param] = load(params[param]);
                return !load(type).isAssignableFrom(args[param]);
            } catch (ClassNotFoundException e) {
                return true;
            }
        }

        boolean checkcast(Type type) {
            try {
                if (this.type == null) this.type = load(returns);
                return !load(type).isAssignableFrom(this.type);
            } catch (ClassNotFoundException e) {
                return true;
            }
        }

        private static Class<?> load(Type type) throws ClassNotFoundException {
            switch (type.getSort()) {
                case Type.VOID:
                    return void.class;
                case Type.BOOLEAN:
                    return boolean.class;
                case Type.CHAR:
                    return char.class;
                case Type.BYTE:
                    return byte.class;
                case Type.SHORT:
                    return short.class;
                case Type.INT:
                    return int.class;
                case Type.FLOAT:
                    return float.class;
                case Type.LONG:
                    return long.class;
                case Type.DOUBLE:
                    return double.class;
                case Type.ARRAY:
                case Type.OBJECT:
                    return Class.forName(type.getInternalName().replace('/', '.'));
                default:
                    throw new AssertionError();
            }
        }
    }
}
