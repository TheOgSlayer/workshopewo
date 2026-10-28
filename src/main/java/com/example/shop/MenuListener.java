package com.example.shop;

import com.example.shop.ShopPlugin.QuickEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.example.shop.ShopPlugin.*;

public class MenuListener implements Listener {

    private static final int QUICK_SLOTS = 45;
    private static final int RESET_SLOT = 53;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final Map<Integer, Integer> AMOUNT_BUTTONS = Map.of(
            10, -64, 11, -16, 12, -1, 14, 1, 15, 16, 16, 64);

    private final ShopPlugin pl;

    public MenuListener(ShopPlugin pl) {
        this.pl = pl;
    }

    // ================================================================ helpers

    private static Component t(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static Component line(String label, String value, TextColor vc) {
        return t(label, BLUE).append(t(value, vc));
    }

    private static ItemStack item(Material m, int amt, Component name, List<Component> lore) {
        ItemStack s = new ItemStack(m, Math.max(1, amt));
        ItemMeta im = s.getItemMeta();
        im.displayName(name);
        if (lore != null) im.lore(lore);
        s.setItemMeta(im);
        return s;
    }

    private static void fill(Inventory inv) {
        ItemStack f = item(Material.BLACK_STAINED_GLASS_PANE, 1, t(" ", WHITE), null);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, f);
        }
    }

    private static ItemStack backButton() {
        return item(Material.BARRIER, 1, t("Back", RED), null);
    }

    /** Columns (0-8) for n items in one row, symmetric around the middle. */
    private static int[] cols(int n) {
        int[] r = new int[n];
        if (n % 2 == 1) {
            int start = (9 - n) / 2;
            for (int i = 0; i < n; i++) r[i] = start + i;
        } else {
            int half = n / 2;
            for (int i = 0; i < half; i++) {
                r[i] = 4 - half + i;
                r[half + i] = 5 + i;
            }
        }
        return r;
    }

    /** Inventory slots for n items, centred horizontally (and vertically). */
    private static int[] layout(int n) {
        int lines = n == 9 ? 2 : Math.max(1, (n + 8) / 9);
        int[] out = new int[n];
        int startRow = lines == 1 ? 1 : (6 - lines) / 2;
        int idx = 0;
        for (int l = 0; l < lines; l++) {
            int count = n / lines + (l < n % lines ? 1 : 0);
            for (int c : cols(count)) out[idx++] = (startRow + l) * 9 + c;
        }
        return out;
    }

    /** 3-row chest normally, double chest for exactly 9 items or more than 9. */
    private static int sizeFor(int n) {
        return n >= 9 ? 54 : 27;
    }

    private static int backSlot(int size) {
        return size == 27 ? 22 : 49;
    }

    private static int indexAt(int n, int slot) {
        int[] pos = layout(n);
        for (int i = 0; i < pos.length; i++) if (pos[i] == slot) return i;
        return -1;
    }

    private static String roman(int n) {
        return n >= 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n);
    }

    private static Component title(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private static Enchantment enchant(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }


    // ================================================================== MAIN

    public void openMain(Player p, boolean select, int quickSlot) {
        List<String> keys = new ArrayList<>();
        var shops = pl.getConfig().getConfigurationSection("shops");
        if (shops != null) keys.addAll(shops.getKeys(false));
        if (!select) {
            keys.add("#shards");
            keys.add("#books");
        }
        if (keys.size() > 45) keys.subList(45, keys.size()).clear();
        int size = sizeFor(keys.size());
        int[] pos = layout(keys.size());
        Menu m = new Menu(Menu.Type.MAIN, size,
                t(select ? "Select an Item" : "Shop", NamedTextColor.DARK_GRAY));
        m.keys = keys;
        m.select = select;
        m.slot = quickSlot;

        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            ItemStack icon;
            if (k.equals("#shards")) {
                icon = item(Material.SPAWNER, 1, t("Shards Shop", BLUE),
                        List.of(t("Buy spawners with shards", WHITE)));
            } else if (k.equals("#books")) {
                icon = item(Material.ENCHANTED_BOOK, 1, t("Books Shop", BLUE),
                        List.of(t("Buy enchanted books", WHITE)));
            } else {
                String name = pl.getConfig().getString("shops." + k + ".title", pretty(k));
                icon = item(categoryIcon(k), 1, t(ChatPlain.strip(name), BLUE),
                        List.of(t("Click to open", WHITE)));
            }
            m.getInventory().setItem(pos[i], icon);
        }
        if (select) m.getInventory().setItem(backSlot(size), backButton());
        fill(m.getInventory());
        p.openInventory(m.getInventory());
    }

    private static Material categoryIcon(String key) {
        return switch (key.toLowerCase()) {
            case "overworld" -> Material.GRASS_BLOCK;
            case "nether" -> Material.NETHERRACK;
            case "end" -> Material.END_STONE;
            case "gear" -> Material.TOTEM_OF_UNDYING;
            case "food" -> Material.GOLDEN_APPLE;
            case "minerals" -> Material.DIAMOND;
            default -> Material.CHEST;
        };
    }

    private void clickMain(Player p, Menu m, int slot) {
        int size = m.getInventory().getSize();
        if (m.select && slot == backSlot(size)) {
            openQuick(p);
            return;
        }
        int i = indexAt(m.keys.size(), slot);
        if (i < 0) return;
        String k = m.keys.get(i);
        if (k.equals("#shards")) openShards(p);
        else if (k.equals("#books")) openBooks(p);
        else openCategory(p, k, m.select, m.slot);
    }

    // ============================================================== CATEGORY

    private void openCategory(Player p, String cat, boolean select, int quickSlot) {
        var sec = pl.getConfig().getConfigurationSection("shops." + cat + ".items");
        if (sec == null) return;
        List<Material> mats = new ArrayList<>();
        for (String k : sec.getKeys(false)) {
            Material mat = Material.matchMaterial(k);
            if (mat == null) pl.getLogger().warning("Unknown material in shop '" + cat + "': " + k);
            else mats.add(mat);
        }
        if (mats.size() > 45) mats = new ArrayList<>(mats.subList(0, 45));
        int size = sizeFor(mats.size());
        int[] pos = layout(mats.size());
        String name = pl.getConfig().getString("shops." + cat + ".title", pretty(cat));
        Menu m = new Menu(Menu.Type.CATEGORY, size, title(name));
        m.key = cat;
        m.select = select;
        m.slot = quickSlot;

        for (int i = 0; i < mats.size(); i++) {
            Material mat = mats.get(i);
            m.keys.add(mat.name());
            double price = sec.getDouble(mat.name());
            List<Component> lore = new ArrayList<>();
            lore.add(line("Price: ", "$" + fmt(price), GREEN));
            lore.add(Component.empty());
            if (select) {
                lore.add(t("Click to add to Quick Buy", WHITE));
            } else {
                int max = mat.getMaxStackSize();
                lore.add(line("Left-click: ", "buy 1", WHITE));
                if (max > 1) {
                    if (Math.min(16, max) > 1) lore.add(line("Right-click: ", "buy " + Math.min(16, max), WHITE));
                    lore.add(line("Shift-click: ", "buy " + max, WHITE));
                }
            }
            m.getInventory().setItem(pos[i], item(mat, 1, t(pretty(mat.name()), WHITE), lore));
        }
        m.getInventory().setItem(backSlot(size), backButton());
        fill(m.getInventory());
        p.openInventory(m.getInventory());
    }

    private void clickCategory(Player p, Menu m, int slot, ClickType ct) {
        int size = m.getInventory().getSize();
        if (slot == backSlot(size)) {
            openMain(p, m.select, m.slot);
            return;
        }
        int idx = indexAt(m.keys.size(), slot);
        if (idx < 0) return;
        Material mat = Material.valueOf(m.keys.get(idx));
        if (m.select) {
            openAmount(p, m.slot, mat, 1);
            return;
        }
        int max = mat.getMaxStackSize();
        int amt = switch (ct) {
            case RIGHT -> Math.min(16, max);
            case SHIFT_LEFT, SHIFT_RIGHT -> max;
            default -> 1;
        };
        double unit = pl.getConfig().getDouble("shops." + m.key + ".items." + mat.name());
        pl.buyItem(p, new ItemStack(mat, amt), pretty(mat.name()), unit * amt);
    }

    // ================================================================ SHARDS

    private static EntityType entityFor(String key) {
        String k = key.equalsIgnoreCase("golem") ? "IRON_GOLEM" : key.toUpperCase();
        try {
            return EntityType.valueOf(k);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String spawnerName(EntityType type) {
        return pretty(type.name()) + " Spawner";
    }

    private ItemStack spawnerItem(EntityType type) {
        ItemStack s = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) s.getItemMeta();
        CreatureSpawner cs = (CreatureSpawner) meta.getBlockState();
        cs.setSpawnedType(type);
        meta.setBlockState(cs);
        meta.displayName(t(spawnerName(type), WHITE));
        s.setItemMeta(meta);
        return s;
    }

    private void openShards(Player p) {
        var sec = pl.getConfig().getConfigurationSection("shards");
        List<String> keys = new ArrayList<>();
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                if (entityFor(k) != null) keys.add(k);
            }
        }
        if (keys.size() > 45) keys.subList(45, keys.size()).clear();
        int size = sizeFor(keys.size());
        int[] pos = layout(keys.size());
        Menu m = new Menu(Menu.Type.SHARDS, size, t("Shards Shop", NamedTextColor.DARK_GRAY));
        m.keys = keys;
        for (int i = 0; i < keys.size(); i++) {
            EntityType type = entityFor(keys.get(i));
            Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
            ItemStack icon = item(egg == null ? Material.SPAWNER : egg, 1, t(spawnerName(type), WHITE),
                    List.of(line("Price: ", fmt(sec.getLong(keys.get(i))) + " Shards", GREEN),
                            Component.empty(), t("Click to buy 1", WHITE)));
            m.getInventory().setItem(pos[i], icon);
        }
        m.getInventory().setItem(4, item(Material.AMETHYST_SHARD, 1,
                line("Your Shards: ", fmt(pl.getShards(p.getUniqueId())), WHITE), null));
        m.getInventory().setItem(backSlot(size), backButton());
        fill(m.getInventory());
        p.openInventory(m.getInventory());
    }

    private void clickShards(Player p, Menu m, int slot) {
        int size = m.getInventory().getSize();
        if (slot == backSlot(size)) {
            openMain(p, false, -1);
            return;
        }
        int idx = indexAt(m.keys.size(), slot);
        if (idx < 0) return;
        String key = m.keys.get(idx);
        EntityType type = entityFor(key);
        long cost = pl.getConfig().getLong("shards." + key);
        boolean ok = pl.buyWithShards(p, spawnerItem(type), spawnerName(type), cost);
        if (ok) {
            m.getInventory().setItem(4, item(Material.AMETHYST_SHARD, 1,
                    line("Your Shards: ", fmt(pl.getShards(p.getUniqueId())), WHITE), null));
        }
    }

    // ================================================================= BOOKS

    private void openBooks(Player p) {
        var sec = pl.getConfig().getConfigurationSection("books");
        List<String> keys = new ArrayList<>();
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                if (enchant(k) != null) keys.add(k);
            }
        }
        if (keys.size() > 45) keys.subList(45, keys.size()).clear();
        int size = sizeFor(keys.size());
        int[] pos = layout(keys.size());
        Menu m = new Menu(Menu.Type.BOOKS, size, t("Books Shop", NamedTextColor.DARK_GRAY));
        m.keys = keys;
        for (int i = 0; i < keys.size(); i++) {
            int max = levels(keys.get(i)).stream().mapToInt(Integer::intValue).max().orElse(1);
            m.getInventory().setItem(pos[i], item(Material.ENCHANTED_BOOK, 1,
                    t(pretty(keys.get(i)), WHITE),
                    List.of(line("Levels: ", "I - " + roman(max), WHITE),
                            Component.empty(), t("Click to choose a level", WHITE))));
        }
        m.getInventory().setItem(backSlot(size), backButton());
        fill(m.getInventory());
        p.openInventory(m.getInventory());
    }

    private List<Integer> levels(String ench) {
        List<Integer> out = new ArrayList<>();
        var sec = pl.getConfig().getConfigurationSection("books." + ench);
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    out.add(Integer.parseInt(k));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private void clickBooks(Player p, Menu m, int slot) {
        if (slot == backSlot(m.getInventory().getSize())) {
            openMain(p, false, -1);
            return;
        }
        int idx = indexAt(m.keys.size(), slot);
        if (idx < 0) return;
        openLevels(p, m.keys.get(idx));
    }

    private void openLevels(Player p, String ench) {
        Menu m = new Menu(Menu.Type.LEVELS, 27, t(pretty(ench) + " Books", NamedTextColor.DARK_GRAY));
        m.key = ench;
        List<Integer> lv = levels(ench);
        int cnt = Math.min(lv.size(), 5);
        int[] pos = layout(cnt);
        for (int i = 0; i < cnt; i++) {
            int level = lv.get(i);
            m.keys.add(String.valueOf(level));
            double price = pl.getConfig().getDouble("books." + ench + "." + level);
            m.getInventory().setItem(pos[i], item(Material.ENCHANTED_BOOK, level,
                    t(pretty(ench) + " " + roman(level), WHITE),
                    List.of(line("Price: ", "$" + fmt(price), GREEN),
                            Component.empty(), t("Click to buy", WHITE))));
        }
        m.getInventory().setItem(22, backButton());
        fill(m.getInventory());
        p.openInventory(m.getInventory());
    }

    private void clickLevels(Player p, Menu m, int slot) {
        if (slot == 22) {
            openBooks(p);
            return;
        }
        int idx = indexAt(m.keys.size(), slot);
        if (idx < 0) return;
        int level = Integer.parseInt(m.keys.get(idx));
        Enchantment e = enchant(m.key);
        if (e == null) return;
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(e, level, true);
        book.setItemMeta(meta);
        double price = pl.getConfig().getDouble("books." + m.key + "." + level);
        pl.buyItem(p, book, pretty(m.key) + " " + roman(level) + " Book", price);
    }

    // ============================================================ QUICK BUY

    public void openQuick(Player p) {
        Menu m = new Menu(Menu.Type.QUICK, 54, t("Quick Buy", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < QUICK_SLOTS; i++) {
            QuickEntry q = pl.getQuick(p.getUniqueId(), i);
            if (q == null) {
                m.getInventory().setItem(i, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 1,
                        t("Empty Slot", NamedTextColor.GRAY),
                        List.of(t("Click to add an item", WHITE))));
            } else {
                double unit = pl.findPrice(q.material());
                List<Component> lore = new ArrayList<>();
                lore.add(line("Amount: ", String.valueOf(q.amount()), WHITE));
                lore.add(unit < 0 ? t("No longer for sale", RED)
                        : line("Price: ", "$" + fmt(unit * q.amount()), GREEN));
                lore.add(Component.empty());
                lore.add(line("Left-click: ", "buy", WHITE));
                lore.add(line("Right-click: ", "edit amount", WHITE));
                lore.add(line("Shift-right-click: ", "remove", WHITE));
                m.getInventory().setItem(i, item(q.material(), q.amount(),
                        t(pretty(q.material().name()), WHITE), lore));
            }
        }
        m.getInventory().setItem(RESET_SLOT, item(Material.RED_STAINED_GLASS_PANE, 1,
                t("Reset Quick Buy", RED), List.of(t("Click to clear every slot", WHITE))));
        fill(m.getInventory());
        p.openInventory(m.getInventory());
    }

    private void clickQuick(Player p, int slot, ClickType ct) {
        if (slot == RESET_SLOT) {
            pl.clearQuick(p.getUniqueId());
            openQuick(p);
            return;
        }
        if (slot >= QUICK_SLOTS) return;
        QuickEntry q = pl.getQuick(p.getUniqueId(), slot);
        if (q == null) {
            openMain(p, true, slot);
            return;
        }
        if (ct == ClickType.SHIFT_RIGHT) {
            pl.removeQuick(p.getUniqueId(), slot);
            openQuick(p);
            return;
        }
        if (ct == ClickType.RIGHT) {
            openAmount(p, slot, q.material(), q.amount());
            return;
        }
        double unit = pl.findPrice(q.material());
        if (unit < 0) {
            pl.send(p, Component.text("That item is no longer for sale.", RED));
            return;
        }
        // GUI stays open and is not refreshed -> spam buying works.
        pl.buyItem(p, new ItemStack(q.material(), q.amount()), pretty(q.material().name()), unit * q.amount());
    }

    // ================================================================ AMOUNT

    private void openAmount(Player p, int slot, Material mat, int amount) {
        Menu m = new Menu(Menu.Type.AMOUNT, 27, t("Set Amount", NamedTextColor.DARK_GRAY));
        m.slot = slot;
        m.material = mat;
        m.amount = Math.max(1, Math.min(amount, mat.getMaxStackSize()));

        for (var en : AMOUNT_BUTTONS.entrySet()) {
            int d = en.getValue();
            boolean neg = d < 0;
            m.getInventory().setItem(en.getKey(), item(
                    neg ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE,
                    Math.abs(d), t((neg ? "-" : "+") + Math.abs(d), neg ? RED : GREEN), null));
        }
        m.getInventory().setItem(13, preview(m));
        m.getInventory().setItem(22, item(Material.LIME_CONCRETE, 1, t("Confirm", GREEN),
                List.of(t("Save to Quick Buy", WHITE))));
        m.getInventory().setItem(18, item(Material.BARRIER, 1, t("Cancel", RED), null));
        fill(m.getInventory());
        p.openInventory(m.getInventory());
    }

    private ItemStack preview(Menu m) {
        int max = m.material.getMaxStackSize();
        double unit = pl.findPrice(m.material);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Amount: ", m.amount + " / " + max, WHITE));
        if (unit >= 0) lore.add(line("Price: ", "$" + fmt(unit * m.amount), GREEN));
        return item(m.material, m.amount, t(pretty(m.material.name()), WHITE), lore);
    }

    private void clickAmount(Player p, Menu m, int slot) {
        if (slot == 18) {
            openQuick(p);
            return;
        }
        if (slot == 22) {
            pl.setQuick(p.getUniqueId(), m.slot, m.material, m.amount);
            openQuick(p);
            return;
        }
        Integer delta = AMOUNT_BUTTONS.get(slot);
        if (delta == null) return;
        m.amount = Math.max(1, Math.min(m.material.getMaxStackSize(), m.amount + delta));
        m.getInventory().setItem(13, preview(m));
    }

    // =============================================================== EVENTS

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType().isAir()) return;

        int slot = e.getSlot();
        ClickType ct = e.getClick();
        switch (m.type) {
            case MAIN -> clickMain(p, m, slot);
            case CATEGORY -> clickCategory(p, m, slot, ct);
            case SHARDS -> clickShards(p, m, slot);
            case BOOKS -> clickBooks(p, m, slot);
            case LEVELS -> clickLevels(p, m, slot);
            case QUICK -> clickQuick(p, slot, ct);
            case AMOUNT -> clickAmount(p, m, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    /** Strips legacy color codes for use in icon names. */
    private static final class ChatPlain {
        static String strip(String s) {
            return s.replaceAll("(?i)&[0-9a-fk-or]", "");
        }
    }
}
