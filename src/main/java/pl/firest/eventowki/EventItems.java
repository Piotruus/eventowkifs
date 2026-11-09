package pl.firest.eventowki;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Tworzenie i rozpoznawanie przedmiotow eventowych (definicje w config.yml). */
public final class EventItems {

    public static final List<String> IDS = List.of("miecz", "sakwa", "totem", "helm", "napiersnik", "spodnie", "buty");
    public static final List<String> ARMOR = List.of("helm", "napiersnik", "spodnie", "buty");

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final FirestEventowkiPlugin plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey uidKey;

    public EventItems(FirestEventowkiPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item_id");
        this.uidKey = new NamespacedKey(plugin, "pouch_uid");
    }

    /** Tekst z kodami &amp; bez kursywy. */
    public Component text(String raw) {
        return LEGACY.deserialize(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public ItemStack create(String id, int amount) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items." + id);
        if (sec == null) {
            return null;
        }

        Material material = Material.matchMaterial(sec.getString("material", "STONE"));
        if (material == null || material.isAir() || !material.isItem()) {
            material = Material.STONE;
        }

        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(text(sec.getString("name", id)));

        String minutes = String.valueOf(plugin.getConfig().getInt("pouch.lifetime-minutes", 15));
        List<Component> lore = new ArrayList<>();
        for (String line : sec.getStringList("lore")) {
            lore.add(text(line.replace("%czas%", minutes).replace("%sloty%", String.valueOf(PouchManager.SIZE))));
        }
        meta.lore(lore);

        ConfigurationSection enchants = sec.getConfigurationSection("enchants");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (enchantment != null) {
                    meta.addEnchant(enchantment, enchants.getInt(key), true);
                } else {
                    plugin.getLogger().warning("Nieznany enchant w configu: " + key);
                }
            }
        }

        String model = sec.getString("item-model", "");
        if (model != null && !model.isBlank()) {
            NamespacedKey modelKey = NamespacedKey.fromString(model.trim());
            if (modelKey != null) {
                meta.setItemModel(modelKey);
            }
        }

        String equipModel = sec.getString("equip-model", "");
        EquipmentSlot slot = slotFor(id);
        if (equipModel != null && !equipModel.isBlank() && slot != null) {
            NamespacedKey equipKey = NamespacedKey.fromString(equipModel.trim());
            if (equipKey != null) {
                EquippableComponent equippable = meta.getEquippable();
                equippable.setSlot(slot);
                equippable.setModel(equipKey);
                meta.setEquippable(equippable);
            }
        }

        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        if (id.equals("sakwa")) {
            meta.getPersistentDataContainer().set(uidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        }

        stack.setItemMeta(meta);
        return stack;
    }

    /** Daje graczowi item(y); to co sie nie zmiesci, wypada pod nogi. Kazda torba jest unikalna. */
    public void give(Player player, String id, int amount) {
        int remaining = Math.max(1, amount);
        while (remaining > 0) {
            ItemStack stack = create(id, 1);
            if (stack == null) {
                return;
            }
            int per = id.equals("sakwa") ? 1 : Math.min(remaining, Math.max(1, stack.getMaxStackSize()));
            stack.setAmount(per);
            remaining -= per;
            player.getInventory().addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    public String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public boolean isItem(ItemStack stack, String id) {
        return id.equals(idOf(stack));
    }

    /** Unikalne ID torby albo null, jesli to nie torba. */
    public UUID pouchId(ItemStack stack) {
        if (!isItem(stack, "sakwa")) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(uidKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String displayName(String id) {
        return plugin.getConfig().getString("items." + id + ".name", id);
    }

    private static EquipmentSlot slotFor(String id) {
        return switch (id) {
            case "helm" -> EquipmentSlot.HEAD;
            case "napiersnik" -> EquipmentSlot.CHEST;
            case "spodnie" -> EquipmentSlot.LEGS;
            case "buty" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
