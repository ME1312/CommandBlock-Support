package net.ME1312.CBS;

import net.ME1312.CBS.ASM.SuppressDebugging;
import net.ME1312.CBS.ASM.Translation;
import net.ME1312.CBS.ASM.Translation.For;

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
                    msg.append(params[i].getCanonicalName());
                    msg.append(' ');
                } while (++i < params.length);
            }
            msg.append("}\narguments: {");
            if (args.length != 0) {
                msg.append(' ');
                int i = 0;
                do {
                    Object arg = args[i];
                    if (arg == null) {
                        msg.append("null");
                    } else {
                        msg.append(arg.getClass().getCanonicalName())
                                .append('@').append(Integer.toHexString(arg.hashCode()));
                    }
                    msg.append(' ');
                } while (++i < args.length);
            }
            msg.append("}\nreturns: ")
                    .append(returns.getCanonicalName())
                    .append("\ncallers:");
            int i = 2;
            do {
                e = stack[i];
                msg.append("\n    ");
                if ("net.ME1312.CBS.Command".equals(e.getClassName())) {
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

    @Translation(name = "getPlayerListName")
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

    @Translation(name = "getEyeLocation")
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

    @Translation(params = { Location.class, TeleportCause.class })
    public boolean teleport(Location location) {
        setLocation(location);
        return true;
    }

    @Translation(params = { Entity.class, TeleportCause.class })
    public boolean teleport(Entity entity) {
        setLocation(entity.getLocation());
        return true;
    }

    @Translation({
            @For(name = "isValid"),
            @For(name = "isPersistent"),
            @For(name = "hasPlayedBefore"),
            @For(name = "isWhitelisted"),
            @For(name = "isOnline"),
            @For(name = "isInvisible"),
            @For(name = "isInvulnerable"),
            @For(name = "hasPermission", params = String.class),
            @For(name = "hasPermission", params = Permission.class),
            @For(name = "isPermissionSet", params = String.class),
            @For(name = "isPermissionSet", params = Permission.class),
            @For(name = "isOp")
    })
    protected final boolean Z() {
        return true;
    }
}
