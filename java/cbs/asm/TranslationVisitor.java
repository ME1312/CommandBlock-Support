package cbs.asm;

import org.bukkit.Bukkit;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static bridge.asm.Types.load;
import static org.objectweb.asm.Opcodes.*;

class TranslationVisitor extends ClassVisitor {
    private static final int MAX_ARITY = 255;
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
                if (PlayerVisitor.DEBUG) log("Failed to locate class: ", path.replace('/', '.'));
            }
        }
    }

    private void scan(Class<?> clazz, String path) throws IOException {
        InputStream stream = clazz.getResourceAsStream('/' + path + ".class");
        if (stream != null) {
            ClassReader reader = new ClassReader(stream);
            reader.accept(this, 0);
        } else if (PlayerVisitor.DEBUG) {
            log("Failed to locate classfile: ", clazz.getCanonicalName());
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String extended, String[] implemented) {
        if (PlayerVisitor.DEBUG) {
            if (!spaced) {
                log.info("");
                log.info("");
            }
            log.info("CBS > Scanning class for " + stage + ": " + name.replace('/', '.'));
        }
    }

    @Override
    public void visitEnd() {
        if (PlayerVisitor.DEBUG) {
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
        private int fromIndex, toIndex, length = MAX_ARITY;
        private final List<Sender> senders;
        private final String name, desc;
        private String $name, $desc;


        Parser(String name, String descriptor, List<Sender> senders) {
            super(ASM9);
            this.name = $name = name;
            this.desc = $desc = descriptor;
            this.senders = senders;
        }

        @Override
        public void visit(String name, Object value) {
            if ("name".equals(name)) {
                $name = value.toString();
            } else if ("fromIndex".equals(name)) {
                if ((fromIndex = Math.min((int) value, MAX_ARITY)) < 0) fromIndex = 0;
            } else if ("toIndex".equals(name)) {
                if ((toIndex = Math.min((int) value, MAX_ARITY)) < 0) toIndex = 0;
            } else if ("length".equals(name)) {
                if ((length = Math.min((int) value, MAX_ARITY)) < 0) length = 0;
            } else if ("returns".equals(name)) {
                String desc = $desc;
                $desc = new StringBuilder(desc).replace(desc.indexOf(')') + 1, desc.length(), value.toString()).toString();
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("params".equals(name)) {
                return new AnnotationVisitor(ASM9) {
                    private final StringBuilder desc = new StringBuilder().append('(');

                    @Override
                    public void visit(String name, Object descriptor) {
                        desc.append(descriptor);
                    }

                    @Override
                    public void visitEnd() {
                        String desc = $desc;
                        $desc = this.desc.append(')').append(desc, desc.indexOf(')') + 1, desc.length()).toString();
                    }
                };
            } else {
                return new AnnotationVisitor(ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                        return new Parser(Parser.this.name, desc, senders);
                    }
                };
            }
        }

        @Override
        public void visitEnd() {
            if (!$name.equals(name) || !$desc.equals(desc)) {
                senders.add(new Sender($name, $desc, fromIndex, toIndex, length, true));
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (((access & (ACC_PUBLIC | ACC_FINAL)) == ACC_PUBLIC || (access & ACC_PROTECTED) != 0) && (access & (ACC_STATIC | ACC_SYNTHETIC)) == 0) {
            return new MethodVisitor(ASM9) {
                private final List<Sender> senders = new LinkedList<Sender>();
                private boolean debug = true;

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (desc.equals("Lcbs/asm/SuppressDebugging;")) {
                        debug = false;
                    } else if (desc.equals("Lbridge/Bridges;") || desc.equals("Lbridge/Bridge;")) {
                        return new Parser(name, descriptor, senders);
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
                        translation = new Receiver(name, descriptor, params, debug, mv -> {
                            mv.visitMethodInsn(INVOKESTATIC, "org/bukkit/Bukkit", name, descriptor, false);
                            mv.visitInsn(ARETURN);
                        });
                    } else if (name.equals("getPlayer") && params.length == 0) {
                        translation = new Receiver(name, descriptor, params, debug, mv -> {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitInsn(ARETURN);
                        });
                    } else {
                        return;
                    }

                    for (Sender sender : senders) add(sender, translation);
                    if ((access & ACC_FINAL) == 0) add(new Sender(name, descriptor, 0, 0, MAX_ARITY, false), translation);
                }
            };
        } else {
            return null;
        }
    }

    private void add(Sender sender, Receiver receiver) {
        translations.compute(identify(sender.name, sender.desc), (key, value) -> {
            if (value == null) {
                if (PlayerVisitor.DEBUG) log.info(((sender.synthetic)? "@Found: " : "Found:  ") + sender.name + sender.desc);
                sender.translation = receiver;
                return sender;
            } else {
                if (PlayerVisitor.DEBUG && !sender.synthetic) log.info("Merged: " + sender.name + sender.desc);
                return value;
            }
        });
    }

    final static class Sender {
        private Receiver translation;
        final String name, desc;
        final int fromIndex, toIndex, length;
        final boolean synthetic;

        private Sender(String name, String descriptor, int fromIndex, int toIndex, int length, boolean synthetic) {
            this.name = name;
            this.desc = descriptor;
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.length = length;
            this.synthetic = synthetic;
        }

        Receiver translate() {
            return translation;
        }
    }

    final static class Receiver {
        final Consumer<MethodVisitor> special;
        final String name, desc;
        final Type[] params;
        private final Class<?>[] args;
        private Class<?> type;
        final Type returns;
        final boolean debug;

        private Receiver(String name, String descriptor, Type[] params, boolean debug, Consumer<MethodVisitor> special) {
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
                ClassLoader loader = ASM.class.getClassLoader();
                if (this.args[param] == null) this.args[param] = load(loader, params[param]);
                return !load(loader, type).isAssignableFrom(args[param]);
            } catch (ClassNotFoundException e) {
                return true;
            }
        }

        boolean checkcast(Type type) {
            try {
                ClassLoader loader = ASM.class.getClassLoader();
                if (this.type == null) this.type = load(loader, returns);
                return !load(loader, type).isAssignableFrom(this.type);
            } catch (ClassNotFoundException e) {
                return true;
            }
        }
    }
}
