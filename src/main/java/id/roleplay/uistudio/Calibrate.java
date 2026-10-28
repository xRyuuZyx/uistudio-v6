package id.roleplay.uistudio;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.UUID;

/** Mengatur posisi tepi layar: penanda ungu harus menempel di tepi kiri dan kanan layar. */
public class Calibrate implements Listener {
    private static class CalMenu extends Menu {
        CalMenu(Component title, int rows) { super(title, rows); }
    }

    private final UIStudio plugin;

    public Calibrate(UIStudio plugin) { this.plugin = plugin; }

    public void open(Player p) {
        UUID id = p.getUniqueId();
        CalMenu m = new CalMenu(Items.c("<dark_gray>Atur posisi HUD"), 3);
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            int w = plugin.players().halfWidth(id);
            m.clear();
            m.fill(Material.BLACK_STAINED_GLASS_PANE);
            int[] deltas = {-10, -1, 1, 10};
            int[] slots = {10, 11, 15, 16};
            for (int i = 0; i < 4; i++) {
                final int d = deltas[i];
                m.set(slots[i], Items.make(Material.ARROW, (d < 0 ? "<red>− " : "<green>+ ") + Math.abs(d)), pl -> {
                    plugin.players().setHalfWidth(id, plugin.players().halfWidth(id) + d);
                    render[0].run();
                });
            }
            m.set(13, Items.make(Material.PAPER, "<white>Lebar setengah layar: <yellow>" + w,
                    "<gray>Lihat kotak <light_purple>ungu</light_purple> di tepi layar.",
                    "<gray>Kotak belum menempel di tepi: naikkan angka.",
                    "<gray>Kotak terpotong / hilang: turunkan angka.",
                    "<gray>Berhenti saat kotak terlihat penuh dan menempel tepi."), null);
            m.set(18, Items.make(Material.BARRIER, "<red>Kembalikan ke bawaan"), pl -> {
                plugin.players().resetHalfWidth(id);
                render[0].run();
            });
            m.set(22, Items.make(Material.LIME_DYE, "<green>Selesai"), pl -> pl.closeInventory());
        };
        render[0].run();
        plugin.huds().preview(p, "calibrate");
        m.open(p);
        plugin.send(p, "<gray>Atur sampai kotak <light_purple>ungu</light_purple> di keempat sudut layar terlihat penuh dan menempel di tepi. "
                + "Pengaturan tersimpan otomatis.");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof CalMenu && e.getPlayer() instanceof Player p) {
            plugin.huds().endPreview(p);
        }
    }
}
