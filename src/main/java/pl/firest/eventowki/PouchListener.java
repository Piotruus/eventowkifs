package pl.firest.eventowki;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/** Otwieranie torby (PPM) i obsluga jej GUI. */
public final class PouchListener implements Listener {

    private final FirestEventowkiPlugin plugin;

    public PouchListener(FirestEventowkiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        UUID id = plugin.items().pouchId(event.getItem());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        plugin.pouches().open(event.getPlayer(), id);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PouchManager.Holder holder)) {
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }

        // klikniecie w ekwipunek gracza: blokujemy wrzucanie przedmiotow do torby
        if (raw >= top.getSize()) {
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        // klikniecie w torbe: nic nie mozna tam wkladac ani wyciagac "na kursor"
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        PouchManager pouches = plugin.pouches();
        UUID id = holder.id();

        if (raw == PouchManager.BUTTON_SLOT) {
            int[] result = pouches.withdrawAll(player, id);
            if (result[0] == 0 && result[1] == 0) {
                player.sendMessage(plugin.msg("pouch-nothing"));
            } else {
                if (result[0] > 0) {
                    player.sendMessage(plugin.msg("pouch-withdrawn", "%n%", String.valueOf(result[0])));
                }
                if (result[1] > 0) {
                    player.sendMessage(plugin.msg("pouch-inv-full"));
                }
            }
            pouches.render(top, id);
            return;
        }

        if (raw < PouchManager.SIZE) {
            PouchManager.Entry entry = pouches.slots(id)[raw];
            if (entry == null) {
                return;
            }
            if (pouches.withdraw(player, id, raw) == 0) {
                player.sendMessage(plugin.msg("pouch-inv-full"));
            }
            pouches.render(top, id);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PouchManager.Holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
