package net.ME1312.CBS.ASM;

import net.ME1312.CBS.EmulatedPlayer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.logging.Logger;

import static org.objectweb.asm.Opcodes.*;

public final class PlayerVisitor extends ClassVisitor {
    public static final String CLASS_PATH = "net/ME1312/CBS/EmulatedExtension";
    public static final String CLASS_NAME = CLASS_PATH.replace('/', '.');
    public static final int CLASS_VERSION = V1_8;

    private static final String EMU_PATH = Type.getInternalName(EmulatedPlayer.class);
    private static final String EMU_DESC = Type.getDescriptor(EmulatedPlayer.class);
    private static final String EMU_FIELD = "$";
    private static final String EMU_METHOD = EMU_FIELD;

    public static final boolean DEBUG = Boolean.getBoolean("cbs.debug");
    private static final String DEBUG_FIELD = "debug";
    private static final String DEBUG_METHOD = EMU_METHOD;

    private final HashSet<String> classes;
    private final HashSet<String> methods;
    private final ClassWriter cv;
    private final Logger log;
    private boolean spaced;
    public PlayerVisitor() {
        super(ASM9);
        classes = new HashSet<>();
        methods = new HashSet<>();
        log = Bukkit.getLogger();
        cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(CLASS_VERSION, ACC_PUBLIC | ACC_FINAL, CLASS_PATH, null, "java/lang/Object", new String[] { Type.getInternalName(Player.class) });
        cv.visitField(ACC_PRIVATE | ACC_FINAL, EMU_FIELD, EMU_DESC, null, null);
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(EmulatedPlayer.class)), null, null);
        mv.visitLabel(new Label());
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(DUP_X2);
        mv.visitFieldInsn(PUTFIELD, CLASS_PATH, EMU_FIELD, EMU_DESC);
        mv.visitMethodInsn(INVOKEVIRTUAL, EMU_PATH, EMU_METHOD, '(' + Type.getDescriptor(Player.class) + ")V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void log(String msg, String clazz) {
        log.warning("CBS > " + msg + clazz);
        spaced = false;
    }

    public PlayerVisitor scan(Class<?> clazz) throws IOException {
        String path = clazz.getCanonicalName().replace('.', '/');
        if (classes.add(path)) {
            scan(clazz, path);
        }
        return this;
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
            log("Failed to locate classfile: ", path.replace('/', '.'));
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
                log.info("CBS > Scanning class for implementable methods: " + name.replace('/', '.'));
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

    public byte[] flip() {
        return cv.toByteArray();
    }

    private static Class<?> translate(Type type) throws ClassNotFoundException {
        switch(type.getSort()) {
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
                return Array.newInstance(translate(type.getElementType()), 0).getClass();
            case Type.OBJECT:
                return Class.forName(type.getClassName());
            default:
                throw new AssertionError();
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if ((access & ACC_STATIC) == 0 && (access & ACC_FINAL) == 0) {
            String status = "Merged:     ";
            if (methods.add(name + descriptor)) {
                Type method = Type.getMethodType(descriptor);
                Type returns = method.getReturnType();
                Type[] params = method.getArgumentTypes();
                String translation = null;
                boolean debugging = true;
                boolean translated = false;
                final int length = params.length;
                status = "Skipped:    ";

                // Detect forwardable methods
                try {
                    Class<?>[] args = new Class[length];
                    for (int i = 0; i < length; ++i) {
                        args[i] = translate(params[i]);
                    }
                    Method m = EmulatedPlayer.class.getMethod(name, args);
                    debugging = !m.isAnnotationPresent(SuppressDebugging.class);
                    translation = Type.getMethodDescriptor(m);
                    translated = true;
                } catch (NoSuchMethodException | NoSuchMethodError e) {
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }

                // Insert debugging method
                if (translated || (access & ACC_ABSTRACT) != 0) {
                    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, name, descriptor, signature, exceptions);
                    mv.visitLabel(new Label());
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, CLASS_PATH, EMU_FIELD, EMU_DESC);
                    if (debugging) {
                        if (translated) mv.visitInsn(DUP);
                        mv.visitFieldInsn(GETFIELD, EMU_PATH, DEBUG_FIELD, "Z");
                        Label orElse = new Label();
                        mv.visitJumpInsn(IFEQ, orElse);
                        if (translated) {
                            mv.visitInsn(DUP);
                        } else {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, CLASS_PATH, EMU_FIELD, EMU_DESC);
                        }
                        mv.visitInsn((translated)? ICONST_1 : ICONST_0);
                        xldc(mv, returns);
                        xpush(mv, length);
                        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Class.class));
                        for (int i = 0; i < length; ++i) {
                            mv.visitInsn(DUP);
                            xpush(mv, i);
                            xldc(mv, params[i]);
                            mv.visitInsn(AASTORE);
                        }
                        xpush(mv, length);
                        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
                        for (int a = 1, i = 0; i < length; ++i) {
                            mv.visitInsn(DUP);
                            xpush(mv, i);
                            a += xload(mv, params[i], a, true);
                            mv.visitInsn(AASTORE);
                        }
                        mv.visitMethodInsn(INVOKEVIRTUAL, EMU_PATH, DEBUG_METHOD, "(ZLjava/lang/Class;[Ljava/lang/Class;[Ljava/lang/Object;)V", false);
                        mv.visitLabel(orElse);
                    }

                    // Handle method translation
                    if (translated) {
                        for (int a = 1, i = 0; i < length; ++i) {
                            a += xload(mv, params[i], a, false);
                        }
                        mv.visitMethodInsn(INVOKEVIRTUAL, EMU_PATH, name, translation, false);
                        status = "Translated: ";
                    } else {
                        status = "Defaulted:  ";
                    }
                    switch (returns.getSort()) {
                        case Type.VOID:
                            mv.visitInsn(RETURN);
                            break;
                        case Type.BOOLEAN:
                        case Type.CHAR:
                        case Type.BYTE:
                        case Type.SHORT:
                        case Type.INT:
                            if (!translated) mv.visitInsn(ICONST_0);
                            mv.visitInsn(IRETURN);
                            break;
                        case Type.FLOAT:
                            if (!translated) mv.visitInsn(FCONST_0);
                            mv.visitInsn(FRETURN);
                            break;
                        case Type.LONG:
                            if (!translated) mv.visitInsn(LCONST_0);
                            mv.visitInsn(LRETURN);
                            break;
                        case Type.DOUBLE:
                            if (!translated) mv.visitInsn(DCONST_0);
                            mv.visitInsn(DRETURN);
                            break;
                        case Type.ARRAY:
                        case Type.OBJECT:
                            if (!translated) mv.visitInsn(ACONST_NULL);
                            mv.visitInsn(ARETURN);
                            break;
                        default:
                            throw new AssertionError();
                    }
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
            }
            if (DEBUG) log.info(status + name + descriptor);
        }
        return null;
    }

    private static void xpush(MethodVisitor mv, int i) {
        switch (i) {
            case  0:
                mv.visitInsn(ICONST_0);
                return;
            case  1:
                mv.visitInsn(ICONST_1);
                return;
            case  2:
                mv.visitInsn(ICONST_2);
                return;
            case  3:
                mv.visitInsn(ICONST_3);
                return;
            case  4:
                mv.visitInsn(ICONST_4);
                return;
            case  5:
                mv.visitInsn(ICONST_5);
                return;
            default:
                mv.visitVarInsn((i > Byte.MAX_VALUE)? SIPUSH : BIPUSH, i);
        }
    }

    private static void xldc(MethodVisitor mv, Type type) {
        final Class<?> boxer;
        switch (type.getSort()) {
            case Type.VOID:
                boxer = Void.class;
                break;
            case Type.BOOLEAN:
                boxer = Boolean.class;
                break;
            case Type.CHAR:
                boxer = Character.class;
                break;
            case Type.BYTE:
                boxer = Byte.class;
                break;
            case Type.SHORT:
                boxer = Short.class;
                break;
            case Type.INT:
                boxer = Integer.class;
                break;
            case Type.FLOAT:
                boxer = Float.class;
                break;
            case Type.LONG:
                boxer = Long.class;
                break;
            case Type.DOUBLE:
                boxer = Double.class;
                break;
            case Type.ARRAY:
            case Type.OBJECT:
                mv.visitLdcInsn(type);
                return;
            default:
                throw new AssertionError();
        }
        mv.visitFieldInsn(GETSTATIC, Type.getInternalName(boxer), "TYPE", Type.getDescriptor(Class.class));
    }

    private static int xload(MethodVisitor mv, Type type, int i, boolean box) {
        final Method boxer;
        int arity = 1;
        try {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    mv.visitVarInsn(ILOAD, i);
                    boxer = Boolean.class.getMethod("valueOf", boolean.class);
                    break;
                case Type.CHAR:
                    mv.visitVarInsn(ILOAD, i);
                    boxer = Character.class.getMethod("valueOf", char.class);
                    break;
                case Type.BYTE:
                    mv.visitVarInsn(ILOAD, i);
                    boxer = Byte.class.getMethod("valueOf", byte.class);
                    break;
                case Type.SHORT:
                    mv.visitVarInsn(ILOAD, i);
                    boxer = Short.class.getMethod("valueOf", short.class);
                    break;
                case Type.INT:
                    mv.visitVarInsn(ILOAD, i);
                    boxer = Integer.class.getMethod("valueOf", int.class);
                    break;
                case Type.FLOAT:
                    mv.visitVarInsn(FLOAD, i);
                    boxer = Float.class.getMethod("valueOf", float.class);
                    break;
                case Type.LONG:
                    mv.visitVarInsn(LLOAD, i);
                    boxer = Long.class.getMethod("valueOf", long.class);
                    arity = 2;
                    break;
                case Type.DOUBLE:
                    mv.visitVarInsn(DLOAD, i);
                    boxer = Double.class.getMethod("valueOf", double.class);
                    arity = 2;
                    break;
                case Type.ARRAY:
                case Type.OBJECT:
                    mv.visitVarInsn(ALOAD, i);
                    return 1;
                default:
                    throw new AssertionError();
            }

            if (box) mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(boxer.getDeclaringClass()), boxer.getName(), Type.getMethodDescriptor(boxer), false);
            return arity;
        } catch (NoSuchMethodException | NoSuchMethodError e) {
            throw new AssertionError();
        }
    }
}
