package net.ME1312.CBS;

import net.ME1312.CBS.ASM.ASM;

import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class CBS extends JavaPlugin {
    private static final RuntimeException reference;
    private Object plugin;

    static {
        reference = new RuntimeException();
        reference.setStackTrace(new StackTraceElement[0]);
    }

    // This is the only method actually exposed for use by other plugins
    public static RuntimeException getQuietException() {
        return reference;
    }

    @Override
    public void onEnable() {
        try {
            // Reload this plugin with ASM support
            Class<?>[] params = new Class[]{ JavaPlugin.class, RuntimeException.class };
            plugin = MethodHandles.publicLookup()
                    .findConstructor(ASM.get(getDataFolder()).loadClass("net.ME1312.CBS.EmulationManager"), MethodType.methodType(void.class, params))
                    .asType(MethodType.methodType(Object.class, params)).invokeExact((JavaPlugin) this, reference);

            ReferenceFilter.register(reference);
        } catch (Throwable e) {
            e.printStackTrace();
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (plugin == null) return;
        try {
            MethodHandle destroy = MethodHandles.publicLookup().findVirtual(plugin.getClass(), "destroy", MethodType.methodType(void.class)).bindTo(plugin);
            try {
                destroy.invokeExact();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } catch (NoSuchMethodException e) {
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
