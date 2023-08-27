package cbs.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static cbs.plugin.EmulationManager.*;
import static org.bukkit.ChatColor.*;

final class Command extends org.bukkit.command.Command {
    private static final Map<Integer, Flag> flags;
    private static final boolean location;
    private static final boolean flat;
    private final EmulationManager plugin;
    Command(EmulationManager plugin) {
        super("cbs", "CommandBlock Support", "/cbs [-flags] [command] [args...]", Collections.emptyList());
        this.plugin = plugin;
    }

    private String prefix(ChatColor color, ChatColor accent) {
        return color + "CBS " + accent + BOLD + '\u00BB' + color + ' ';
    }

    @SuppressWarnings("NullableProblems")
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            PluginDescriptionFile desc = plugin.getDescription();
            sender.sendMessage("");
            sender.sendMessage(prefix(GRAY, DARK_GRAY) + "You are using " + WHITE + "CommandBlock Support" + GRAY + " version " + WHITE + desc.getVersion());
            sender.sendMessage(DARK_GRAY + " ---" + BOLD + '\u00BB' + WHITE + " /" + label + " [-flags] [command] [args...]");
            sender.sendMessage("");
            sender.sendMessage(GRAY + ITALIC.toString() + UNDERLINE + desc.getWebsite() + "/wiki/Flags");
            sender.sendMessage("");
        } else if (args[0].matches("-m+(?:-.*)?")) {
            int i = 0; // Minimal mode (-m) has the sender run the command as themselves. No further permission checks required.
            while (args[i++].indexOf('-', 1) == -1 && args.length > i && args[i].matches("-m*(?:-.*)?"));
            if (args.length != i) {
                if (!run(sender, sender, args, i)) {
                    if (sender instanceof BlockCommandSender) {
                        throw reference;
                    } else {
                        return !(sender instanceof EmulatedPlayer);
                    }
                }
            } else {
                sender.sendMessage(prefix(RED, DARK_RED) + "No command to execute");
            }
        } else {
            boolean sub = false, debug = false;
            double x = 0, y = 0, z = 0;
            float yaw = 0, pitch = 0;
            World world = null;
            String name = null;
            UUID uid = null;

            if (sender instanceof Player) {
                if (sender instanceof EmulatedPlayer) {
                    sender.sendMessage(prefix(RED, DARK_RED) + "This command cannot be nested");
                    return false;
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
                if (flat) {
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
                all: for (int c; i < args.length && args[i].startsWith("-"); ++i) {
                    PrimitiveIterator.OfInt $i = args[i].codePoints().iterator();
                    $i.nextInt();
                    while ($i.hasNext()) {
                        switch (c = $i.nextInt()) {
                            case 'd': {
                                debug = true;
                                continue;
                            }
                            case 's': {
                                sub = true;
                                continue;
                            }
                            case 'n': {
                                flag(args.length - i, 1, "-n <username>");
                                name = args[++i];
                                continue;
                            }
                            case 'u': {
                                flag(args.length - i, 1, "-u <uuid>");
                                String id = args[++i];
                                try {
                                    uid = UUID.fromString(id);
                                    continue;
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
                                continue;
                            }
                            case 'x': {
                                flag(args.length - i, 1, "-x <position>");
                                x = relative(args[++i], x);
                                continue;
                            }
                            case 'y': {
                                flag(args.length - i, 1, "-y <position>");
                                y = relative(args[++i], y);
                                continue;
                            }
                            case 'z': {
                                flag(args.length - i, 1, "-z <position>");
                                z = relative(args[++i], z);
                                continue;
                            }
                            case 'v': {
                                flag(args.length - i, 3, "-v <x> <y> <z>");
                                x = relative(args[++i], x);
                                y = relative(args[++i], y);
                                z = relative(args[++i], z);
                                continue;
                            }
                            case 'c': {
                                flag(args.length - i, 2, "-c <yaw> <pitch>");
                                String n = null;
                                try {
                                    yaw = Float.parseFloat(n = args[++i]);
                                    pitch = Float.parseFloat(n = args[++i]);
                                    continue;
                                } catch (NumberFormatException e) {
                                    throw new CommandException("Invalid decimal number: " + DARK_RED + n);
                                }
                            }
                            case 'm': {
                                throw new CommandException("The " + DARK_RED + "-m" + RED + " flag cannot be combined with any other flags");
                            }
                            case '-': {
                                ++i;
                                break all;
                            }
                            default: {
                                throw new CommandException(new StringBuilder("Unknown flag: ").append(DARK_RED).append('-').appendCodePoint(c).toString());
                            }
                        }
                    }
                }
            } catch (CommandException e) {
                if (sender instanceof BlockCommandSender) throw reference;
                sender.sendMessage(prefix(RED, DARK_RED) + e.getMessage());
                return true;
            }

            if (world == null) {
                sender.sendMessage(prefix(RED, DARK_RED) + "The " + DARK_RED + "-w" + RED + " flag must be used when sending from console");
            } else {
                if (uid == null) uid = UUID.nameUUIDFromBytes(((name == null)? "cbs:" : "cbs:" + name).getBytes(StandardCharsets.UTF_8));
                EmulatedPlayer player = plugin.getPlayer(uid);
                if (name != null) player.name = name;
                player.setLocation(world, x, y, z, yaw, pitch);
                player.debug = debug;

                try {
                    boolean execute = i < args.length;
                    if (execute || sub) player.subs.add(sender);
                    if (execute) {
                        if (!run(sender, player.getPlayer(), args, i) && sender instanceof BlockCommandSender) {
                            throw reference;
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

    private boolean run(CommandSender sender, CommandSender target, String[] args, int i) {
        final org.bukkit.command.Command command;
        final String label = args[i];
        try {
            command = ((CommandMap) commands.invokeExact(Bukkit.getServer())).getCommand(label);
        } catch (Throwable e) {
            throw Unsafe.rethrow(e);
        }

        if (command == null) {
            sender.sendMessage(prefix(RED, DARK_RED) + "Unknown command: " + DARK_RED + '/' + label);
            return false;
        } else {
            return command.execute(target, label, Arrays.copyOfRange(args, ++i, args.length));
        }
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

    static {
        Map<Integer, Flag> map = new LinkedHashMap<Integer, Flag>();
        map.compute((int) 'd', (c, v) -> new Flag(Collections.emptyList(), c));
        map.compute((int) 's', (c, v) -> new Flag(Collections.emptyList(), c));
        map.compute((int) 'n', (c, v) -> new Flag(Collections.singletonList("<username>"), c));
        map.compute((int) 'u', (c, v) -> new Flag(Collections.singletonList("<uuid>"), c));
        map.compute((int) 'w', (c, v) -> new Flag(Collections.singletonList("<world>"), c));
        map.compute((int) 'x', (c, v) -> new Flag(Collections.singletonList("<x>"), c, 'v'));
        map.compute((int) 'y', (c, v) -> new Flag(Collections.singletonList("<y>"), c, 'v'));
        map.compute((int) 'z', (c, v) -> new Flag(Collections.singletonList("<z>"), c, 'v'));
        map.compute((int) 'v', (c, v) -> new Flag(Arrays.asList("<x>", "<y>", "<z>"), c, 'x', 'y', 'z'));
        map.compute((int) 'c', (c, v) -> new Flag(Arrays.asList("<yaw>", "<pitch>"), c));
        flags = Collections.unmodifiableMap(map);

        boolean value;
        try { // noinspection ConstantConditions
            value = Block.class.getMethod("getBlockData") != null;
        } catch (NoSuchMethodException | NoSuchMethodError e) {
            value = false;
        }
        flat = value;

        try { // noinspection ConstantConditions
            value = org.bukkit.command.Command.class.getMethod("tabComplete", CommandSender.class, String.class, String[].class, Location.class) != null;
        } catch (NoSuchMethodException e) {
            value = false;
        }
        location = value;
    }

    private static final class Flag {
        private final List<String> arguments;
        private final List<Integer> overrides;
        private Flag(List<String> arguments, int... overrides) {
            this.arguments = arguments;
            this.overrides = new ArrayList<>(overrides.length);
            for (int i : overrides) this.overrides.add(i);
        }
    }

    public List<String> tabComplete(CommandSender sender, String label, String[] args) throws IllegalArgumentException {
        return tabComplete(sender, label, args, null);
    }

    @SuppressWarnings("unchecked")
    public List<String> tabComplete(CommandSender sender, String label, String[] args, Location location) throws IllegalArgumentException {
        if (sender.isOp()) {
            final LinkedList<Integer> available = new LinkedList<Integer>(flags.keySet());
            final LinkedList<String> values = new LinkedList<String>();
            final String LAST = (args.length > 0)?args[args.length - 1]:"";
            available.addFirst((int) '-');
            available.add((int) 'm');

            int i = 0;
            String arg = null;
            boolean parsing = true;
            if (args.length != 0) {
                Flag flag;
                int x;
                while (args[i].startsWith("-")) {
                    PrimitiveIterator.OfInt $i = args[i].codePoints().iterator();
                    $i.nextInt();
                    if ($i.hasNext()) {
                        available.remove((Object) (int) 'm');
                        do {
                            if ((flag = flags.get(x = $i.nextInt())) != null) {
                                values.addAll(flag.arguments);
                                available.removeAll(flag.overrides);
                            } else if (x == 'm') {
                                available.clear();
                                available.add((int) '-');
                            } else if (x == '-') {
                                available.clear();
                                parsing = false;
                                break;
                            } else {
                                return Collections.emptyList();
                            }
                        } while ($i.hasNext());
                    }

                    // definition of variable x changes here!
                    x = ++i + values.size();
                    if (x >= args.length) {
                        if (i < args.length && values.size() != 0) {
                            arg = values.get(args.length - 1 - i);
                        }
                        values.clear();
                        break;
                    }
                    i = x;
                    values.clear();
                    if (!parsing) break;
                }
            }

            if (arg != null) {
                values.add(arg);

            } else if (0 == args.length || i == args.length - 1) {
                final String last = (Command.location)? LAST.toLowerCase(Locale.ENGLISH) : LAST.toLowerCase();
                final Map<String, ?> map;
                try {
                    map = (Map<String, ?>) mappings.invokeExact((CommandMap) commands.invoke(Bukkit.getServer()));
                } catch (Throwable e) {
                    throw Unsafe.rethrow(e);
                }

                for (String command : map.keySet()) if (command.startsWith(last)) {
                    values.add(LAST + command.substring(LAST.length()));
                }
                Collections.sort(values);
                if (parsing && LAST.length() == 0 && !values.contains("-")) {
                    values.addFirst("-");
                }
            } else if (i < args.length) {
                final org.bukkit.command.Command command;
                try {
                    command = ((CommandMap) commands.invoke(Bukkit.getServer())).getCommand(args[i]);
                } catch (Throwable e) {
                    throw Unsafe.rethrow(e);
                }

                if (command != null) {
                    if (Command.location) {
                        return command.tabComplete(sender, args[i], Arrays.copyOfRange(args, ++i, args.length), location);
                    } else {
                        return command.tabComplete(sender, args[i], Arrays.copyOfRange(args, ++i, args.length));
                    }
                }
            } else {
                for (Integer c : available) {
                    values.add(new StringBuilder(LAST).appendCodePoint(c).toString());
                }
            }
            return values;
        } else {
            return Collections.emptyList();
        }
    }
}
