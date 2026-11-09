package pl.firest.eventowki;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Panel admina: klikniecie w item daje go adminowi. */
public final class PanelGui implements Listener {

    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int ARMOR_SLOT = 22;

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final FirestEventowkiPlugin plugin;

    public PanelGui(FirestEventowkiPlugin plugin) {
        this.plugin = plugin;
    }

    public static void open(FirestEventowkiPlugin plugin, Player player) {
        EventItems items = plugin.items();
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                items.text(plugin.getConfig().getString("panel.title", "&6&lPanel Eventówek")));
        holder.inventory = inventory;

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.space());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        for (int i = 0; i < EventItems.IDS.size(); i++) {
            ItemStack stack = items.create(EventItems.IDS.get(i), 1);
            if (stack == null) {
                continue;
            }
            addHint(plugin, stack, "&eKliknij, aby otrzymać");
            inventory.setItem(ITEM_SLOTS[i], stack);
        }

        ItemStack set = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta setMeta = set.getItemMeta();
        setMeta.displayName(items.text("&6&lᴘʟᴏɴᴀᴄᴀ ᴢʙʀᴏᴊᴀ"));
        setMeta.lore(List.of(items.text("&7Komplet: hełm, napierśnik, spodnie, buty"),
                Component.empty(), items.text("&eKliknij, aby otrzymać")));
        set.setItemMeta(setMeta);
        inventory.setItem(ARMOR_SLOT, set);

        player.openInventory(inventory);
    }

    private static void addHint(FirestEventowkiPlugin plugin, ItemStack stack, String hint) {
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(plugin.items().text(hint));
        meta.lore(lore);
        stack.setItemMeta(meta);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) {
            return;
        }
        if (!player.hasPermission(AdminCommand.PERMISSION)) {
            return;
        }

        int raw = event.getRawSlot();
        if (raw == ARMOR_SLOT) {
            for (String id : EventItems.ARMOR) {
                plugin.items().give(player, id, 1);
            }
            player.sendMessage(plugin.msg("panel-given", "%item%", "&6&lᴘʟᴏɴᴀᴄᴀ ᴢʙʀᴏᴊᴀ"));
            return;
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == raw) {
                String id = EventItems.IDS.get(i);
                plugin.items().give(player, id, 1);
                player.sendMessage(plugin.msg("panel-given", "%item%", plugin.items().displayName(id)));
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
