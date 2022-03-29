package net.ME1312.CBS;

import net.ME1312.CBS.ASM.ASM;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.UUID;

public final class EmulationManager extends JavaPlugin {
    private final HashMap<UUID, EmulatedPlayer> players = new HashMap<UUID, EmulatedPlayer>();
    private static final RuntimeException reference;
    private final MethodHandle generator;

    static {
        reference = new RuntimeException();
        reference.setStackTrace(new StackTraceElement[0]);
    }

    public EmulationManager() throws Throwable {
        // noinspection JavaLangInvokeHandleSignature
        generator = (MethodHandle) MethodHandles.publicLookup().findStatic(
                ASM.get(getDataFolder(),
                        "org.objectweb.asm.",
                        "net.ME1312.CBS.ASM.PlayerVisitor",
                        "net.ME1312.CBS.ASM.TranslationVisitor"
                ).loadClass("net.ME1312.CBS.ASM.PlayerVisitor"),
                "extendAndLoad", MethodType.methodType(MethodHandle.class, new Class[]{ JavaPlugin.class, Class.class })
        ).invokeExact((JavaPlugin) this, Player.class);
    }

    @Override
    public void onEnable() {
        try {
            new Command(this, reference);
            ReferenceFilter.register(reference);
            new BStats(this, 14759).addCustomChart(new BStats.SingleLineChart("emulators", players::size));
        } catch (Throwable e) {
            e.printStackTrace();
            setEnabled(false);
        }
    }

    EmulatedPlayer getPlayer(UUID uid) {
        return players.computeIfAbsent(uid, key -> {
            try {
                return (EmulatedPlayer) generator.invokeExact(uid);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static RuntimeException getQuietException() {
        return reference;
    }
}
