package io.github.zzz1999.entityreskin.server.command;

import io.github.zzz1999.entityreskin.protocol.Identifiers;
import io.github.zzz1999.entityreskin.server.EntityReskinPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The /entityreskin command. Target syntax: an entity UUID, {@code nearest[:radius]} (the closest
 * non-player entity to the executing player), or an {@code @} selector — selectors are resolved
 * through {@code Bukkit.selectEntities} via reflection, kept defensive across server forks and
 * versions in case a target lacks the selector API.
 */
public final class EntityReskinCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "[EntityReskin] ";
    private static final double DEFAULT_NEAREST_RADIUS = 10.0;
    private static final double MAX_NEAREST_RADIUS = 64.0;
    private static final List<String> SUBCOMMANDS =
            Arrays.asList("set", "clear", "info", "reload", "status");

    private final EntityReskinPlugin plugin;

    public EntityReskinCommand(EntityReskinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(subcommand)) {
            plugin.reloadPlugin();
            if (plugin.poller() != null) {
                plugin.poller().pollNow();
            }
            sender.sendMessage(PREFIX + "Configuration reloaded.");
            return true;
        }
        if ("status".equals(subcommand)) {
            sendStatus(sender);
            return true;
        }
        if ("set".equals(subcommand)) {
            if (args.length < 3) {
                sender.sendMessage(PREFIX + "Usage: /" + label + " set <target> <appearance>");
                return true;
            }
            String identifier = args[2];
            if (!Identifiers.isValid(identifier)) {
                sender.sendMessage(PREFIX + "Invalid appearance identifier; expected namespace:name (lowercase letters, digits, '_', '.', '-', '/').");
                return true;
            }
            List<Entity> targets = resolveTargets(sender, args[1]);
            for (Entity target : targets) {
                plugin.registry().set(target.getUniqueId(), identifier);
                plugin.gateway().broadcastSet(target.getUniqueId(), identifier);
            }
            if (!targets.isEmpty()) {
                plugin.saveRegistryAsync();
                sender.sendMessage(PREFIX + "Applied to " + targets.size() + " entities, appearance " + identifier + ".");
            }
            return true;
        }
        if ("clear".equals(subcommand)) {
            if (args.length < 2) {
                sender.sendMessage(PREFIX + "Usage: /" + label + " clear <target>");
                return true;
            }
            List<Entity> targets = resolveTargets(sender, args[1]);
            int cleared = 0;
            for (Entity target : targets) {
                if (plugin.registry().clear(target.getUniqueId()) != null) {
                    plugin.gateway().broadcastClear(target.getUniqueId());
                    cleared++;
                }
            }
            if (cleared > 0) {
                plugin.saveRegistryAsync();
            }
            if (!targets.isEmpty()) {
                sender.sendMessage(PREFIX + "Cleared " + cleared + " entities' appearances.");
            }
            return true;
        }
        if ("info".equals(subcommand)) {
            if (args.length < 2) {
                sender.sendMessage(PREFIX + "Usage: /" + label + " info <target>");
                return true;
            }
            for (Entity target : resolveTargets(sender, args[1])) {
                String identifier = plugin.registry().get(target.getUniqueId());
                sender.sendMessage(PREFIX + target.getType() + " " + target.getUniqueId() + " -> "
                        + (identifier != null ? identifier : "(no appearance)"));
            }
            return true;
        }
        sendUsage(sender);
        return true;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(PREFIX + "Channel: " + plugin.gateway().channelName()
                + ", handshaked clients: " + plugin.gateway().handshakedCount()
                + ", appearance entries: " + plugin.registry().size());
        if (plugin.poller() == null) {
            sender.sendMessage(PREFIX + "Resource backend: not configured (set the backend section in config.yml).");
        } else {
            String hash = plugin.poller().lastHashHex();
            sender.sendMessage(PREFIX + "Manifest hash: "
                    + (hash != null ? hash.substring(0, 12) + "..." : "(backend empty or not yet fetched)"));
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX + "Usage: /entityreskin <set|clear|info|reload|status>");
        sender.sendMessage(PREFIX + "Target may be an entity UUID, nearest[:radius], or an @ selector (1.13.2+).");
    }

    private List<Entity> resolveTargets(CommandSender sender, String token) {
        if (token.startsWith("@")) {
            return selectEntitiesReflectively(sender, token);
        }
        if ("nearest".equals(token) || token.startsWith("nearest:")) {
            return resolveNearest(sender, token);
        }
        try {
            UUID uuid = UUID.fromString(token);
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null) {
                sender.sendMessage(PREFIX + "No loaded entity found for that UUID.");
                return Collections.emptyList();
            }
            return Collections.singletonList(entity);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PREFIX + "Could not parse target: " + token);
            return Collections.emptyList();
        }
    }

    private List<Entity> resolveNearest(CommandSender sender, String token) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "The nearest target can only be used by a player.");
            return Collections.emptyList();
        }
        Player player = (Player) sender;
        double radius = DEFAULT_NEAREST_RADIUS;
        int separator = token.indexOf(':');
        if (separator >= 0) {
            try {
                radius = Math.min(MAX_NEAREST_RADIUS, Double.parseDouble(token.substring(separator + 1)));
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "Invalid radius: " + token.substring(separator + 1));
                return Collections.emptyList();
            }
        }
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity candidate : player.getNearbyEntities(radius, radius, radius)) {
            if (candidate instanceof Player) {
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        if (nearest == null) {
            sender.sendMessage(PREFIX + "No entities within " + radius + " blocks.");
            return Collections.emptyList();
        }
        return Collections.singletonList(nearest);
    }

    @SuppressWarnings("unchecked")
    private List<Entity> selectEntitiesReflectively(CommandSender sender, String selector) {
        try {
            Method selectEntities = Bukkit.class.getMethod("selectEntities",
                    CommandSender.class, String.class);
            return (List<Entity>) selectEntities.invoke(null, sender, selector);
        } catch (NoSuchMethodException e) {
            sender.sendMessage(PREFIX + "This server version does not support @ selectors (requires 1.13.2+); use nearest or an entity UUID.");
            return Collections.emptyList();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            sender.sendMessage(PREFIX + "Invalid selector: " + cause.getMessage());
            return Collections.emptyList();
        } catch (IllegalAccessException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<String>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.startsWith(prefix)) {
                    matches.add(subcommand);
                }
            }
            return matches;
        }
        if (args.length == 2 && ("set".equalsIgnoreCase(args[0])
                || "clear".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0]))) {
            return Collections.singletonList("nearest");
        }
        return Collections.emptyList();
    }
}
