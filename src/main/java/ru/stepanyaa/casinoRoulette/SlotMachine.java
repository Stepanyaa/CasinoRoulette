package ru.stepanyaa.casinoRoulette;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class SlotMachine {
    private final CasinoRoulette plugin;
    private final Map<UUID, Integer> currentSlotBet = new HashMap<>();
    private final Map<UUID, Boolean> isSpinning = new HashMap<>();
    private final Random random = new Random();

    private final Map<Material, Integer> weights = new HashMap<>();

    public SlotMachine(CasinoRoulette plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    public void loadConfigValues() {
        weights.clear();
        if (plugin.getConfigManager().getConfig().isConfigurationSection("slots.weights")) {
            for (String key : plugin.getConfigManager().getConfig().getConfigurationSection("slots.weights").getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    weights.put(mat, plugin.getConfigManager().getConfig().getInt("slots.weights." + key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (weights.isEmpty()) {
            // Reduced win chances - more common symbols, fewer rare ones
            weights.put(Material.COAL, 40);      // Very common (40%)
            weights.put(Material.IRON_INGOT, 25); // Common (25%)
            weights.put(Material.GOLD_INGOT, 5);  // Rare (5%, was 8%)
            weights.put(Material.EMERALD, 3);   // Very rare (3%, was 5%)
            weights.put(Material.DIAMOND, 2);    // Extremely rare (2%, was 3%)
        }
    }

    public void openSlotMachine(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 27, cm.getMessage("gui.titles.slots", "Slot Machine"));
        ItemStack filler = plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int minBet = plugin.getMinBet();
        int current = currentSlotBet.getOrDefault(p.getUniqueId(), minBet);
        currentSlotBet.put(p.getUniqueId(), current);

        inv.setItem(9, createBetItem(current));
        inv.setItem(17, plugin.createItem(Material.LEVER, cm.getMessage("gui.slots.spin", "&6SPIN!")));
        for (int i = 10; i <= 16; i++) inv.setItem(i, plugin.createItem(Material.BARRIER, cm.getMessage("common.awaiting", "&7Awaiting...")));

        // Add player's current chips display
        int chips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
        inv.setItem(20, plugin.createItem(Material.GOLD_NUGGET, cm.getMessage("gui.roulette.your_chips", "&6Chips: %amount%", "amount", plugin.formatNumber(chips))));

        inv.setItem(26, plugin.createItem(Material.ARROW, cm.getMessage("common.back", "&cBack")));

        p.openInventory(inv);
    }

    private ItemStack createBetItem(int amount) {
        int minBet = plugin.getMinBet();
        int maxBet = plugin.getMaxBet();
        ConfigManager cm = plugin.getConfigManager();
        return plugin.createItem(Material.SUNFLOWER, cm.getMessage("common.bet_size", "&6Bet: %amount%", "amount", plugin.formatNumber(amount)),
                cm.getMessageList("common.bet_lore", Arrays.asList("&7Lore"), "min", minBet, "max", plugin.formatNumber(maxBet)));
    }

    public void handleClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        Inventory inv = e.getInventory();
        int minBet = plugin.getMinBet();
        int maxBet = plugin.getMaxBet();
        ConfigManager cm = plugin.getConfigManager();

        if (slot == 9) {
            int current = currentSlotBet.getOrDefault(p.getUniqueId(), minBet);
            int step = minBet >= 1000 ? 1000 : 10;
            if (e.getClick() == ClickType.RIGHT) current = Math.max(minBet, current - step);
            else if (e.getClick() == ClickType.LEFT) current = Math.min(maxBet, current + step);

            currentSlotBet.put(p.getUniqueId(), current);
            inv.setItem(9, createBetItem(current));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            return;
        }

        if (slot == 26) {
            p.closeInventory();
            plugin.getGui().openMainMenu(p);
            return;
        }

        if (slot != 17) return;
        if (Boolean.TRUE.equals(isSpinning.get(p.getUniqueId()))) return;

        int bet = currentSlotBet.getOrDefault(p.getUniqueId(), minBet);
        int chips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
        String prefix = cm.getMessage("messages.prefix", "&7[&6Casino&7] ");

        if (chips < bet) {
            p.sendMessage(prefix + cm.getMessage("messages.insufficient_funds", "&cNo funds!"));
            return;
        }

        plugin.getPlayerChips().put(p.getUniqueId(), chips - bet);
        isSpinning.put(p.getUniqueId(), true);

        inv.setItem(17, plugin.createItem(Material.REDSTONE_BLOCK, cm.getMessage("gui.slots.spinning", "&cSPINNING...")));

        new BukkitRunnable() {
            int spins = 0;

            @Override
            public void run() {
                spins++;
                List<Material> keys = new ArrayList<>(weights.keySet());
                for (int i = 10; i <= 16; i++) {
                    inv.setItem(i, plugin.createItem(keys.get(random.nextInt(keys.size())), " "));
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.6f + spins * 0.03f);

                if (spins >= 20) {
                    cancel();

                    Material[] rolledSymbols = new Material[7];
                    for (int i = 0; i < 7; i++) {
                        rolledSymbols[i] = getWeightedSymbol();
                        inv.setItem(10 + i, plugin.createItem(rolledSymbols[i], ChatColor.BOLD + rolledSymbols[i].name()));
                    }

                    Map<Material, Integer> counts = new HashMap<>();
                    for (Material m : rolledSymbols) counts.put(m, counts.getOrDefault(m, 0) + 1);

                    int maxMatches = 0;
                    Material winningMat = Material.COAL;
                    for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
                        if (entry.getValue() > maxMatches) {
                            maxMatches = entry.getValue();
                            winningMat = entry.getKey();
                        }
                    }

                    if (maxMatches >= 3) {
                        int baseMult = plugin.getConfigManager().getConfig().getInt("slots.multipliers." + winningMat.name(), plugin.getConfigManager().getConfig().getInt("slots.multipliers.DEFAULT", 5));
                        int progressiveFactor = (int) Math.pow(2, maxMatches - 3);
                        int finalPayout = bet * baseMult * progressiveFactor;

                        plugin.getPlayerChips().put(p.getUniqueId(), plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0) + finalPayout);
                        plugin.logGameResult(p.getUniqueId(), bet, finalPayout, true);

                        p.sendMessage(prefix + cm.getMessage("messages.slots.win", "&6WIN!", "matches", maxMatches, "symbol", winningMat.name(), "mult", (baseMult * progressiveFactor), "payout", plugin.formatNumber(finalPayout)));
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1f);
                    } else {
                        plugin.logGameResult(p.getUniqueId(), bet, 0, false);
                        p.sendMessage(prefix + cm.getMessage("messages.slots.loss", "&cNo match!"));
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }

                    inv.setItem(17, plugin.createItem(Material.LEVER, cm.getMessage("gui.slots.spin", "&6SPIN!")));
                    isSpinning.put(p.getUniqueId(), false);

                    // Update player's chips display after spin
                    int currentChips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
                    inv.setItem(20, plugin.createItem(Material.GOLD_NUGGET, cm.getMessage("gui.roulette.your_chips", "&6Chips: %amount%", "amount", plugin.formatNumber(currentChips))));
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Handles player disconnection by refunding any active bets
     * @param uuid The UUID of the player who disconnected
     */
    public void handlePlayerDisconnect(UUID uuid) {
        if (isSpinning.containsKey(uuid) && isSpinning.get(uuid)) {
            // Player was spinning when they disconnected - refund their bet
            int betAmount = currentSlotBet.getOrDefault(uuid, 0);
            if (betAmount > 0) {
                plugin.getPlayerChips().put(uuid, plugin.getPlayerChips().getOrDefault(uuid, 0) + betAmount);
                plugin.getLogger().info("Refunded " + betAmount + " chips to player " + uuid + " from Slot machine due to disconnection during spin");
            }
        }
        isSpinning.remove(uuid);
        currentSlotBet.remove(uuid);
    }

    private Material getWeightedSymbol() {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);
        int current = 0;
        for (Map.Entry<Material, Integer> e : weights.entrySet()) {
            current += e.getValue();
            if (roll < current) return e.getKey();
        }
        return Material.COAL;
    }
}