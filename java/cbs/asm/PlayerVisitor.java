package cbs.asm;

import cbs.plugin.EmulatedPlayer;
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
import java.util.HashSet;
import java.util.UUID;

import static bridge.asm.Types.*;
import static org.objectweb.asm.Opcodes.*;

public final class PlayerVisitor extends TranslationVisitor {
    public static final String CLASS_PATH = "cbs/plugin/EmulatedExtension";
    public static final String CLASS_NAME = "cbs.plugin.EmulatedExtension";
    public static final int CLASS_VERSION = V1_8;

    private static final String EMU_PATH = "cbs/plugin/EmulatedPlayer";
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
        String constructor = Type.getMethodDescriptor(VOID_TYPE, Type.getType(UUID.class));
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
                        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, name, descriptor, signature, exceptions);
                        mv.visitLabel(new Label());

                        if (translated && translation.special != null) {
                            // This translation uses special pre-prepared bytecode instructions
                            translation.special.accept(mv);
                            status = "Implemented: ";
                        } else {
                            final Type[] params = Type.getArgumentTypes(descriptor);
                            final Type returns = Type.getReturnType(descriptor);

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
                                push(mv, translated);
                                push(mv, returns);
                                push(mv, params.length);
                                mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
                                for (int i = 0; i < params.length; ++i) {
                                    mv.visitInsn(DUP);
                                    push(mv, i);
                                    push(mv, params[i]);
                                    mv.visitInsn(AASTORE);
                                }
                                push(mv, params.length);
                                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                                int a = 1, i = 0;
                                for (Type param; i < params.length; ++i) {
                                    mv.visitInsn(DUP);
                                    push(mv, i);
                                    mv.visitVarInsn((param = params[i]).getOpcode(ILOAD), a);
                                    a += size(param);
                                    if (param.getSort() < ARRAY_SORT) {
                                        cast(mv, param, OBJECT_TYPE);
                                    }
                                    mv.visitInsn(AASTORE);
                                }
                                mv.visitMethodInsn(INVOKESPECIAL, EMU_PATH, DEBUG_METHOD, "(ZLjava/lang/Class;[Ljava/lang/Class;[Ljava/lang/Object;)V", false);
                                mv.visitLabel(orElse);
                            }

                            // Handle method translation
                            if (translated) {
                                // method -> translation
                                int fromIndex = method.fromIndex;
                                final int length = Math.min(Math.min(translation.params.length, params.length), fromIndex + method.length);
                                int size = 1 + size(params, 0, Math.min(fromIndex, params.length));

                                int toIndex = 0;
                                while (toIndex < method.toIndex) {
                                    cast(mv, VOID_TYPE, translation.params[toIndex++]);
                                }
                                for (Type from; fromIndex < length; ++fromIndex, ++toIndex) {
                                    mv.visitVarInsn((from = params[fromIndex]).getOpcode(ILOAD), size);
                                    size += size(from);
                                    if (translation.checkcast(toIndex, from)) {
                                        cast(mv, from, translation.params[toIndex]);
                                    }
                                }
                                while (toIndex < translation.params.length) {
                                    cast(mv, VOID_TYPE, translation.params[toIndex++]);
                                }

                                // translation -> method
                                mv.visitMethodInsn(INVOKESPECIAL, EMU_PATH, translation.name, translation.desc, false);
                                if (translation.checkcast(returns)) {
                                    cast(mv, translation.returns, returns);
                                }

                                status = (translation.debug)? "Translated:  " : "@Translated: ";
                            } else {
                                cast(mv, VOID_TYPE, returns);
                                status = "Defaulted:   ";
                            }

                            mv.visitInsn(returns.getOpcode(IRETURN));
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
}
