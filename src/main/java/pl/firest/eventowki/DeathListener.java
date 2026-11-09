package pl.firest.eventowki;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Totem Drugiej Szansy (zachowuje polowe ekwipunku) i zbieranie lupow do Plonacej Torby. */
public final class DeathListener implements Listener {

    private final FirestEventowkiPlugin plugin;
    private final NamespacedKey pendingKey;

    public DeathListener(FirestEventowkiPlugin plugin) {
        this.plugin = plugin;
        this.pendingKey = new NamespacedKey(plugin, "pending_items");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Player victim = event.getEntity();
        List<ItemStack> drops = event.getDrops();

        handleTotem(victim, drops);
        handlePouch(victim, drops);
    }

    // ------------------------------------------------------------ totem

    private void handleTotem(Player victim, List<ItemStack> drops) {
        EventItems items = plugin.items();

        boolean consumed = false;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack stack = drops.get(i);
            if (items.isItem(stack, "totem")) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    drops.remove(i);
                }
                consumed = true;
                break;
            }
        }
        if (!consumed) {
            return;
        }

        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack != null && !stack.getType().isAir()) {
                candidates.add(stack);
            }
        }
        Collections.shuffle(candidates);

        double loseRatio = Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("totem.lose-ratio", 0.5)));
        int lose = (int) Math.round(candidates.size() * loseRatio);
        List<ItemStack> kept = new ArrayList<>(candidates.subList(lose, candidates.size()));

        for (ItemStack stack : kept) {
            drops.remove(stack);
        }

        if (!kept.isEmpty()) {
            victim.getPersistentDataContainer().set(pendingKey, PersistentDataType.BYTE_ARRAY,
                    ItemStack.serializeItemsAsBytes(kept));
        }
        victim.sendMessage(plugin.msg("totem-used"));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> restore(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                restore(player);
            }
        }, 20L);
    }

    private void restore(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        byte[] bytes = container.get(pendingKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) {
            return;
        }
        container.remove(pendingKey);

        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : ItemStack.deserializeItemsFromBytes(bytes)) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            EquipmentSlot slot = armorSlot(stack);
            if (slot != null) {
                ItemStack current = inventory.getItem(slot);
                if (current == null || current.getType().isAir()) {
                    inventory.setItem(slot, stack);
                    continue;
                }
            }
            inventory.addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.sendMessage(plugin.msg("totem-restored"));
    }

    private static EquipmentSlot armorSlot(ItemStack stack) {
        String name = stack.getType().name();
        if (name.endsWith("_HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    // ------------------------------------------------------------ torba

    private void handlePouch(Player victim, List<ItemStack> drops) {
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || drops.isEmpty()) {
            return;
        }

        EventItems items = plugin.items();
        UUID pouchId = null;
        for (ItemStack stack : killer.getInventory().getContents()) {
            pouchId = items.pouchId(stack);
            if (pouchId != null) {
                break;
            }
        }
        if (pouchId == null) {
            return;
        }

        // torby nie wkladamy do torby
        int moved = plugin.pouches().store(pouchId, drops, stack -> items.isItem(stack, "sakwa"));
        if (moved > 0) {
            killer.sendMessage(plugin.msg("pouch-captured", "%n%", String.valueOf(moved)));
            plugin.pouches().refreshViewers();
        }
        boolean leftovers = drops.stream().anyMatch(s -> s != null && !s.getType().isAir() && !items.isItem(s, "sakwa"));
        if (leftovers) {
            killer.sendMessage(plugin.msg("pouch-full"));
        }
    }
}
