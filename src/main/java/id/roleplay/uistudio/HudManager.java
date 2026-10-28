package id.roleplay.uistudio;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

/** HUD: baris boss bar + action bar berisi gambar, teks, bar, animasi. */
public class HudManager implements Listener {
    public static class Def {
        public final String id;
        public final File file;
        public final YamlConfiguration cfg;

        Def(String id, File file, YamlConfiguration cfg) {
            this.id = id;
            this.file = file;
            this.cfg = cfg;
        }
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] NO_ARGS = new String[0];
    private final UIStudio plugin;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> active = new HashMap<>();
    private final Map<UUID, String> temp = new HashMap<>();
    private final Map<UUID, List<BossBar>> bars = new HashMap<>();
    private BukkitTask task;

    public HudManager(UIStudio plugin) { this.plugin = plugin; }

    public Set<String> ids() { return defs.keySet(); }

    public Def get(String id) { return defs.get(id.toLowerCase()); }

    public void load() {
        defs.clear();
        File dir = new File(plugin.getDataFolder(), "huds");
        if (!dir.exists()) {
            dir.mkdirs();
            plugin.saveResource("huds/main.yml", false);
            plugin.saveResource("huds/demo.yml", false);
        }
        for (String n : new String[]{"fivem", "calibrate"}) {
            if (!new File(dir, n + ".yml").exists()) plugin.saveResource("huds/" + n + ".yml", false);
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase();
                defs.put(id, new Def(id, f, YamlConfiguration.loadConfiguration(f)));
            }
        }
        plugin.getLogger().info(defs.size() + " HUD dimuat.");
    }

    public void start() {
        if (task != null) task.cancel();
        long interval = Math.max(2L, plugin.getConfig().getLong("hud.interval-ticks", 5));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) render(p);
        }, 20L, interval);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
    }

    // ---------------- API ----------------

    private boolean add(Player p, String id) {
        if (!defs.containsKey(id.toLowerCase())) return false;
        active.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashSet<>()).add(id.toLowerCase());
        return true;
    }

    private void persist(Player p) {
        plugin.players().setHuds(p.getUniqueId(), active.getOrDefault(p.getUniqueId(), Set.of()));
    }

    /** Tampilkan HUD (dan ingat pilihan ini untuk login berikutnya). */
    public boolean show(Player p, String id) {
        boolean ok = add(p, id);
        if (ok) persist(p);
        return ok;
    }

    /** Tampilkan hanya HUD ini (HUD lain disembunyikan). Pilihan diingat. */
    public boolean only(Player p, String id) {
        if (!defs.containsKey(id.toLowerCase())) return false;
        active.remove(p.getUniqueId());
        clear(p);
        add(p, id);
        persist(p);
        return true;
    }

    public void hide(Player p, String id) {
        Set<String> s = active.get(p.getUniqueId());
        if (s != null) s.remove(id.toLowerCase());
        if (s == null || s.isEmpty()) clear(p);
        persist(p);
    }

    public void toggle(Player p, String id) {
        Set<String> s = active.get(p.getUniqueId());
        if (s != null && s.contains(id.toLowerCase())) hide(p, id); else show(p, id);
    }

    public Set<String> activeOf(Player p) { return active.getOrDefault(p.getUniqueId(), Set.of()); }

    /** Tampilan sementara (tidak disimpan), dipakai untuk kalibrasi. */
    public boolean preview(Player p, String id) {
        if (!defs.containsKey(id.toLowerCase())) return false;
        temp.put(p.getUniqueId(), id.toLowerCase());
        return true;
    }

    public void endPreview(Player p) {
        temp.remove(p.getUniqueId());
        Set<String> s = active.get(p.getUniqueId());
        if (s == null || s.isEmpty()) clear(p);
    }

    private void clear(Player p) {
        List<BossBar> list = bars.remove(p.getUniqueId());
        if (list != null) for (BossBar b : list) p.hideBossBar(b);
        p.sendActionBar(Component.empty());
    }

    // ---------------- Render ----------------

    private void render(Player p) {
        UUID uid = p.getUniqueId();
        Set<String> ids = new LinkedHashSet<>(active.getOrDefault(uid, Set.of()));
        String t = temp.get(uid);
        if (t != null) ids.add(t);
        if (ids.isEmpty()) return;

        int half = plugin.players().halfWidth(uid);
        List<Component> boss = new ArrayList<>();
        List<Integer> prio = new ArrayList<>();
        Component action = null;
        for (String hid : ids) {
            Def d = defs.get(hid);
            if (d == null) continue;
            int span = Math.max(d.cfg.getInt("span", 240), half + 24);
            for (Map<?, ?> row : d.cfg.getMapList("rows")) {
                Component c = buildRow(p, row, span, half);
                String type = row.get("type") == null ? "bossbar" : String.valueOf(row.get("type")).toLowerCase();
                if (type.equals("actionbar")) {
                    action = c;
                } else {
                    boss.add(c);
                    prio.add(num(row.get("priority"), 100));
                }
            }
        }

        // Minecraft hanya menampilkan boss bar sampai 1/3 tinggi layar: buang baris berprioritas rendah dulu.
        int allowed = allowedRows(half);
        while (boss.size() > allowed && boss.size() > 1) {
            int idx = 0;
            for (int i = 1; i < boss.size(); i++) if (prio.get(i) <= prio.get(idx)) idx = i;
            boss.remove(idx);
            prio.remove(idx);
        }

        List<BossBar> list = bars.computeIfAbsent(uid, k -> new ArrayList<>());
        while (list.size() > boss.size()) p.hideBossBar(list.remove(list.size() - 1));
        for (int i = 0; i < boss.size(); i++) {
            if (i < list.size()) {
                list.get(i).name(boss.get(i));
            } else {
                BossBar b = BossBar.bossBar(boss.get(i), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                p.showBossBar(b);
                list.add(b);
            }
        }
        if (action != null) p.sendActionBar(action);
    }

    /** Perkiraan jumlah baris boss bar yang muat (layar diasumsikan rasio 16:9, atur di screen.aspect). */
    private int allowedRows(int half) {
        double aspect = Math.max(1.0, plugin.getConfig().getDouble("screen.aspect", 16.0 / 9.0));
        double h = 2.0 * half / aspect;
        return Math.max(1, (int) Math.ceil((h / 3.0 - 12.0) / 19.0));
    }

    private static int num(Object o, int def) { return o instanceof Number n ? n.intValue() : def; }

    private static TextColor color(String s) {
        if (s == null || s.isBlank()) return null;
        return TextColor.fromHexString(s.trim());
    }

    private String val(Player p, Object o) {
        if (o == null) return "";
        return plugin.placeholders().apply(p, String.valueOf(o), NO_ARGS);
    }

    private double parse(Player p, Object expr, double def) {
        if (expr == null) return def;
        String s = val(p, expr).replaceAll("[^0-9.\\-]", "");
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean right(Map<?, ?> l) { return "right".equalsIgnoreCase(String.valueOf(l.get("align"))); }

    /** anchor: right = tepi kanan layar, left = tepi kiri layar, lainnya = tengah. */
    private static int anchor(Map<?, ?> l, int half) {
        String a = l.get("anchor") == null ? "" : String.valueOf(l.get("anchor")).toLowerCase();
        return a.equals("right") ? half : a.equals("left") ? -half : 0;
    }

    private static String numId(char ch) {
        if (ch >= '0' && ch <= '9') return "num_" + ch;
        return switch (ch) {
            case ',' -> "num_comma";
            case '.' -> "num_dot";
            case '$' -> "num_dollar";
            case '-' -> "num_minus";
            default -> null;
        };
    }

    private Component buildRow(Player p, Map<?, ?> row, int span, int half) {
        Images images = plugin.images();
        Row r = new Row(images, -span);
        Object layers = row.get("layers");
        if (!(layers instanceof List<?> list)) return r.finish(span);

        for (Object o : list) {
            if (!(o instanceof Map<?, ?> l)) continue;
            String type = String.valueOf(l.get("type")).toLowerCase();
            int x = num(l.get("x"), 0) + anchor(l, half);
            switch (type) {
                case "image" -> {
                    String id = val(p, l.get("id"));
                    int ix = x;
                    if (right(l)) {
                        Images.Img im = images.get(id);
                        if (im != null) ix = x - im.advance;
                    }
                    r.image(id, color(val(p, l.get("color"))), ix);
                }
                case "text" -> {
                    String t = val(p, l.get("text"));
                    int sx = x;
                    if (right(l)) {
                        sx = x - Ui.textWidth(PlainTextComponentSerializer.plainText().serialize(MM.deserialize(t)));
                    }
                    r.text(t, sx);
                }
                case "number" -> {
                    String s = val(p, l.get("value"));
                    List<String> ids = new ArrayList<>();
                    int total = 0;
                    for (char ch : s.toCharArray()) {
                        String id = numId(ch);
                        if (id == null) continue;
                        Images.Img im = images.get(id);
                        if (im == null) continue;
                        ids.add(id);
                        total += im.advance;
                    }
                    TextColor tint = color(val(p, l.get("color")));
                    r.moveTo(right(l) ? x - total : x);
                    for (String id : ids) r.image(id, tint, r.pos());
                }
                case "animation" -> {
                    if (l.get("frames") instanceof List<?> fr && !fr.isEmpty()) {
                        int fps = Math.max(1, num(l.get("fps"), 4));
                        int idx = (int) ((System.currentTimeMillis() * fps / 1000) % fr.size());
                        r.image(String.valueOf(fr.get(idx)), color(val(p, l.get("color"))), x);
                    }
                }
                case "bar" -> {
                    String img = l.get("image") == null ? "seg" : String.valueOf(l.get("image"));
                    int n = Math.max(1, num(l.get("segments"), 10));
                    double max = Math.max(0.0001, parse(p, l.get("max"), 20));
                    double v = parse(p, l.get("value"), 0);
                    int filled = (int) Math.round(Math.max(0, Math.min(1, v / max)) * n);
                    if (v > 0 && filled == 0) filled = 1;
                    TextColor on = color(val(p, l.get("color")));
                    TextColor off = color(val(p, l.get("empty")));
                    if (on == null) on = NamedTextColor.WHITE;
                    if (off == null) off = TextColor.color(0x33333d);
                    r.moveTo(x);
                    for (int i = 0; i < n; i++) r.image(img, i < filled ? on : off, r.pos());
                }
                case "meter" -> {
                    // gambar dasar: <image>_bg (latar penuh) dan <image>_1 .. <image>_<steps> (isi)
                    String base = String.valueOf(l.get("image"));
                    int steps = Math.max(1, num(l.get("steps"), 20));
                    double max = Math.max(0.0001, parse(p, l.get("max"), 20));
                    double v = parse(p, l.get("value"), 0);
                    int k = (int) Math.round(Math.max(0, Math.min(1, v / max)) * steps);
                    if (v > 0 && k == 0) k = 1;
                    TextColor on = color(val(p, l.get("color")));
                    TextColor off = color(val(p, l.get("bg")));
                    r.image(base + "_bg", off == null ? TextColor.color(0x33333d) : off, x);
                    if (k > 0) r.image(base + "_" + k, on == null ? NamedTextColor.WHITE : on, x);
                    String icon = val(p, l.get("icon"));
                    if (!icon.isBlank()) {
                        Images.Img bg = images.get(base + "_bg");
                        Images.Img ic = images.get(icon);
                        if (bg != null && ic != null) r.image(icon, null, x + (bg.canvas - ic.canvas) / 2);
                    }
                }
                default -> { }
            }
        }
        return r.finish(span);
    }

    // ---------------- Event ----------------

    private List<String> defaultAuto() {
        List<String> auto = plugin.getConfig().getStringList("hud.auto");
        // config lama berisi [main]: pakai HUD fivem yang baru kalau ada
        if (auto.equals(List.of("main")) && defs.containsKey("fivem")) return List.of("fivem");
        return auto;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            List<String> saved = plugin.players().huds(p.getUniqueId());
            for (String id : saved != null ? saved : defaultAuto()) add(p, id);
            if (plugin.players().firstHint(p.getUniqueId())) {
                plugin.send(p, "<gray>Posisi HUD di pojok layar tidak pas? Ketik <white>/uistudio calibrate</white> untuk mengaturnya.");
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        active.remove(e.getPlayer().getUniqueId());
        temp.remove(e.getPlayer().getUniqueId());
        bars.remove(e.getPlayer().getUniqueId());
    }
}
