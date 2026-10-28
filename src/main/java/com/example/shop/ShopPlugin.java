package com.example.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ShopPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    public static final TextColor BLUE = TextColor.color(0x00a2ff);
    public static final TextColor WHITE = NamedTextColor.WHITE;
    public static final TextColor GREEN = NamedTextColor.GREEN;
    public static final TextColor RED = NamedTextColor.RED;

    private static final DecimalFormat FMT =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    public record QuickEntry(Material material, int amount) {}

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private MenuListener menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Vault + an economy plugin are required. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        econ = rsp.getProvider();

        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        menus = new MenuListener(this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(this, this);
        startShardTimer();
        for (String cmd : new String[]{"shop", "quickbuy", "shards", "shardsafkset"}) {
            getCommand(cmd).setExecutor(this);
            getCommand(cmd).setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        if (data != null) saveData();
    }

    // ------------------------------------------------------------ shard rewards

    /** Gives every online player shards on a repeating timer (default 3 per 5 minutes). */
    private void startShardTimer() {
        long ticks = Math.max(1L, getConfig().getLong("shard-rewards.interval-minutes", 5L)) * 60L * 20L;
        long normal = getConfig().getLong("shard-rewards.interval-amount", 3L);
        long afk = getConfig().getLong("shard-rewards.afk-area-amount", 1L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                long amount = inAfkArea(p.getLocation()) ? afk : normal;
                if (amount <= 0) continue;
                data.set("shards." + p.getUniqueId(), getShards(p.getUniqueId()) + amount);
                send(p, Component.text("Awarded ", WHITE)
                        .append(Component.text(amount + " shards", NamedTextColor.DARK_PURPLE))
                        .append(Component.text(" for playing the server", WHITE)));
            }
            saveData();
        }, ticks, ticks);
    }

    /** 10 shards per player kill (default), no cooldown. */
    @EventHandler
    public void onPlayerKill(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        long amount = getConfig().getLong("shard-rewards.kill-amount", 10L);
        setShards(killer.getUniqueId(), getShards(killer.getUniqueId()) + amount);
        send(killer, Component.text("You earned ", WHITE)
                .append(Component.text(amount + " shards", NamedTextColor.DARK_PURPLE))
                .append(Component.text(" for killing ", WHITE))
                .append(Component.text(victim.getName(), BLUE)));
    }

    // ---------------------------------------------------------------- AFK area

    private record Pos(String world, int x, int y, int z) {}

    private Pos getAfkPos(String key) {
        String w = data.getString("afk." + key + ".world");
        if (w == null) return null;
        return new Pos(w, data.getInt("afk." + key + ".x"),
                data.getInt("afk." + key + ".y"), data.getInt("afk." + key + ".z"));
    }

    private void setAfkPos(String key, Location l) {
        String b = "afk." + key;
        data.set(b + ".world", l.getWorld().getName());
        data.set(b + ".x", l.getBlockX());
        data.set(b + ".y", l.getBlockY());
        data.set(b + ".z", l.getBlockZ());
    }

    private String fmtPos(Pos p) {
        return p == null ? "not set" : p.world() + " " + p.x() + ", " + p.y() + ", " + p.z();
    }

    /** True if the location is inside the configured AFK area (both corners must be set). */
    public boolean inAfkArea(Location l) {
        Pos a = getAfkPos("pos1");
        Pos b = getAfkPos("pos2");
        if (a == null || b == null || l.getWorld() == null) return false;
        if (!a.world().equals(b.world()) || !a.world().equals(l.getWorld().getName())) return false;
        return l.getBlockX() >= Math.min(a.x(), b.x()) && l.getBlockX() <= Math.max(a.x(), b.x())
                && l.getBlockY() >= Math.min(a.y(), b.y()) && l.getBlockY() <= Math.max(a.y(), b.y())
                && l.getBlockZ() >= Math.min(a.z(), b.z()) && l.getBlockZ() <= Math.max(a.z(), b.z());
    }

    private boolean afkCommand(CommandSender s, String[] a) {
        if (!s.hasPermission("shop.admin")) {
            s.sendMessage(Component.text("You don't have permission.", RED));
            return true;
        }
        String sub = a.length > 0 ? a[0].toLowerCase() : "";
        switch (sub) {
            case "pos1", "pos2" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage("Players only.");
                    return true;
                }
                String other = sub.equals("pos1") ? "pos2" : "pos1";
                Pos o = getAfkPos(other);
                if (o != null && !o.world().equals(p.getWorld().getName())) data.set("afk." + other, null);
                setAfkPos(sub, p.getLocation());
                saveData();
                s.sendMessage(Component.text(sub + " set to ", WHITE)
                        .append(Component.text(fmtPos(getAfkPos(sub)), BLUE)));
                if (getAfkPos("pos1") != null && getAfkPos("pos2") != null) {
                    s.sendMessage(Component.text("AFK area is active. Players inside it won't be kicked for idling.", WHITE));
                } else {
                    s.sendMessage(Component.text("Now set " + other + " to finish the area.", WHITE));
                }
            }
            case "remove" -> {
                data.set("afk", null);
                saveData();
                s.sendMessage(Component.text("AFK area removed.", WHITE));
            }
            case "info" -> {
                s.sendMessage(Component.text("pos1: ", BLUE).append(Component.text(fmtPos(getAfkPos("pos1")), WHITE)));
                s.sendMessage(Component.text("pos2: ", BLUE).append(Component.text(fmtPos(getAfkPos("pos2")), WHITE)));
            }
            default -> s.sendMessage(Component.text("Usage: /shardsafkset <pos1|pos2|info|remove>", RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("shardsafkset")) {
            if (a.length != 1) return List.of();
            String typed = a[0].toLowerCase();
            return List.of("pos1", "pos2", "info", "remove").stream()
                    .filter(x -> x.startsWith(typed)).toList();
        }
        return null; // default completion (player names etc.)
    }

    /** Stops idle / floating kicks for players standing inside the AFK area. */
    @EventHandler(ignoreCancelled = true)
    public void onKick(PlayerKickEvent e) {
        String cause = e.getCause().name();
        if ((cause.equals("IDLING") || cause.equals("FLYING_PLAYER"))
                && inAfkArea(e.getPlayer().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase();
        if (n.equals("shards")) return shardsCommand(s, a);
        if (n.equals("shardsafkset")) return afkCommand(s, a);
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        if (n.equals("shop")) menus.openMain(p, false, -1);
        else menus.openQuick(p);
        return true;
    }

    private boolean shardsCommand(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) {
                p.sendMessage(Component.text("Shards: ", BLUE)
                        .append(Component.text(fmt(getShards(p.getUniqueId())), WHITE)));
            }
            return true;
        }
        if (!s.hasPermission("shop.admin") || a.length < 3) {
            s.sendMessage(Component.text("Usage: /shards <give|take|set> <player> <amount>", RED));
            return true;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            s.sendMessage(Component.text("Player not online.", RED));
            return true;
        }
        long amt;
        try {
            amt = Long.parseLong(a[2]);
        } catch (NumberFormatException ex) {
            s.sendMessage(Component.text("Invalid amount.", RED));
            return true;
        }
        long cur = getShards(t.getUniqueId());
        switch (a[0].toLowerCase()) {
            case "give" -> setShards(t.getUniqueId(), cur + amt);
            case "take" -> setShards(t.getUniqueId(), Math.max(0, cur - amt));
            case "set" -> setShards(t.getUniqueId(), Math.max(0, amt));
            default -> {
                s.sendMessage(Component.text("Usage: /shards <give|take|set> <player> <amount>", RED));
                return true;
            }
        }
        s.sendMessage(Component.text(t.getName() + " now has " + fmt(getShards(t.getUniqueId())) + " shards.", WHITE));
        return true;
    }

    // -------------------------------------------------------------------- data

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    public QuickEntry getQuick(UUID id, int slot) {
        String b = "quick." + id + "." + slot;
        String item = data.getString(b + ".item");
        if (item == null) return null;
        Material m = Material.matchMaterial(item);
        if (m == null) return null;
        return new QuickEntry(m, Math.max(1, data.getInt(b + ".amount", 1)));
    }

    public void setQuick(UUID id, int slot, Material m, int amount) {
        String b = "quick." + id + "." + slot;
        data.set(b + ".item", m.name());
        data.set(b + ".amount", amount);
        saveData();
    }

    public void removeQuick(UUID id, int slot) {
        data.set("quick." + id + "." + slot, null);
        saveData();
    }

    public void clearQuick(UUID id) {
        data.set("quick." + id, null);
        saveData();
    }

    public long getShards(UUID id) {
        return data.getLong("shards." + id, 0L);
    }

    public void setShards(UUID id, long v) {
        data.set("shards." + id, v);
        saveData();
    }

    /** Price of one unit of the material (first shop that sells it) or -1. */
    public double findPrice(Material m) {
        ConfigurationSection shops = getConfig().getConfigurationSection("shops");
        if (shops == null) return -1;
        for (String cat : shops.getKeys(false)) {
            String path = "shops." + cat + ".items." + m.name();
            if (getConfig().contains(path)) return getConfig().getDouble(path);
        }
        return -1;
    }

    // ------------------------------------------------------------------ helpers

    public static String fmt(double d) {
        return FMT.format(d);
    }

    public static String pretty(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.toLowerCase().split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Sends to both chat and action bar. */
    public void send(Player p, Component c) {
        p.sendMessage(c);
        p.sendActionBar(c);
    }

    private int space(Player p, ItemStack st) {
        int free = 0;
        for (ItemStack c : p.getInventory().getStorageContents()) {
            if (c == null || c.getType().isAir()) free += st.getMaxStackSize();
            else if (c.isSimilar(st)) free += Math.max(0, c.getMaxStackSize() - c.getAmount());
        }
        return free;
    }

    private void inventoryFull(Player p) {
        send(p, Component.text("Inventory Full", RED));
    }

    // --------------------------------------------------------------- purchasing

    /** Buys with money (Vault). */
    public boolean buyItem(Player p, ItemStack stack, String name, double total) {
        if (space(p, stack) < stack.getAmount()) {
            inventoryFull(p);
            return false;
        }
        double bal = econ.getBalance(p);
        if (bal < total) {
            send(p, Component.text("You dont have enough money. ", WHITE)
                    .append(Component.text("Need: ", BLUE))
                    .append(Component.text("$" + fmt(total - bal), GREEN)));
            return false;
        }
        if (!econ.withdrawPlayer(p, total).transactionSuccess()) {
            send(p, Component.text("Transaction failed.", RED));
            return false;
        }
        p.getInventory().addItem(stack.clone());
        send(p, Component.text("Purchased " + stack.getAmount() + " ", WHITE)
                .append(Component.text(name, BLUE))
                .append(Component.text(" for ", WHITE))
                .append(Component.text("$" + fmt(total), GREEN)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        return true;
    }

    /** Buys with shards. */
    public boolean buyWithShards(Player p, ItemStack stack, String name, long cost) {
        if (space(p, stack) < stack.getAmount()) {
            inventoryFull(p);
            return false;
        }
        long bal = getShards(p.getUniqueId());
        if (bal < cost) {
            send(p, Component.text("You dont have enough shards. ", WHITE)
                    .append(Component.text("Need: ", BLUE))
                    .append(Component.text(fmt(cost - bal) + " Shards", GREEN)));
            return false;
        }
        setShards(p.getUniqueId(), bal - cost);
        p.getInventory().addItem(stack.clone());
        send(p, Component.text("Purchased " + stack.getAmount() + " ", WHITE)
                .append(Component.text(name, BLUE))
                .append(Component.text(" for ", WHITE))
                .append(Component.text(fmt(cost) + " Shards", GREEN)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        return true;
    }
}
