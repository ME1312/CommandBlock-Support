package cbs.plugin;

import bridge.Bridge;
import cbs.asm.SuppressDebugging;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.permissions.Permission;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;

// Bridging within this class is handled directly by us, instead of as usual by the maven bridge plugin
@SuppressWarnings({"NullableProblems", "unused"})
public abstract class EmulatedPlayer /* implements Player */ {
    private static final String locale;
    final Set<CommandSender> subs;
  //private final Unsafe unsafe;
    protected boolean debug;
    private final UUID uid;
    private World world;
    private double x, y, z;
    private float yaw, pitch;
    private String display;
    String name;

    protected EmulatedPlayer(UUID uid) {
        this.name = (this.uid = uid).toString();
        this.subs = new CopyOnWriteArraySet<CommandSender>();
      //this.unsafe = new Unsafe();
    }

    protected final void $(boolean translated, Class<?> returns, Class<?>[] params, Object[] args) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        if (stack.length > 3) {
            String uid = this.uid.toString();
            StackTraceElement e = stack[1];
            StringBuilder msg = new StringBuilder("CBS > Requested ");
            if (!translated) msg.append("untranslated ");
            msg.append("method: player.")
                    .append(e.getMethodName())
                    .append('(')
                    .append(params.length)
                    .append(")\ninstance: ")
                    .append(uid);
            if (!name.equals(uid)) {
                msg.append(" (")
                        .append(name)
                        .append(')');
            }
            msg.append("\nparameters: {");
            if (params.length != 0) {
                msg.append(' ');
                int i = 0;
                do {
                    msg.append(params[i].getTypeName());
                    msg.append(' ');
                } while (++i < params.length);
            }
            msg.append("}\narguments: ");
            Unsafe.toString(msg, args);
            msg.append("\nreturns: ")
                    .append(returns.getTypeName())
                    .append("\ncallers:");
            int i = 2;
            do {
                e = stack[i];
                msg.append("\n    ");
                if ("cbs.plugin.Command".equals(e.getClassName())) {
                    msg.append("... ")
                            .append(stack.length - i)
                            .append(" more");
                    break;
                }
                msg.append(e);
            } while (++i < stack.length);
            msg.append('\n');
            Bukkit.getLogger().log((translated)? Level.INFO : Level.WARNING, msg.toString());
        }
    }

    @SuppressDebugging
    public String toString() {
        final String uid = this.uid.toString();
        return "EmulatedPlayer(" + uid + ((name.equals(uid))? "" : ", " + name) + ')';
    }

    @SuppressDebugging
    public String getLocale() {
        return locale;
    } static {
        Locale def = Locale.getDefault();
        locale = def.getLanguage() + '_' + def.getCountry().toLowerCase(Locale.ROOT);
    }

    @SuppressDebugging
    public void sendMessage(String msg) {
        for (CommandSender s : subs) s.sendMessage(msg);
    }

    @SuppressDebugging
    public void sendMessage(String... msgs) {
        for (CommandSender s : subs) s.sendMessage(msgs);
    }

    @SuppressDebugging
    public void sendMessage(UUID sender, String msg) {
        for (CommandSender s : subs) s.sendMessage(sender, msg);
    }

    @SuppressDebugging
    public void sendMessage(UUID sender, String... msgs) {
        for (CommandSender s : subs) s.sendMessage(sender, msgs);
    }

    @SuppressDebugging
    public Spigot spigot() {
        return new Spigot();
    }

    private final class Spigot extends Player.Spigot {
        private Spigot() {}

        @Override
        public void sendMessage(BaseComponent msg) {
            for (CommandSender s : subs) s.spigot().sendMessage(msg);
        }

        @Override
        public void sendMessage(BaseComponent... msgs) {
            for (CommandSender s : subs) s.spigot().sendMessage(msgs);
        }

        @Override
        public void sendMessage(UUID sender, BaseComponent msg) {
            for (CommandSender s : subs) s.spigot().sendMessage(sender, msg);
        }

        @Override
        public void sendMessage(UUID sender, BaseComponent... msgs) {
            for (CommandSender s : subs) s.spigot().sendMessage(sender, msgs);
        }

        @Override
        public void sendMessage(ChatMessageType ctx, BaseComponent msg) {
            if (ctx == net.md_5.bungee.api.ChatMessageType.CHAT) sendMessage(msg);
        }

        @Override
        public void sendMessage(ChatMessageType ctx, BaseComponent... msgs) {
            if (ctx == net.md_5.bungee.api.ChatMessageType.CHAT) sendMessage(msgs);
        }

        @Override
        public void sendMessage(ChatMessageType ctx, UUID sender, BaseComponent msg) {
            if (ctx == net.md_5.bungee.api.ChatMessageType.CHAT) sendMessage(sender, msg);
        }

        @Override
        public void sendMessage(ChatMessageType ctx, UUID sender, BaseComponent... msgs) {
            if (ctx == net.md_5.bungee.api.ChatMessageType.CHAT) sendMessage(sender, msgs);
        }
    }

    public abstract Server getServer();

    public abstract Player getPlayer();

    public UUID getUniqueId() {
        return uid;
    }

    @Bridge(name = "getPlayerListName")
    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return (display == null)? name : display;
    }

    public void setDisplayName(String name) {
        display = name;
    }

    public GameMode getGameMode() {
        return GameMode.CREATIVE;
    }

    public World getWorld() {
        return world;
    }

    @Bridge(name = "getEyeLocation")
    public Location getLocation() {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public Location getLocation(Location reference) {
        if (reference != null) {
            reference.setWorld(world);
            reference.setX(x);
            reference.setY(y);
            reference.setZ(z);
            reference.setYaw(yaw);
            reference.setPitch(pitch);
        }
        return reference;
    }

    final void setLocation(World world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    private void setLocation(Location location) {
        setLocation(location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Bridge(params = { Location.class, TeleportCause.class })
    public boolean teleport(Location location) {
        setLocation(location);
        return true;
    }

    @Bridge(params = { Entity.class, TeleportCause.class })
    public boolean teleport(Entity entity) {
        setLocation(entity.getLocation());
        return true;
    }

    @Bridge(name = "isValid")
    @Bridge(name = "isPersistent")
    @Bridge(name = "hasPlayedBefore")
    @Bridge(name = "isWhitelisted")
    @Bridge(name = "isOnline")
    @Bridge(name = "isInvisible")
    @Bridge(name = "isInvulnerable")
    @Bridge(name = "hasPermission", params = String.class)
    @Bridge(name = "hasPermission", params = Permission.class)
    @Bridge(name = "isPermissionSet", params = String.class)
    @Bridge(name = "isPermissionSet", params = Permission.class)
    @Bridge(name = "isOp")
    protected final boolean Z() {
        return true;
    }
}
