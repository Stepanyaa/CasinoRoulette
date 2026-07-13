/*
MIT License

Copyright (c) 2026 Stepanyaa

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package ru.stepanyaa.casinoRoulette;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class CrashGame {
    private final CasinoRoulette plugin;
    private final Map<UUID, Integer> playerBets = new HashMap<>();
    private final Map<UUID, Boolean> cashedOut = new HashMap<>();
    private final Map<UUID, Integer> currentBetAmount = new HashMap<>();
    private final Random random = new Random();

    private boolean gameRunning = false;
    private double currentMultiplier = 0.10;
    private double crashPoint = 0.0;

    public CrashGame(CasinoRoulette plugin) {
        this.plugin = plugin;
    }

    public void openCrashMenu(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 27, cm.getMessage("gui.titles.crash", "Crash Game"));
        ItemStack filler = plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int minBet = plugin.getMinBet();
        currentBetAmount.putIfAbsent(p.getUniqueId(), minBet);

        inv.setItem(11, createBetItem(p));
        inv.setItem(13, plugin.createItem(Material.SUNFLOWER, cm.getMessage("gui.crash.place_bet", "&ePLACE BET"),
                cm.getMessage("gui.crash.place_bet_lore", "&7Amount: %amount%", "amount", plugin.formatNumber(currentBetAmount.get(p.getUniqueId())))));
        inv.setItem(4, plugin.createItem(Material.NETHER_STAR, cm.getMessage("gui.crash.multiplier", "&6x%multiplier%", "multiplier", "0.10")));
        inv.setItem(22, plugin.createItem(Material.BARRIER, cm.getMessage("gui.crash.awaiting", "&7Awaiting bet...")));
        inv.setItem(26, plugin.createItem(Material.ARROW, cm.getMessage("common.back", "&cBack")));

        int chips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
        inv.setItem(20, plugin.createItem(Material.GOLD_NUGGET, cm.getMessage("gui.roulette.your_chips", "&6Chips: %amount%", "amount", plugin.formatNumber(chips))));

        p.openInventory(inv);
    }

    private ItemStack createBetItem(Player p) {
        int amount = currentBetAmount.getOrDefault(p.getUniqueId(), plugin.getMinBet());
        int minBet = plugin.getMinBet();
        int maxBet = plugin.getMaxBet();
        ConfigManager cm = plugin.getConfigManager();
        return plugin.createItem(Material.GOLD_INGOT, cm.getMessage("common.bet_size", "&6Bet: %amount%", "amount", plugin.formatNumber(amount)),
                cm.getMessageList("common.bet_lore", Arrays.asList("&7Lore"), "min", minBet, "max", plugin.formatNumber(maxBet)));
    }

    public void handleClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        Inventory inv = e.getInventory();
        int minBet = plugin.getMinBet();
        int maxBet = plugin.getMaxBet();
        ConfigManager cm = plugin.getConfigManager();
        String prefix = cm.getMessage("messages.prefix", "&7[&6Casino&7] ");

        if (slot == 11) {
            if (gameRunning) {
                p.sendMessage(prefix + cm.getMessage("messages.crash.no_change", "&cCannot change bet now!"));
                return;
            }
            int current = currentBetAmount.getOrDefault(p.getUniqueId(), minBet);
            int step = minBet >= 1000 ? 1000 : 10;
            if (e.getClick() == ClickType.LEFT) current = Math.min(maxBet, current + step);
            else if (e.getClick() == ClickType.RIGHT) current = Math.max(minBet, current - step);

            currentBetAmount.put(p.getUniqueId(), current);
            inv.setItem(11, createBetItem(p));
            inv.setItem(13, plugin.createItem(Material.SUNFLOWER, cm.getMessage("gui.crash.place_bet", "&ePLACE BET"),
                    cm.getMessage("gui.crash.place_bet_lore", "&7Amount: %amount%", "amount", plugin.formatNumber(current))));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            return;
        }

        if (slot == 13) {
            if (gameRunning) {
                p.sendMessage(prefix + cm.getMessage("messages.crash.started", "&cRound already started!"));
                return;
            }
            int bet = currentBetAmount.getOrDefault(p.getUniqueId(), minBet);
            int chips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
            if (chips < bet) {
                p.sendMessage(prefix + cm.getMessage("messages.insufficient_funds", "&cInsufficient funds!"));
                return;
            }

            plugin.getPlayerChips().put(p.getUniqueId(), chips - bet);
            playerBets.put(p.getUniqueId(), bet);
            cashedOut.put(p.getUniqueId(), false);

            p.sendMessage(prefix + cm.getMessage("messages.crash.locked", "&aBet %bet% locked.", "bet", plugin.formatNumber(bet)));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

            if (!gameRunning) startNewRound();
            updateAllInventories();
            return;
        }

        if (slot == 22 && gameRunning && playerBets.containsKey(p.getUniqueId()) && !cashedOut.get(p.getUniqueId())) {
            double multiplier = currentMultiplier;
            int bet = playerBets.get(p.getUniqueId());
            int win = (int) (bet * multiplier);

            plugin.getPlayerChips().put(p.getUniqueId(), plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0) + win);
            plugin.logGameResult(p.getUniqueId(), bet, win, true);
            cashedOut.put(p.getUniqueId(), true);

            p.sendMessage(prefix + cm.getMessage("messages.crash.cashed_out", "&6CASHED OUT! x%mult% → +%win%", "mult", String.format("%.2f", multiplier), "win", plugin.formatNumber(win)));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1f);
            updateAllInventories();
        }

        if (slot == 26) {
            p.closeInventory();
            plugin.getGui().openMainMenu(p);
        }
    }

    private void startNewRound() {
        gameRunning = true;
        currentMultiplier = plugin.getConfigManager().getConfig().getDouble("crash.start_multiplier", 0.10);
        crashPoint = generateCrashPoint();
        long speed = plugin.getConfigManager().getConfig().getLong("crash.tick_speed", 2L);
        double step = plugin.getConfigManager().getConfig().getDouble("crash.increment", 0.01);
        ConfigManager cm = plugin.getConfigManager();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) { cancel(); return; }
                currentMultiplier += step;

                if (currentMultiplier >= crashPoint) {
                    gameRunning = false;
                    String crashedMsg = cm.getMessage("messages.crash.exploded", "&cCRASHED AT x%mult%!", "mult", String.format("%.2f", crashPoint));
                    String prefix = cm.getMessage("messages.prefix", "&7[&6Casino&7] ");
                    Bukkit.getOnlinePlayers().forEach(player -> {
                        UUID uuid = player.getUniqueId();
                        if (playerBets.containsKey(uuid) && !cashedOut.getOrDefault(uuid, false)) {
                            plugin.logGameResult(uuid, playerBets.get(uuid), 0, false);
                            player.sendMessage(prefix + crashedMsg);
                            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
                        }
                        if (ChatColor.stripColor(player.getOpenInventory().getTitle()).equals(ChatColor.stripColor(cm.getMessage("gui.titles.crash", "Crash Game")))) {
                            updateInventory(player);
                        }
                    });
                    playerBets.clear();
                    cashedOut.clear();
                    cancel();
                } else {
                    updateAllInventories();
                }
            }
        }.runTaskTimer(plugin, 0L, speed);
    }

    private double generateCrashPoint() {
        double r = random.nextDouble();
        double instantChance = plugin.getConfigManager().getConfig().getDouble("crash.generation.instant_crash_chance", 0.30);
        double lowChance = plugin.getConfigManager().getConfig().getDouble("crash.generation.low_crash_chance", 0.60);

        if (r < instantChance) return 1.00;
        if (r < lowChance) return 0.50 + random.nextDouble() * 0.60;
        return 1.50 + random.nextDouble() * 8.50;
    }

    /**
     * Handles player disconnection by refunding any active bets
     * @param uuid The UUID of the player who disconnected
     */
    public void handlePlayerDisconnect(UUID uuid) {
        if (playerBets.containsKey(uuid)) {
            int betAmount = playerBets.remove(uuid);
            plugin.getPlayerChips().put(uuid, plugin.getPlayerChips().getOrDefault(uuid, 0) + betAmount);
            plugin.getLogger().info("Refunded " + betAmount + " chips to player " + uuid + " from Crash game due to disconnection");
        }
        cashedOut.remove(uuid);
        currentBetAmount.remove(uuid);
    }

    private void updateInventory(Player p) {
        Inventory inv = p.getOpenInventory().getTopInventory();
        ConfigManager cm = plugin.getConfigManager();
        if (inv == null || !ChatColor.stripColor(p.getOpenInventory().getTitle()).equals(ChatColor.stripColor(cm.getMessage("gui.titles.crash", "Crash Game")))) return;

        ItemStack multItem = plugin.createItem(
                gameRunning ? Material.NETHER_STAR : Material.BARRIER,
                gameRunning ? cm.getMessage("gui.crash.multiplier", "&6x%multiplier%", "multiplier", String.format("%.2f", currentMultiplier))
                        : cm.getMessage("gui.crash.crashed", "&cCrashed at x%multiplier%", "multiplier", String.format("%.2f", crashPoint))
        );

        boolean hasBet = playerBets.containsKey(p.getUniqueId());
        boolean canCashout = gameRunning && hasBet && !cashedOut.getOrDefault(p.getUniqueId(), false);

        String buttonName = canCashout ? cm.getMessage("gui.crash.cashout", "&aCASH OUT", "multiplier", String.format("%.2f", currentMultiplier))
                : hasBet ? cm.getMessage("gui.crash.already_claimed", "&cAlready Cashed Out") : cm.getMessage("gui.crash.no_bet", "&7No Bet");

        String buttonLore = canCashout ? cm.getMessage("gui.crash.cashout_lore", "&ePayout: %payout%", "payout", plugin.formatNumber((int)(playerBets.get(p.getUniqueId()) * currentMultiplier)))
                : cm.getMessage("gui.crash.click_secure", "&8Click");

        ItemStack cashoutItem = plugin.createItem(
                canCashout ? Material.EMERALD_BLOCK : Material.BARRIER,
                buttonName,
                buttonLore
        );

        int currentChips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
        inv.setItem(20, plugin.createItem(Material.GOLD_NUGGET, cm.getMessage("gui.roulette.your_chips", "&6Chips: %amount%", "amount", plugin.formatNumber(currentChips))));

        inv.setItem(4, multItem);
        inv.setItem(22, cashoutItem);
    }

    private void updateAllInventories() {
        ConfigManager cm = plugin.getConfigManager();
        String expectedTitle = ChatColor.stripColor(cm.getMessage("gui.titles.crash", "Crash Game"));
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (ChatColor.stripColor(p.getOpenInventory().getTitle()).equals(expectedTitle)) updateInventory(p);
        });
    }
}