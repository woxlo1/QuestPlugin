package com.woxloi.questplugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public class ItemBackup {

    public static class InventoryBackup {
        private final ItemStack[] contents;
        private final ItemStack[] armorContents;
        private final ItemStack offHand;

        public InventoryBackup(ItemStack[] contents, ItemStack[] armorContents, ItemStack offHand) {
            this.contents = contents;
            this.armorContents = armorContents;
            this.offHand = offHand;
        }

        public ItemStack[] getContents() { return contents; }
        public ItemStack[] getArmorContents() { return armorContents; }
        public ItemStack getOffHand() { return offHand; }
    }

    /**
     * プレイヤーのインベントリをBase64でファイルに保存
     */
    public static void saveInventoryToFile(Player player, File file) {
        try {
            InventoryBackup backup = new InventoryBackup(
                    player.getInventory().getContents(),
                    player.getInventory().getArmorContents(),
                    player.getInventory().getItemInOffHand()
            );

            YamlConfiguration config = new YamlConfiguration();
            config.set("contents", itemStackArrayToBase64(backup.getContents()));
            config.set("armor", itemStackArrayToBase64(backup.getArmorContents()));
            config.set("offhand", itemStackToBase64(backup.getOffHand()));

            config.save(file);

            player.sendMessage("§e§lインベントリをファイルに保存しました。");
            player.sendMessage("§e§l万が一バグで消えた場合補填はありません。");

        } catch (Exception e) {
            player.sendMessage("§cインベントリの保存に失敗しました。");
            e.printStackTrace();
        }
    }

    /**
     * ファイルからインベントリを読み込み、プレイヤーに復元
     */
    public static void loadInventoryFromFile(Player player, File file) {
        if (!file.exists()) {
            player.sendMessage("§c§lバックアップファイルが存在しません。");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            ItemStack[] contents = itemStackArrayFromBase64(config.getString("contents"));
            ItemStack[] armor = itemStackArrayFromBase64(config.getString("armor"));
            ItemStack offHand = itemStackFromBase64(config.getString("offhand"));

            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setItemInOffHand(offHand == null ? new ItemStack(Material.AIR) : offHand);

            player.updateInventory();
            player.sendMessage("§e§lインベントリを復元しました。");

        } catch (Exception e) {
            player.sendMessage("§c§lインベントリの復元に失敗しました。");
            e.printStackTrace();
        }
    }

    // ========= Base64 シリアライズ関連 =========

    public static String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        dataOutput.writeInt(items.length);
        for (ItemStack item : items) {
            dataOutput.writeObject(item);
        }

        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static ItemStack[] itemStackArrayFromBase64(String base64) throws IOException, ClassNotFoundException {
        if (base64 == null || base64.isEmpty()) return new ItemStack[0];

        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        int size = dataInput.readInt();
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            items[i] = (ItemStack) dataInput.readObject();
        }

        dataInput.close();
        return items;
    }

    public static String itemStackToBase64(ItemStack item) throws IOException {
        if (item == null) return "";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        dataOutput.writeObject(item);
        dataOutput.close();

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static ItemStack itemStackFromBase64(String base64) throws IOException, ClassNotFoundException {
        if (base64 == null || base64.isEmpty()) return null;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }
}
