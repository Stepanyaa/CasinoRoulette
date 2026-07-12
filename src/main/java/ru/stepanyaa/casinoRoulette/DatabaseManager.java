package ru.stepanyaa.casinoRoulette;

import org.bukkit.Bukkit;
import java.io.File;
import java.sql.*;
import java.util.UUID;

public class DatabaseManager {
    private final CasinoRoulette plugin;
    private Connection connection;

    public DatabaseManager(CasinoRoulette plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        try {
            File file = new File(plugin.getDataFolder(), "database.db");
            if (!file.exists()) file.createNewFile();
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                        "uuid VARCHAR(36) PRIMARY KEY, chips INT, wins INT, " +
                        "losses INT, total_won BIGINT, total_lost BIGINT, rounds INT);");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void savePlayer(UUID uuid, int chips, int wins, int losses, int totalWon, int totalLost, int rounds) {
        // Check if plugin is enabled before scheduling async task
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String sql = "REPLACE INTO player_data VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, chips);
                    ps.setInt(3, wins);
                    ps.setInt(4, losses);
                    ps.setInt(5, totalWon);
                    ps.setInt(6, totalLost);
                    ps.setInt(7, rounds);
                    ps.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            });
        } else {
            // Plugin is disabling, execute synchronously
            try {
                String sql = "REPLACE INTO player_data VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, chips);
                    ps.setInt(3, wins);
                    ps.setInt(4, losses);
                    ps.setInt(5, totalWon);
                    ps.setInt(6, totalLost);
                    ps.setInt(7, rounds);
                    ps.executeUpdate();
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public void loadPlayer(UUID uuid, CasinoRoulette plugin) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT * FROM player_data WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    plugin.getPlayerChips().put(uuid, rs.getInt("chips"));
                    plugin.getWins().put(uuid, rs.getInt("wins"));
                    plugin.getLosses().put(uuid, rs.getInt("losses"));
                    plugin.getTotalWon().put(uuid, rs.getInt("total_won"));
                    plugin.getTotalLost().put(uuid, rs.getInt("total_lost"));
                    plugin.getTotalRounds().put(uuid, rs.getInt("rounds"));
                }
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }
}