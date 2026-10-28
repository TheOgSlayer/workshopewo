package com.example.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

/** Inventory holder that remembers which menu this is and its state. */
public class Menu implements InventoryHolder {

    public enum Type { MAIN, CATEGORY, SHARDS, BOOKS, LEVELS, QUICK, AMOUNT }

    public final Type type;
    private final Inventory inv;

    public List<String> keys = new ArrayList<>();
    public String key;          // category / enchant key
    public boolean select;      // true = picking an item for Quick Buy
    public int slot = -1;       // quick buy slot being edited
    public Material material;   // amount menu item
    public int amount = 1;      // amount menu value

    public Menu(Type type, int size, Component title) {
        this.type = type;
        this.inv = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
