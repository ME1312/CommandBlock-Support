package net.ME1312.CBS;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.PrimitiveIterator;
import java.util.UUID;

import static org.bukkit.ChatColor.*;

final class Command implements CommandExecutor {
    private final RuntimeException FAILURE;
    private final Class<?> CBS;
    private final boolean FLAT;
    private final EmulationManager plugin;
    Command(EmulationManager plugin, Class<?> extension, RuntimeException reference) {
        this.plugin = plugin;
        boolean flat;
        try { // noinspection ConstantConditions
            flat = Block.class.getMethod("getBlockData") != null;
        } catch (NoSuchMethodException | NoSuchMethodError e) {
            flat = false;
        }
        FLAT = flat;
        CBS = extension;
        FAILURE = reference;
    }

    private String prefix(ChatColor color, ChatColor accent) {
        return color + "CBS " + accent + BOLD + '\u00BB' + color + ' ';
    }

    @SuppressWarnings("NullableProblems")
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command context, String label, String[] args) {
        if (args.length == 0) {
            PluginDescriptionFile desc = plugin.plugin.getDescription();
            sender.sendMessage("");
            sender.sendMessage(prefix(GRAY, DARK_GRAY) + "You are using " + WHITE + "CommandBlock Support" + GRAY + " version " + WHITE + desc.getVersion());
            sender.sendMessage(DARK_GRAY + " ---" + BOLD + '\u00BB' + WHITE + " /" + label + " [-flags] [command] [args...]");
            sender.sendMessage("");
            sender.sendMessage(GRAY + ITALIC.toString() + UNDERLINE + desc.getWebsite() + "/wiki/Flags");
            sender.sendMessage("");
        } else {
            boolean sub = false, debug = false;
            double x = 0, y = 0, z = 0;
            float yaw = 0, pitch = 0;
            World world = null;
            String name = null;
            UUID uid = null;

            if (sender instanceof Player) {
                if (sender.getClass() == CBS) {
                    sender.sendMessage(prefix(RED, DARK_RED) + "This command cannot be nested");
                    throw FAILURE;
                } else if (!sender.isOp()) {
                    sender.sendMessage(prefix(RED, DARK_RED) + "This command may only be tested by server operators");
                    return true;
                }

                Location data = ((Player) sender).getLocation();
                world = data.getWorld();
                x = data.getX();
                y = data.getY();
                z = data.getZ();
                yaw = data.getYaw();
                pitch = data.getPitch();
            } else if (sender instanceof BlockCommandSender) {
                Block block = ((BlockCommandSender) sender).getBlock();
                world = block.getWorld();
                x = block.getX();
                y = block.getY();
                z = block.getZ();
                if (FLAT) {
                    BlockFace mod = ((Directional) block.getBlockData()).getFacing();
                    yaw = (float) (-Math.atan2(mod.getModX(), mod.getModZ()) / Math.PI * 180.0);
                    pitch = (float) (Math.asin(mod.getModY() / new Vector(mod.getModX(), mod.getModY(), mod.getModZ()).length()) * 180.0 / Math.PI);
                } else {
                    switch ((block.getData() & 7) % 6) {
                        case 0:
                            pitch = 90;
                            break;
                        case 1:
                            pitch = -90;
                            break;
                        case 2:
                            yaw = -180;
                            break;
                        case 4:
                            yaw = 90;
                            break;
                        case 5:
                            yaw = -90;
                            break;
                    }
                }
            }

            int i = 0;
            try {
                boolean starting = true;
                for (int c; i < args.length && args[i].startsWith("-"); ++i, starting = true) {
                    for (PrimitiveIterator.OfInt $i = args[i].codePoints().iterator(); $i.hasNext(); ) {
                        switch (c = $i.nextInt()) {
                            case 'd': {
                                debug = true;
                                break;
                            }
                            case 's': {
                                sub = true;
                                break;
                            }
                            case 'n': {
                                flag(args.length - i, 1, "-n <username>");
                                name = args[++i];
                                break;
                            }
                            case 'u': {
                                flag(args.length - i, 1, "-u <uuid>");
                                String id = args[++i];
                                try {
                                    uid = UUID.fromString(id);
                                    break;
                                } catch (IllegalArgumentException e) {
                                    throw new CommandException("Invalid UUID: " + DARK_RED + id);
                                }
                            }
                            case 'w': {
                                flag(args.length - i, 1, "-w <world>");
                                String wn = args[++i];
                                world = Bukkit.getWorld(wn);
                                if (world == null) {
                                    throw new CommandException("Unknown world: " + DARK_RED + wn);
                                }
                                break;
                            }
                            case 'x': {
                                flag(args.length - i, 1, "-x <position>");
                                x = relative(args[++i], x);
                                break;
                            }
                            case 'y': {
                                flag(args.length - i, 1, "-y <position>");
                                y = relative(args[++i], y);
                                break;
                            }
                            case 'z': {
                                flag(args.length - i, 1, "-z <position>");
                                z = relative(args[++i], z);
                                break;
                            }
                            case 'v': {
                                flag(args.length - i, 3, "-v <x> <y> <z>");
                                x = relative(args[++i], x);
                                y = relative(args[++i], y);
                                z = relative(args[++i], z);
                                break;
                            }
                            case 'c': {
                                flag(args.length - i, 2, "-c <yaw> <pitch>");
                                String n = null;
                                try {
                                    yaw = Float.parseFloat(n = args[++i]);
                                    pitch = Float.parseFloat(n = args[++i]);
                                    break;
                                } catch (NumberFormatException e) {
                                    throw new CommandException("Invalid decimal number: " + DARK_RED + n);
                                }
                            }
                            case '-': if (starting) {
                                continue;
                            }
                            default: {
                                throw new CommandException(new StringBuilder("Unknown flag: ").append(DARK_RED).append('-').appendCodePoint(c).toString());
                            }
                        }
                        starting = false;
                    }
                }
            } catch (CommandException e) {
                if (sender instanceof BlockCommandSender) throw FAILURE;
                sender.sendMessage(prefix(RED, DARK_RED) + e.getMessage());
                return true;
            }

            if (world == null) {
                sender.sendMessage(prefix(RED, DARK_RED) + "The " + DARK_RED + "-w" + RED + " flag must be used when sending from console");
            } else {
                if (uid == null) uid = UUID.nameUUIDFromBytes(((name == null)? "cbs:" : "cbs:" + name).getBytes(StandardCharsets.UTF_8));
                EmulatedPlayer player = plugin.getPlayer(uid);
                if (name != null) player.name = name;
                player.pos = new Location(world, x, y, z, yaw, pitch);
                player.debug = debug;

                try {
                    boolean execute = i < args.length;
                    if (execute || sub) player.subs.add(sender);
                    if (execute) {

                        if (!Bukkit.getServer().dispatchCommand(player.getPlayer(), join(args, i)) && sender instanceof BlockCommandSender) {
                            throw FAILURE;
                        }
                    } else {
                        sender.sendMessage(prefix(YELLOW, GOLD) + "Instance flags updated " + GOLD + '\u25CF' + YELLOW + " No command to execute");
                    }
                } finally {
                    if (!sub) player.subs.remove(sender);
                }
            }
        }
        return true;
    }

    private static void flag(int length, int args, String usage) {
        if (length <= args) throw new CommandException("Missing argument" + ((length == args)?"":"s") + " for flag: " + DARK_RED + usage);
    }

    private static double relative(String input, double current) {
        try {
            if (input.startsWith("~")) {
                if (input.length() > 1) {
                    return current + Double.parseDouble(input.substring(1));
                } else {
                    return current;
                }
            } else {
                return Double.parseDouble(input);
            }
        } catch (NumberFormatException e) {
            throw new CommandException("Invalid decimal number: " + DARK_RED + input);
        }
    }

    private static String join(String[] args, int i) {
        String command = args[i++];
        if (i < args.length) {
            StringBuilder builder = new StringBuilder(command);
            do {
                builder.append(' ');
                builder.append(args[i]);
            } while (++i < args.length);
            command = builder.toString();
        }
        return command;
    }
}
