package pl.firest.eventowki;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/** Co 2 sekundy nadaje efekty graczom w pelnym komplecie Plonacej Zbroi. */
public final class ArmorSetTask extends BukkitRunnable {

    private final FirestEventowkiPlugin plugin;

    public ArmorSetTask(FirestEventowkiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        List<PotionEffect> effects = plugin.setBonusEffects();
        if (effects.isEmpty()) {
            return;
        }
        EventItems items = plugin.items();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inventory = player.getInventory();
            boolean full = items.isItem(inventory.getHelmet(), "helm")
                    && items.isItem(inventory.getChestplate(), "napiersnik")
                    && items.isItem(inventory.getLeggings(), "spodnie")
                    && items.isItem(inventory.getBoots(), "buty");
            if (full) {
                for (PotionEffect effect : effects) {
                    player.addPotionEffect(effect);
                }
            }
        }
    }
}
