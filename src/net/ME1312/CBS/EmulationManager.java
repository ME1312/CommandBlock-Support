package net.ME1312.CBS;

import net.ME1312.CBS.ASM.MemoryClassLoader;
import net.ME1312.CBS.ASM.PlayerVisitor;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.UUID;

public final class EmulationManager {
    private final HashMap<UUID, EmulatedPlayer> players = new HashMap<UUID, EmulatedPlayer>();
    private final MethodHandle generator;
    final JavaPlugin plugin;

    public EmulationManager(JavaPlugin plugin, RuntimeException reference) throws Throwable {
        this.plugin = plugin;

        // Generate extended class
        byte[] data = new PlayerVisitor().translate(Player.class).flip();
        if (PlayerVisitor.DEBUG && (plugin.getDataFolder().isDirectory() || plugin.getDataFolder().mkdirs())) {
            FileOutputStream fos = new FileOutputStream(new File(plugin.getDataFolder(), "EmulatedExtension.class"), false);
            fos.write(data);
            fos.close();
        }
        Class<?> extension = new MemoryClassLoader(EmulationManager.class.getClassLoader(), PlayerVisitor.CLASS_NAME, data).loadClass(PlayerVisitor.CLASS_NAME);
        generator = MethodHandles.publicLookup()
                .findConstructor(extension, MethodType.methodType(void.class, EmulatedPlayer.class))
                .asType(MethodType.methodType(void.class, EmulatedPlayer.class));

        plugin.getCommand("cbs").setExecutor(new Command(this, extension, reference));
        new BStats(plugin, 14759).addCustomChart(new BStats.SingleLineChart("emulators", players::size));
    }

    EmulatedPlayer getPlayer(UUID uid) {
        return players.computeIfAbsent(uid, key -> {
            final EmulatedPlayer player = new EmulatedPlayer(key);
            try {
                generator.invokeExact(player);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return player;
        });
    }
}
