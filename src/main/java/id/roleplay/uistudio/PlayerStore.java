package id.roleplay.uistudio;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Pengaturan tiap pemain yang disimpan permanen: HUD pilihan dan lebar layar. */
public class PlayerStore {
    private final UIStudio plugin;
    private final File file;
    private final YamlConfiguration y;

    public PlayerStore(UIStudio plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.y = YamlConfiguration.loadConfiguration(file);
    }

    private static String k(UUID id) { return "players." + id; }

    public int halfWidth(UUID id) {
        return y.getInt(k(id) + ".half-width", plugin.getConfig().getInt("screen.half-width", 320));
    }

    public void setHalfWidth(UUID id, int w) {
        y.set(k(id) + ".half-width", Math.max(160, Math.min(1200, w)));
        save();
    }

    public void resetHalfWidth(UUID id) {
        y.set(k(id) + ".half-width", null);
        save();
    }

    /** null = pemain belum pernah memilih HUD. */
    public List<String> huds(UUID id) {
        return y.contains(k(id) + ".huds") ? y.getStringList(k(id) + ".huds") : null;
    }

    public void setHuds(UUID id, Collection<String> list) {
        y.set(k(id) + ".huds", new ArrayList<>(list));
        save();
    }

    /** true hanya saat pertama kali dipanggil untuk pemain ini. */
    public boolean firstHint(UUID id) {
        if (y.getBoolean(k(id) + ".hint", false)) return false;
        y.set(k(id) + ".hint", true);
        save();
        return true;
    }

    // ---- pangkat khusus (menimpa pangkat bawaan) ----
    public String jobRank(UUID id, String job) {
        String j = y.getString(k(id) + ".job-rank.job");
        return job != null && job.equalsIgnoreCase(j) ? y.getString(k(id) + ".job-rank.text") : null;
    }

    public void setJobRank(UUID id, String job, String text) {
        y.set(k(id) + ".job-rank.job", job);
        y.set(k(id) + ".job-rank.text", text);
        save();
    }

    public void clearJobRank(UUID id) {
        y.set(k(id) + ".job-rank", null);
        save();
    }

    public String gangRank(UUID id, String gang) {
        String g = y.getString(k(id) + ".gang-rank.gang");
        return gang != null && gang.equalsIgnoreCase(g) ? y.getString(k(id) + ".gang-rank.text") : null;
    }

    public void setGangRank(UUID id, String gang, String text) {
        y.set(k(id) + ".gang-rank.gang", gang);
        y.set(k(id) + ".gang-rank.text", text);
        save();
    }

    public void clearGangRank(UUID id) {
        y.set(k(id) + ".gang-rank", null);
        save();
    }

    private void save() {
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan players.yml: " + e.getMessage());
        }
    }
}
