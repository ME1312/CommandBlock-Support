package net.ME1312.CBS.ASM;

import net.ME1312.CBS.EmulatedPlayer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.UUID;

import static org.objectweb.asm.Opcodes.*;

public final class PlayerVisitor extends TranslationVisitor {
    public static final String CLASS_PATH = "net/ME1312/CBS/EmulatedExtension";
    public static final String CLASS_NAME = "net.ME1312.CBS.EmulatedExtension";
    public static final int CLASS_VERSION = V1_8;

    private static final String EMU_PATH = "net/ME1312/CBS/EmulatedPlayer";
    public static final boolean DEBUG = Boolean.getBoolean("cbs.debug");
    private static final String DEBUG_FIELD = "debug";
    private static final String DEBUG_METHOD = "$";

    private final HashSet<String> methods;
    private final ClassWriter cv;
    public PlayerVisitor() throws IOException {
        scan(EmulatedPlayer.class);
        classes.clear();
        methods = new HashSet<>();
        stage = "implementable methods"; flip = true;
        cv = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cv.visit(CLASS_VERSION, ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC, CLASS_PATH, null, EMU_PATH, new String[] { "org/bukkit/entity/Player" });
        String constructor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(UUID.class));
        MethodVisitor mv = cv.visitMethod(ACC_PRIVATE, "<init>", constructor, null, null);
        mv.visitLabel(new Label());
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, EMU_PATH, "<init>", constructor, false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        scan(Player.class);
    }

    public static MethodHandle generateExtension(Plugin plugin) throws Throwable {
        byte[] data = new PlayerVisitor().export();
        if (DEBUG && (plugin.getDataFolder().isDirectory() || plugin.getDataFolder().mkdirs())) {
            FileOutputStream fos = new FileOutputStream(new File(plugin.getDataFolder(), "EmulatedExtension.class"), false);
            fos.write(data);
            fos.close();
        }
        Constructor<?> constructor = new MemoryClassLoader(plugin.getClass().getClassLoader(), CLASS_NAME, data).loadClass(CLASS_NAME).getDeclaredConstructor(UUID.class);
        constructor.setAccessible(true);
        return MethodHandles.lookup().unreflectConstructor(constructor).asType(MethodType.methodType(EmulatedPlayer.class, UUID.class));
    }

    public byte[] export() {
        return cv.toByteArray();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String extended, String[] implemented) {
        if (flip) try {
            if (extended != null) scan(extended);
            if (implemented != null) for (String s : implemented) scan(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.visit(version, access, name, signature, extended, implemented);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (flip) {
            if ((access & (ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC)) == ACC_PUBLIC) {
                String status = "Merged:      ";
                if (methods.add(name + descriptor)) {
                    final Sender method = translations.get(identify(name, descriptor));
                    final Receiver translation = (method == null)? null : method.translate();
                    final boolean translated = translation != null;
                    status = "Skipped:     ";

                    if (translated && !translation.debug && translation.special == null && translation.desc.equals(descriptor)) {
                        // The method signatures and functionality are identical, so calls can be handled directly by the superclass
                        status = "@Redirected: ";

                    } else if (translated || (access & ACC_ABSTRACT) != 0) {
                        final Type returns = Type.getReturnType(descriptor);

                        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, name, descriptor, signature, exceptions);
                        mv.visitLabel(new Label());
                        if (translated && translation.special != null) {
                            // This translation uses special pre-prepared bytecode instructions
                            translation.special.accept(mv, returns);
                            status = "Implemented: ";
                        } else {
                            final Type[] params = Type.getArgumentTypes(descriptor);

                            // Install code for method debugging (unless @suppressed)
                            mv.visitVarInsn(ALOAD, 0);
                            if (!translated || translation.debug) {
                                if (translated) mv.visitInsn(DUP);
                                mv.visitFieldInsn(GETFIELD, EMU_PATH, DEBUG_FIELD, "Z");
                                Label orElse = new Label();
                                mv.visitJumpInsn(IFEQ, orElse);
                                if (translated) {
                                    mv.visitInsn(DUP);
                                } else {
                                    mv.visitVarInsn(ALOAD, 0);
                                }
                                mv.visitInsn((translated)? ICONST_1 : ICONST_0);
                                xldc(mv, returns);
                                xpush(mv, params.length);
                                mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
                                for (int i = 0; i < params.length; ++i) {
                                    mv.visitInsn(DUP);
                                    xpush(mv, i);
                                    xldc(mv, params[i]);
                                    mv.visitInsn(AASTORE);
                                }
                                xpush(mv, params.length);
                                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                                for (int a = 1, i = 0; i < params.length; ++i) {
                                    mv.visitInsn(DUP);
                                    xpush(mv, i);
                                    a += xload(mv, params[i], a, true);
                                    mv.visitInsn(AASTORE);
                                }
                                mv.visitMethodInsn(INVOKESPECIAL, EMU_PATH, DEBUG_METHOD, "(ZLjava/lang/Class;[Ljava/lang/Class;[Ljava/lang/Object;)V", false);
                                mv.visitLabel(orElse);
                            }

                            // Handle method translation
                            if (translated) {
                                int index = method.index;
                                int length = index + translation.params.length;
                                if (length > params.length) {
                                    length = params.length;
                                    index = Math.max(params.length - translation.params.length, 0);
                                }

                                int x = 0, i = 0, a = 1;
                                for (int type; i < index; ++i) {
                                    if ((type = params[i].getSort()) == Type.LONG || type == Type.DOUBLE) {
                                        a += 2;
                                    } else ++a;
                                }
                                for (Type param; i < length; ++x, ++i) {
                                    a += xload(mv, param = params[i], a, false);
                                    if (translation.checkcast(x, param)) mv.visitTypeInsn(CHECKCAST, translation.params[x].getInternalName());
                                }
                                mv.visitMethodInsn(INVOKESPECIAL, EMU_PATH, translation.name, translation.desc, false);
                                status = (translation.debug)? "Translated:  " : "@Translated: ";
                            } else {
                                status = "Defaulted:   ";
                            }

                            // Maybe return something
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
                                    else if (translation.checkcast(returns)) mv.visitTypeInsn(CHECKCAST, returns.getInternalName());
                                    mv.visitInsn(ARETURN);
                                    break;
                                default:
                                    throw new AssertionError();
                            }
                        }
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                    }
                }
                if (DEBUG) log.info(status + name + descriptor);
            }
            return null;
        } else {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    private static void xpush(MethodVisitor mv, int i) {
        switch (i) {
            case 0:
                mv.visitInsn(ICONST_0);
                return;
            case 1:
                mv.visitInsn(ICONST_1);
                return;
            case 2:
                mv.visitInsn(ICONST_2);
                return;
            case 3:
                mv.visitInsn(ICONST_3);
                return;
            case 4:
                mv.visitInsn(ICONST_4);
                return;
            case 5:
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
        mv.visitFieldInsn(GETSTATIC, Type.getInternalName(boxer), "TYPE", "Ljava/lang/Class;");
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
