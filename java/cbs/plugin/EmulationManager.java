package cbs.plugin;

import cbs.asm.ASM;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EmulationManager extends JavaPlugin {
    static final RuntimeException reference;
    static final MethodHandle commands, mappings;
    private final Map<UUID, EmulatedPlayer> players;
    private final MethodHandle generator;

    static {
        try {
            Server server = Bukkit.getServer();
            Class<?> clazz = server.getClass();
            Lookup lookup = MethodHandles.publicLookup();
            Field field = clazz.getDeclaredField("commandMap"); field.setAccessible(true);
            commands = lookup.unreflectGetter(field).asType(MethodType.methodType(CommandMap.class, Server.class));

            clazz = field.getType();
            MethodHandle handle;
            for (;;) try {
                field = clazz.getDeclaredField("knownCommands"); field.setAccessible(true);
                handle = lookup.unreflectGetter(field).asType(MethodType.methodType(Map.class, CommandMap.class));
                break;
            } catch (NoSuchFieldException e) {
                if ((clazz = clazz.getSuperclass()) == null) throw e;
            }
            mappings = handle;
            reference = new RuntimeException();
            reference.setStackTrace(new StackTraceElement[0]);
        } catch (Throwable e) {
            throw Unsafe.rethrow(e);
        }
    }

    public EmulationManager() throws Throwable {
        players = new HashMap<>();
        generator = (MethodHandle) MethodHandles.publicLookup().findStatic(
                ASM.get(getDataFolder(),
                        "org.objectweb.asm.",
                        "bridge.asm.",
                        "cbs.asm.PlayerVisitor",
                        "cbs.asm.TranslationVisitor"
                ).loadClass("cbs.asm.PlayerVisitor"),
                "generateExtension", MethodType.methodType(MethodHandle.class, Plugin.class)
        ).invokeExact((Plugin) this);
    }

    @Override
    public void onEnable() {
        registerCommand("cbs", new Command(this));
        ReferenceFilter.register();
        new BStats(this, 14759).addCustomChart(new BStats.SingleLineChart("emulators", players::size));
    }

    public static void registerCommand(Plugin plugin, org.bukkit.command.Command... commands) {
        registerCommand(plugin.getDescription().getName(), commands);
    }

    public static void registerCommand(String prefix, org.bukkit.command.Command... commands) {
        try {
            CommandMap mappings = (CommandMap) EmulationManager.commands.invokeExact(Bukkit.getServer());
            for (org.bukkit.command.Command command : commands) {
                mappings.register(prefix, command);
            }
        } catch (Throwable e) {
            throw Unsafe.rethrow(e);
        }
    }

    public static RuntimeException getQuietException() {
        return reference;
    }

    EmulatedPlayer getPlayer(UUID uid) {
        return players.computeIfAbsent(uid, key -> {
            try {
                return (EmulatedPlayer) generator.invokeExact(uid);
            } catch (Throwable e) {
                throw Unsafe.rethrow(e);
            }
        });
    }

    @Override
    public void onDisable() {
        players.clear();
    }
}
