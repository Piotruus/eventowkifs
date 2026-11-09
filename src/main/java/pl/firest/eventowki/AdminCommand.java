package pl.firest.eventowki;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /firestareventowki give|panel|lista|reload */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "firest.eventowki.admin";

    private final FirestEventowkiPlugin plugin;

    public AdminCommand(FirestEventowkiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.msg("usage"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "panel" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.msg("only-player"));
                } else {
                    PanelGui.open(plugin, player);
                }
            }
            case "lista" -> {
                List<String> all = new ArrayList<>(EventItems.IDS);
                all.add("zbroja");
                sender.sendMessage(plugin.msg("list-header", "%lista%", String.join(", ", all)));
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            default -> sender.sendMessage(plugin.msg("usage"));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("usage"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.msg("player-offline", "%gracz%", args[1]));
            return;
        }

        String id = args[2].toLowerCase(Locale.ROOT);
        if (id.equals("torba")) {
            id = "sakwa";
        }
        List<String> ids;
        if (id.equals("zbroja")) {
            ids = EventItems.ARMOR;
        } else if (EventItems.IDS.contains(id)) {
            ids = List.of(id);
        } else {
            sender.sendMessage(plugin.msg("unknown-item"));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.msg("usage"));
                return;
            }
        }

        for (String itemId : ids) {
            plugin.items().give(target, itemId, amount);
        }
        String shown = id.equals("zbroja") ? "&6&lᴘʟᴏɴᴀᴄᴀ ᴢʙʀᴏᴊᴀ" : plugin.items().displayName(id);
        sender.sendMessage(plugin.msg("given",
                "%gracz%", target.getName(),
                "%ilosc%", String.valueOf(amount),
                "%item%", shown));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("give", "panel", "lista", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> all = new ArrayList<>(EventItems.IDS);
            all.add("zbroja");
            return filter(all, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return List.of("1", "5", "10");
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
