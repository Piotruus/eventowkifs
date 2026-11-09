package pl.firest.eventowki;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FirestEventowkiPlugin extends JavaPlugin {

    private EventItems items;
    private PouchManager pouches;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        items = new EventItems(this);
        pouches = new PouchManager(this);
        pouches.load();
        pouches.startTasks();

        getServer().getPluginManager().registerEvents(new PouchListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PanelGui(this), this);

        PluginCommand command = getCommand("firestareventowki");
        if (command != null) {
            AdminCommand admin = new AdminCommand(this);
            command.setExecutor(admin);
            command.setTabCompleter(admin);
        }

        new ArmorSetTask(this).runTaskTimer(this, 40L, 40L);
    }

    @Override
    public void onDisable() {
        if (pouches != null) {
            pouches.save();
        }
    }

    public EventItems items() {
        return items;
    }

    public PouchManager pouches() {
        return pouches;
    }

    /** Wiadomosc z configu z prefixem. Pary: "%klucz%", "wartosc". */
    public Component msg(String key, String... pairs) {
        String raw = getConfig().getString("messages." + key, "&cBrak wiadomosci: " + key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace(pairs[i], pairs[i + 1]);
        }
        return items.text(getConfig().getString("messages.prefix", "") + raw);
    }

    /** Efekty za pelny komplet zbroi. */
    public List<PotionEffect> setBonusEffects() {
        List<PotionEffect> out = new ArrayList<>();
        if (!getConfig().getBoolean("armor.set-bonus.enabled", true)) {
            return out;
        }
        for (String line : getConfig().getStringList("armor.set-bonus.effects")) {
            String[] parts = line.split(":");
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                getLogger().warning("Nieznany efekt w configu: " + line);
                continue;
            }
            int amplifier = 0;
            if (parts.length > 1) {
                try {
                    amplifier = Math.max(0, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    // zostaje 0
                }
            }
            out.add(new PotionEffect(type, 100, amplifier, true, false, true));
        }
        return out;
    }
}
