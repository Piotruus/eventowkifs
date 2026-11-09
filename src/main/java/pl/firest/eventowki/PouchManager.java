package pl.firest.eventowki;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Dane i GUI Plonacej Torby. Kazda torba ma unikalne ID, a jej zawartosc to 45 slotow
 * (stale pozycje - nic sie nie przesuwa). Dolny rzad GUI to pasek z przyciskiem wyplaty.
 */
public final class PouchManager {

    public static final int SIZE = 45;
    public static final int BUTTON_SLOT = 49;

    public record Entry(ItemStack item, long addedAt) {
    }

    public static final class Holder implements InventoryHolder {
        private final UUID id;
        private Inventory inventory;

        Holder(UUID id) {
            this.id = id;
        }

        public UUID id() {
            return id;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final FirestEventowkiPlugin plugin;
    private final Map<UUID, Entry[]> data = new HashMap<>();
    private final File file;
    private boolean dirty;

    public PouchManager(FirestEventowkiPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pouches.yml");
    }

    // ------------------------------------------------------------ dane

    public Entry[] slots(UUID id) {
        return data.computeIfAbsent(id, k -> new Entry[SIZE]);
    }

    public void markDirty() {
        dirty = true;
    }

    private long lifetimeMillis() {
        return Math.max(1, plugin.getConfig().getInt("pouch.lifetime-minutes", 15)) * 60_000L;
    }

    /**
     * Przenosi przedmioty z listy do wolnych slotow torby (usuwa je z listy).
     *
     * @return ile stackow trafilo do torby
     */
    public int store(UUID id, List<ItemStack> drops, Predicate<ItemStack> skip) {
        Entry[] slots = slots(id);
        long now = System.currentTimeMillis();
        int moved = 0;
        Iterator<ItemStack> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (stack == null || stack.getType().isAir() || skip.test(stack)) {
                continue;
            }
            int free = -1;
            for (int i = 0; i < SIZE; i++) {
                if (slots[i] == null) {
                    free = i;
                    break;
                }
            }
            if (free == -1) {
                break;
            }
            slots[free] = new Entry(stack.clone(), now);
            it.remove();
            moved++;
        }
        if (moved > 0) {
            dirty = true;
        }
        return moved;
    }

    /** Usuwa przeterminowane przedmioty. */
    private void purge() {
        long now = System.currentTimeMillis();
        long life = lifetimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<UUID, Entry[]>> it = data.entrySet().iterator();
        while (it.hasNext()) {
            Entry[] slots = it.next().getValue();
            boolean empty = true;
            for (int i = 0; i < SIZE; i++) {
                if (slots[i] != null && now - slots[i].addedAt() >= life) {
                    slots[i] = null;
                    changed = true;
                }
                if (slots[i] != null) {
                    empty = false;
                }
            }
            if (empty) {
                it.remove();
            }
        }
        if (changed) {
            dirty = true;
        }
    }

    // ------------------------------------------------------------ wyplata

    /** Przenosi jeden slot do ekwipunku gracza. @return 1 jesli przeniesiono calosc. */
    public int withdraw(Player player, UUID id, int slot) {
        Entry[] slots = slots(id);
        Entry entry = slots[slot];
        if (entry == null) {
            return 0;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(entry.item().clone());
        dirty = true;
        if (left.isEmpty()) {
            slots[slot] = null;
            return 1;
        }
        slots[slot] = new Entry(left.values().iterator().next(), entry.addedAt());
        return 0;
    }

    /** @return {przeniesione stacki, pozostale stacki} */
    public int[] withdrawAll(Player player, UUID id) {
        int moved = 0;
        for (int i = 0; i < SIZE; i++) {
            moved += withdraw(player, id, i);
        }
        int remaining = 0;
        for (Entry entry : slots(id)) {
            if (entry != null) {
                remaining++;
            }
        }
        return new int[]{moved, remaining};
    }

    // ------------------------------------------------------------ GUI

    public void open(Player player, UUID id) {
        Holder holder = new Holder(id);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                plugin.items().text(plugin.getConfig().getString("items.sakwa.name", "Torba")));
        holder.setInventory(inventory);
        render(inventory, id);
        player.openInventory(inventory);
    }

    public void render(Inventory inventory, UUID id) {
        Entry[] slots = slots(id);
        long now = System.currentTimeMillis();
        long life = lifetimeMillis();
        int count = 0;

        for (int i = 0; i < SIZE; i++) {
            Entry entry = slots[i];
            if (entry == null) {
                inventory.setItem(i, null);
            } else {
                count++;
                inventory.setItem(i, display(entry, now, life));
            }
        }

        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = SIZE; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("pouch.button-lore")) {
            lore.add(line.replace("%n%", String.valueOf(count)));
        }
        inventory.setItem(BUTTON_SLOT, button(Material.HOPPER,
                plugin.getConfig().getString("pouch.button-name", "&a&lWYPŁAĆ WSZYSTKO"), lore));
    }

    private ItemStack display(Entry entry, long now, long life) {
        ItemStack copy = entry.item().clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        long remaining = Math.max(0, life - (now - entry.addedAt()));
        lore.add(Component.empty());
        lore.add(plugin.items().text(plugin.getConfig().getString("pouch.expire-line", "&cZnika za: &e%czas%")
                .replace("%czas%", formatTime(remaining))));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plugin.items().text(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(plugin.items().text(line));
        }
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        return (totalSeconds / 60) + "m " + String.format("%02d", totalSeconds % 60) + "s";
    }

    /** Odswieza GUI wszystkim graczom, ktorzy maja otwarta torbe. */
    public void refreshViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder) {
                render(top, holder.id());
            }
        }
    }

    // ------------------------------------------------------------ zadania i zapis

    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            purge();
            refreshViewers();
        }, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
            }
        }, 1200L, 1200L);
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("pouches");
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            Entry[] slots = new Entry[SIZE];
            for (String key : section.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    String encoded = section.getString(key + ".item");
                    if (slot < 0 || slot >= SIZE || encoded == null) {
                        continue;
                    }
                    ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                    slots[slot] = new Entry(item, section.getLong(key + ".time"));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Nie udalo sie wczytac przedmiotu torby " + rawId + "/" + key);
                }
            }
            data.put(id, slots);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Entry[]> pouch : data.entrySet()) {
            Entry[] slots = pouch.getValue();
            for (int i = 0; i < SIZE; i++) {
                if (slots[i] == null) {
                    continue;
                }
                String base = "pouches." + pouch.getKey() + "." + i;
                yaml.set(base + ".item", Base64.getEncoder().encodeToString(slots[i].item().serializeAsBytes()));
                yaml.set(base + ".time", slots[i].addedAt());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udalo sie zapisac pouches.yml: " + ex.getMessage());
        }
    }
}
