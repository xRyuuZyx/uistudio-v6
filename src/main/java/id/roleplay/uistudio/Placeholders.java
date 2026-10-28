package id.roleplay.uistudio;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Placeholder bawaan + PlaceholderAPI (opsional). Hasilnya masih berupa teks MiniMessage. */
public class Placeholders {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final UIStudio plugin;
    private Method papiSet;

    public Placeholders(UIStudio plugin) {
        this.plugin = plugin;
        try {
            papiSet = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                    .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (Exception e) {
            papiSet = null;
        }
    }

    public boolean hasPapi() {
        return papiSet != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public String apply(Player p, String text, String[] args) {
        if (text == null) return "";
        String r = text;
        if (r.indexOf('%') >= 0) {
            r = r.replace("%player%", MM.escapeTags(p.getName()));
            if (r.contains("%money%")) r = r.replace("%money%", num(balance(p)));
            if (r.contains("%job_")) r = jobTokens(p, r);
            if (r.contains("%gang_")) r = gangTokens(p, r);
            if (r.contains("%bank%")) r = r.replace("%bank%", bank(p));
            if (r.contains("%job%")) r = r.replace("%job%", job(p));
            if (r.contains("%gang%")) r = r.replace("%gang%", gang(p));
            if (r.contains("%health%")) r = r.replace("%health%", String.valueOf((int) Math.ceil(p.getHealth())));
            if (r.contains("%food%")) r = r.replace("%food%", String.valueOf(p.getFoodLevel()));
            if (r.contains("%armor%")) {
                AttributeInstance a = p.getAttribute(Attribute.ARMOR);
                r = r.replace("%armor%", String.valueOf(a == null ? 0 : (int) a.getValue()));
            }
            if (r.contains("%air%")) {
                int max = p.getMaximumAir();
                r = r.replace("%air%", String.valueOf(max <= 0 ? 100 : Math.max(0, p.getRemainingAir()) * 100 / max));
            }
            if (r.contains("%saturation%")) r = r.replace("%saturation%", String.valueOf((int) Math.ceil(p.getSaturation())));
            if (r.contains("%voice_")) {
                r = r.replace("%voice_color%", Voice.color(p.getUniqueId()))
                        .replace("%voice_icon%", Voice.icon(p.getUniqueId()))
                        .replace("%voice_value%", "100");
            }
            r = r.replace("%ping%", String.valueOf(p.getPing()))
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
            if (r.contains("%time%")) r = r.replace("%time%", now("HH:mm"));
            if (r.contains("%date%")) r = r.replace("%date%", now("dd/MM/yyyy"));
            for (int i = 0; i < 9; i++) {
                r = r.replace("%arg" + (i + 1) + "%", args != null && i < args.length ? MM.escapeTags(args[i]) : "");
            }
            if (hasPapi()) {
                try {
                    r = (String) papiSet.invoke(null, p, r);
                } catch (Exception ignored) { }
            }
        }
        return r;
    }

    /** Angka dengan pemisah ribuan sesuai config (bawaan titik: 10.731). */
    private String num(double d) {
        String sep = plugin.getConfig().getString("hud.thousands-separator", ".");
        return String.format(Locale.US, "%,.0f", d).replace(",", sep);
    }

    private String now(String pattern) {
        String zone = plugin.getConfig().getString("timezone", "Asia/Makassar");
        try {
            return ZonedDateTime.now(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception e) {
            return "--";
        }
    }

    private String bank(Player p) {
        String expr = plugin.getConfig().getString("hud.bank-placeholder", "0");
        String v = expr;
        if (expr.indexOf('%') >= 0 && hasPapi()) {
            try {
                v = (String) papiSet.invoke(null, p, expr);
            } catch (Exception e) {
                v = "0";
            }
        }
        v = v == null ? "0" : v.trim().replace(",", "");
        try {
            return num(Double.parseDouble(v));
        } catch (NumberFormatException e) {
            return MM.escapeTags(v);
        }
    }

    public record GangInfo(String name, String type, UUID leader, Set<UUID> members) {}

    private String upper(String s) {
        return plugin.getConfig().getBoolean("hud.uppercase-names", true) ? s.toUpperCase(Locale.ROOT) : s;
    }

    /** Nama job dari RoleplayJobs, atau null kalau belum punya job. */
    public String jobNameOf(UUID id) {
        try {
            Plugin rj = Bukkit.getPluginManager().getPlugin("RoleplayJobs");
            if (rj == null || !rj.isEnabled()) return null;
            Object jm = rj.getClass().getMethod("jobs").invoke(rj);
            Object job = jm.getClass().getMethod("getJob", UUID.class).invoke(jm, id);
            return job == null ? null : String.valueOf(job.getClass().getMethod("display").invoke(job));
        } catch (Exception e) {
            return null;
        }
    }

    /** Data gang dari RoleplayHUD, atau null kalau belum punya gang. */
    @SuppressWarnings("unchecked")
    public GangInfo gangOf(UUID id) {
        try {
            Plugin hud = Bukkit.getPluginManager().getPlugin("RoleplayHUD");
            if (hud == null || !hud.isEnabled()) return null;
            Object gm = hud.getClass().getMethod("gangs").invoke(hud);
            Object g = gm.getClass().getMethod("byPlayer", UUID.class).invoke(gm, id);
            if (g == null) return null;
            Class<?> c = g.getClass();
            return new GangInfo(String.valueOf(c.getField("name").get(g)), String.valueOf(c.getField("type").get(g)),
                    (UUID) c.getField("leader").get(g), (Set<UUID>) c.getField("members").get(g));
        } catch (Exception e) {
            return null;
        }
    }

    public String jobRankOf(UUID id, String job) {
        String custom = plugin.players().jobRank(id, job);
        return custom != null ? custom : plugin.getConfig().getString("ranks.job-default", "Pekerja");
    }

    public String gangRankOf(UUID id, GangInfo g) {
        String custom = plugin.players().gangRank(id, g.name());
        if (custom != null) return custom;
        String key = g.type().equalsIgnoreCase("KELUARGA") ? "family" : "gang";
        boolean leader = g.leader().equals(id);
        return plugin.getConfig().getString("ranks." + key + (leader ? ".leader" : ".member"), leader ? "Pemimpin" : "Anggota");
    }

    private String jobTokens(Player p, String r) {
        String job = jobNameOf(p.getUniqueId());
        String n = job == null ? "" : MM.escapeTags(upper(job));
        String rk = job == null ? "" : MM.escapeTags(jobRankOf(p.getUniqueId(), job));
        String line = job == null
                ? plugin.getConfig().getString("hud.job-none", "<gray>Belum ada pekerjaan")
                : plugin.getConfig().getString("hud.job-format", "<white>%job_name% <gray>- <aqua>%job_rank%")
                        .replace("%job_name%", n).replace("%job_rank%", rk);
        return r.replace("%job_line%", line).replace("%job_name%", n).replace("%job_rank%", rk);
    }

    private String gangTokens(Player p, String r) {
        GangInfo g = gangOf(p.getUniqueId());
        String n = g == null ? "" : MM.escapeTags(upper(g.name()));
        String rk = g == null ? "" : MM.escapeTags(gangRankOf(p.getUniqueId(), g));
        String line = g == null
                ? plugin.getConfig().getString("hud.gang-none", "<gray>Belum ada gang / keluarga")
                : plugin.getConfig().getString("hud.gang-format", "<white>%gang_name% <gray>- <gold>%gang_rank%")
                        .replace("%gang_name%", n).replace("%gang_rank%", rk);
        return r.replace("%gang_line%", line).replace("%gang_name%", n).replace("%gang_rank%", rk);
    }

    private double balance(Player p) {
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) return 0;
            Method m = eco.getMethod("getBalance", OfflinePlayer.class);
            return ((Number) m.invoke(rsp.getProvider(), p)).doubleValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private String job(Player p) {
        try {
            Plugin rj = Bukkit.getPluginManager().getPlugin("RoleplayJobs");
            if (rj == null || !rj.isEnabled()) return "-";
            Object jm = rj.getClass().getMethod("jobs").invoke(rj);
            Object job = jm.getClass().getMethod("getJob", UUID.class).invoke(jm, p.getUniqueId());
            if (job == null) return "Pengangguran";
            return String.valueOf(job.getClass().getMethod("display").invoke(job));
        } catch (Exception e) {
            return "-";
        }
    }

    private String gang(Player p) {
        try {
            Plugin hud = Bukkit.getPluginManager().getPlugin("RoleplayHUD");
            if (hud == null || !hud.isEnabled()) return "-";
            Object gm = hud.getClass().getMethod("gangs").invoke(hud);
            Object g = gm.getClass().getMethod("byPlayer", UUID.class).invoke(gm, p.getUniqueId());
            if (g == null) return "-";
            return MM.escapeTags(String.valueOf(g.getClass().getField("name").get(g)));
        } catch (Exception e) {
            return "-";
        }
    }
}
