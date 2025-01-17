package www.legendarycommunity.com.br.legendary_inventory;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class Legendary_inventory extends JavaPlugin implements Listener {

    private Connection connection;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // Cria o config.yml se ele não existir
        Bukkit.getPluginManager().registerEvents(this, this);
        setupDatabase();
        getLogger().info("Legendary Inventory Plugin habilitado!");
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        getLogger().info("Legendary Inventory Plugin desabilitado!");
    }

    private void setupDatabase() {
        FileConfiguration config = getConfig();

        String url = config.getString("database.url", "jdbc:mysql://localhost:3306/minecraft?autoReconnect=true&useSSL=false");
        String user = config.getString("database.user", "root");
        String password = config.getString("database.password", "root");

        try {
            connection = DriverManager.getConnection(url, user, password);
            getLogger().info("Conexão com o banco de dados estabelecida com sucesso!");
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Não foi possível conectar ao banco de dados.");
        }
    }

    private void saveInventoryToDatabase(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return; // Não salva inventário se o jogador estiver no modo criativo
        }

        UUID uuid = player.getUniqueId();
        ItemStack[] inventoryItems = player.getInventory().getContents();
        ItemStack[] armorItems = player.getInventory().getArmorContents();

        StringBuilder inventoryBuilder = new StringBuilder();
        for (int i = 0; i < inventoryItems.length; i++) {
            if (i >= 36 && i <= 39) { // Slots de armadura são 36 a 39
                continue;
            }
            inventoryBuilder.append(inventoryItems[i] != null ? inventoryItems[i].serialize().toString() : "null").append(";");
        }

        StringBuilder armorBuilder = new StringBuilder();
        for (ItemStack item : armorItems) {
            armorBuilder.append(item != null ? item.serialize().toString() : "null").append(";");
        }

        try {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tickets_inventory_player (uuid, inventory, armor) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE inventory = VALUES(inventory), armor = VALUES(armor)"
            );
            statement.setString(1, uuid.toString());
            statement.setString(2, inventoryBuilder.toString());
            statement.setString(3, armorBuilder.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveInventoryToFile(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return; // Não salva inventário se o jogador estiver no modo criativo
        }

        UUID uuid = player.getUniqueId();
        String playerName = player.getName(); // Obtém o nick do jogador
        File folder = new File(getDataFolder(), "uuid");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File file = new File(folder, uuid + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set("nick", playerName);
        config.set("inventory", null);
        config.set("armor", null);

        ItemStack[] inventoryItems = player.getInventory().getContents();
        for (int i = 0; i < inventoryItems.length; i++) {
            if (inventoryItems[i] != null && inventoryItems[i].getType() != Material.AIR) {
                config.set("inventory.slot" + i, inventoryItems[i]);
            }
        }

        ItemStack[] armorItems = player.getInventory().getArmorContents();
        for (int i = 0; i < armorItems.length; i++) {
            if (armorItems[i] != null && armorItems[i].getType() != Material.AIR) {
                config.set("armor.slot" + i, armorItems[i]);
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveInventory(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return; // Não salva inventário se o jogador estiver no modo criativo
        }
        saveInventoryToFile(player);
        saveInventoryToDatabase(player);
    }

    @EventHandler
    public void onInventoryChange(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            saveInventory(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            saveInventory(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        saveInventory(player);
    }
}
