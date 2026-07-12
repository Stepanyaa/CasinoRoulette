package ru.stepanyaa.casinoRoulette;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.*;

public class RouletteGUI {
    private final CasinoRoulette plugin;

    public RouletteGUI(CasinoRoulette plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 54, cm.getMessage("gui.titles.main_menu", "Casino: Main Menu"));
        ItemStack filler = plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(22, plugin.createItem(Material.DIAMOND, cm.getMessage("gui.main.play_roulette", "&a&lPLAY ROULETTE")));
        inv.setItem(29, plugin.createItem(Material.TNT, cm.getMessage("gui.main.slots", "&c&lSLOTS")));
        inv.setItem(33, plugin.createItem(Material.FIRE_CHARGE, cm.getMessage("gui.main.crash", "&c&lCRASH")));
        inv.setItem(53, plugin.createItem(Material.BOOK, cm.getMessage("gui.main.stats", "&bStats")));
        inv.setItem(49, plugin.createItem(Material.GOLD_INGOT, cm.getMessage("gui.main.exchange", "&eExchange")));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(p);
        meta.setDisplayName(cm.getMessage("gui.main.profile_name", "&6Profile"));
        meta.setLore(cm.getMessageList("gui.main.profile_lore", Arrays.asList("&eChips: %chips%", "&eBalance: %balance%"),
                "chips", plugin.formatNumber(plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0)),
                "balance", plugin.formatBalance(p)));
        head.setItemMeta(meta);
        inv.setItem(45, head);

        p.openInventory(inv);
    }

    public void openExchangeMenu(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 27, cm.getMessage("gui.titles.exchange", "Casino: Exchange"));
        ItemStack filler = plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        String currency = plugin.getCurrencyName();

        // Add player's current chips display
        int chips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
        inv.setItem(13, plugin.createItem(Material.GOLD_NUGGET, cm.getMessage("gui.roulette.your_chips", "&6Chips: %amount%", "amount", plugin.formatNumber(chips))));

        inv.setItem(11, plugin.createItem(Material.EMERALD_BLOCK, cm.getMessage("gui.exchange.buy_btn", "&a&lBUY"), cm.getMessage("gui.exchange.buy_lore", "&7Exchange %currency% for chips", "currency", currency)));
        inv.setItem(15, plugin.createItem(Material.REDSTONE_BLOCK, cm.getMessage("gui.exchange.sell_btn", "&c&lSELL"), cm.getMessage("gui.exchange.sell_lore", "&7Convert chips to %currency%", "currency", currency)));
        inv.setItem(26, plugin.createItem(Material.ARROW, cm.getMessage("common.back", "&cBack")));
        p.openInventory(inv);
    }

    public void openBuyChipsMenu(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 27, cm.getMessage("gui.titles.buy_chips", "Casino: Buy Chips"));
        ItemStack filler = plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        double tax = plugin.getConfigManager().getConfig().getDouble("settings.commission_percent", 10.0) / 100.0;
        int minEx = plugin.getMinExchange();
        String currency = plugin.getCurrencyName();

        int p1 = Math.max(10, minEx * 1);
        int p2 = Math.max(100, minEx * 10);
        int p3 = Math.max(1000, minEx * 50);

        inv.setItem(10, plugin.createItem(Material.GOLD_NUGGET,
                cm.getMessage("gui.buy.item_name", "&aBuy %amount%", "amount", String.valueOf(p1)),
                cm.getMessage("gui.buy.price", "&7Price: %price% %currency%", "price", p1, "currency", currency),
                cm.getMessage("gui.buy.receive", "&eReceive: %chips% chips", "chips", (int)(p1 * (1 - tax)))));

        inv.setItem(11, plugin.createItem(Material.GOLD_INGOT,
                cm.getMessage("gui.buy.item_name", "&aBuy %amount%", "amount", String.valueOf(p2)),
                cm.getMessage("gui.buy.price", "&7Price: %price% %currency%", "price", p2, "currency", currency),
                cm.getMessage("gui.buy.receive", "&eReceive: %chips% chips", "chips", (int)(p2 * (1 - tax)))));

        inv.setItem(12, plugin.createItem(Material.GOLD_BLOCK,
                cm.getMessage("gui.buy.item_name", "&aBuy %amount%", "amount", String.valueOf(p3)),
                cm.getMessage("gui.buy.price", "&7Price: %price% %currency%", "price", p3, "currency", currency),
                cm.getMessage("gui.buy.receive", "&eReceive: %chips% chips", "chips", (int)(p3 * (1 - tax)))));

        inv.setItem(13, plugin.createItem(Material.WRITABLE_BOOK,
                cm.getMessage("gui.buy.custom", "&aCustom"),
                cm.getMessage("gui.buy.custom_lore", "&7Lore"),
                cm.getMessage("gui.exchange.commission", "&6Fee: %fee%", "fee", plugin.getConfigManager().getConfig().getDouble("settings.commission_percent"))));

        // Add player's current chips display
        int currentChips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
        inv.setItem(20, plugin.createItem(Material.GOLD_NUGGET, cm.getMessage("gui.roulette.your_chips", "&6Chips: %amount%", "amount", plugin.formatNumber(currentChips))));

        inv.setItem(26, plugin.createItem(Material.ARROW, cm.getMessage("common.back", "&cBack")));
        p.openInventory(inv);
    }

    public void openSellChipsMenu(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 27, cm.getMessage("gui.titles.sell_chips", "Casino: Sell Chips"));
        ItemStack filler = plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        double tax = plugin.getConfigManager().getConfig().getDouble("settings.commission_percent", 10.0) / 100.0;
        int minEx = plugin.getMinExchange();
        String currency = plugin.getCurrencyName();

        int p1 = Math.max(10, minEx * 1);
        int p2 = Math.max(100, minEx * 10);
        int p3 = Math.max(1000, minEx * 50);

        inv.setItem(10, plugin.createItem(Material.IRON_NUGGET,
                cm.getMessage("gui.sell.item_name", "&cSell %amount%", "amount", String.valueOf(p1)),
                cm.getMessage("gui.sell.price", "&7Price: %price% chips", "price", p1),
                cm.getMessage("gui.sell.receive", "&eReceive: %coins% %currency%", "coins", (int)(p1 * (1 - tax)), "currency", currency)));

        inv.setItem(11, plugin.createItem(Material.IRON_INGOT,
                cm.getMessage("gui.sell.item_name", "&cSell %amount%", "amount", String.valueOf(p2)),
                cm.getMessage("gui.sell.price", "&7Price: %price% chips", "price", p2),
                cm.getMessage("gui.sell.receive", "&eReceive: %coins% %currency%", "coins", (int)(p2 * (1 - tax)), "currency", currency)));

        inv.setItem(12, plugin.createItem(Material.IRON_BLOCK,
                cm.getMessage("gui.sell.item_name", "&cSell %amount%", "amount", String.valueOf(p3)),
                cm.getMessage("gui.sell.price", "&7Price: %price% chips", "price", p3),
                cm.getMessage("gui.sell.receive", "&eReceive: %coins% %currency%", "coins", (int)(p3 * (1 - tax)), "currency", currency)));

        inv.setItem(13, plugin.createItem(Material.WRITABLE_BOOK,
                cm.getMessage("gui.sell.custom", "&cCustom"),
                cm.getMessage("gui.sell.custom_lore", "&7Lore"),
                cm.getMessage("gui.exchange.commission", "&6Fee: %fee%", "fee", plugin.getConfigManager().getConfig().getDouble("settings.commission_percent"))));

        // Add player's current chips display
        int currentChips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);
        inv.setItem(20, plugin.createItem(Material.GOLD_NUGGET, cm.getMessage("gui.roulette.your_chips", "&6Chips: %amount%", "amount", plugin.formatNumber(currentChips))));

        inv.setItem(26, plugin.createItem(Material.ARROW, cm.getMessage("common.back", "&cBack")));
        p.openInventory(inv);
    }

    public void handleExchangeMenuClick(Player p, InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot == 11) openBuyChipsMenu(p);
        else if (slot == 15) openSellChipsMenu(p);
        else if (slot == 26) openMainMenu(p);
    }

    public void handleBuyMenuClick(Player p, InventoryClickEvent e) {
        int slot = e.getSlot();
        UUID uuid = p.getUniqueId();
        double tax = plugin.getConfigManager().getConfig().getDouble("settings.commission_percent", 10.0) / 100.0;
        ConfigManager cm = plugin.getConfigManager();

        if (slot == 26) { openExchangeMenu(p); return; }

        if (slot == 10 || slot == 11 || slot == 12) {
            int minEx = plugin.getMinExchange();
            int cost = (slot == 10) ? Math.max(10, minEx * 1) : (slot == 11) ? Math.max(100, minEx * 10) : Math.max(1000, minEx * 50);
            int chipsGain = (int) (cost * (1 - tax));

            if (plugin.hasEnoughBalance(p, cost)) {
                plugin.withdrawBalance(p, cost);
                plugin.getPlayerChips().put(uuid, plugin.getPlayerChips().getOrDefault(uuid, 0) + chipsGain);
                p.sendMessage(cm.getMessage("messages.prefix", "&7[&6Casino&7] ") + cm.getMessage("messages.exchange.buy_success", "&aPurchased", "chips", chipsGain, "coins", cost));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                openBuyChipsMenu(p);
            } else p.sendMessage(cm.getMessage("messages.prefix", "&7[&6Casino&7] ") + cm.getMessage("messages.insufficient_funds", "&cNo funds!"));
        } else if (slot == 13) {
            p.closeInventory();
            plugin.getAwaitingInput().put(uuid, "buy");
            int currentChips = plugin.getPlayerChips().getOrDefault(uuid, 0);
            String currency = plugin.getCurrencyName();
            p.sendMessage(cm.getMessage("messages.prefix", "&7[&6Casino&7] ") +
                         cm.getMessage("messages.enter_buy_enhanced", "&eEnter amount of %currency% to buy chips:", "currency", currency));
            p.sendMessage(cm.getMessage("messages.current_chips", "&7(You have: %chips% chips)", "chips", plugin.formatNumber(currentChips)) +
                         " " + cm.getMessage("messages.cancel_hint", "&7(Type 'cancel' to abort)"));
        }
    }

    public void handleSellMenuClick(Player p, InventoryClickEvent e) {
        int slot = e.getSlot();
        UUID uuid = p.getUniqueId();
        double tax = plugin.getConfigManager().getConfig().getDouble("settings.commission_percent", 10.0) / 100.0;
        ConfigManager cm = plugin.getConfigManager();

        if (slot == 26) { openExchangeMenu(p); return; }

        if (slot == 10 || slot == 11 || slot == 12) {
            int minEx = plugin.getMinExchange();
            int chipsCost = (slot == 10) ? Math.max(10, minEx * 1) : (slot == 11) ? Math.max(100, minEx * 10) : Math.max(1000, minEx * 50);
            int monetGain = (int) (chipsCost * (1 - tax));

            if (plugin.getPlayerChips().getOrDefault(uuid, 0) >= chipsCost) {
                plugin.getPlayerChips().put(uuid, plugin.getPlayerChips().get(uuid) - chipsCost);
                plugin.depositBalance(p, monetGain);
                p.sendMessage(cm.getMessage("messages.prefix", "&7[&6Casino&7] ") + cm.getMessage("messages.exchange.sell_success", "&aSold", "chips", chipsCost, "coins", monetGain));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                openSellChipsMenu(p);
            } else p.sendMessage(cm.getMessage("messages.prefix", "&7[&6Casino&7] ") + cm.getMessage("messages.no_chips", "&cNo chips!"));
        } else if (slot == 13) {
            p.closeInventory();
            plugin.getAwaitingInput().put(uuid, "sell");
            int currentChips = plugin.getPlayerChips().getOrDefault(uuid, 0);
            String currency = plugin.getCurrencyName();
            p.sendMessage(cm.getMessage("messages.prefix", "&7[&6Casino&7] ") +
                         cm.getMessage("messages.enter_sell_enhanced", "&eEnter amount of chips to sell for %currency%:", "currency", currency));
            p.sendMessage(cm.getMessage("messages.current_chips", "&7(You have: %chips% chips)", "chips", plugin.formatNumber(currentChips)) +
                         " " + cm.getMessage("messages.cancel_hint", "&7(Type 'cancel' to abort)"));
        }
    }

    public void openGameMenu(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 54, cm.getMessage("gui.titles.roulette", "Roulette Room"));
        plugin.getActiveGameInventories().put(p.getUniqueId(), inv);
        plugin.getActiveBets().putIfAbsent(p.getUniqueId(), new HashMap<>());
        plugin.getCurrentBetAmount().putIfAbsent(p.getUniqueId(), plugin.getMinBet());
        plugin.getPlayersInGame().add(p.getUniqueId());

        updateGameInventory(p, inv);
        p.openInventory(inv);
        plugin.checkGameLoop();
    }

    public void updateGameInventory(Player p, Inventory inv) {
        UUID uuid = p.getUniqueId();
        Map<String, Integer> bets = plugin.getActiveBets().getOrDefault(uuid, new HashMap<>());
        int amount = plugin.getCurrentBetAmount().getOrDefault(uuid, plugin.getMinBet());
        int minBetLocal = plugin.getMinBet();
        ConfigManager cm = plugin.getConfigManager();

        for (int i = 0; i < 54; i++) inv.setItem(i, plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " "));

        if (plugin.getGameState() == CasinoRoulette.GameState.WAITING) {
            inv.setItem(4, plugin.createItem(Material.CLOCK,
                    cm.getMessage("gui.roulette.accepting", "&6Accepting Bets..."),
                    cm.getMessage("gui.roulette.spinning_in", "&eSpinning in: %time%s", "time", plugin.getTimer())));
        } else {
            inv.setItem(4, plugin.createItem(Material.REDSTONE_TORCH,
                    cm.getMessage("gui.roulette.in_progress", "&cSPIN IN PROGRESS")));
        }

        int[] numbers = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36};
        int slot = 9;
        for (int num : numbers) {
            boolean isRed = plugin.isNumRed(num);
            Material mat = isRed ? Material.RED_WOOL : Material.BLACK_WOOL;
            ChatColor color = isRed ? ChatColor.RED : ChatColor.DARK_GRAY;

            String loreText = bets.containsKey(String.valueOf(num))
                    ? cm.getMessage("gui.roulette.bet_placed", "&eBet: %amount%", "amount", plugin.formatNumber(bets.get(String.valueOf(num))))
                    : cm.getMessage("gui.roulette.click_to_bet", "&7Click — bet %amount%", "amount", plugin.formatNumber(amount));

            ItemStack item = plugin.createItem(mat, color + "" + ChatColor.BOLD + num, loreText);
            if (bets.containsKey(String.valueOf(num))) plugin.addGlow(item);
            inv.setItem(slot++, item);
        }

        ItemStack redItem = plugin.createItem(Material.RED_WOOL,
                cm.getMessage("gui.roulette.red", "&cRed"),
                bets.containsKey("red") ? cm.getMessage("gui.roulette.bet_placed", "&eBet: %amount%", "amount", plugin.formatNumber(bets.get("red")))
                        : cm.getMessage("gui.roulette.click_to_bet_no_amt", "&7Click to bet"));
        if (bets.containsKey("red")) plugin.addGlow(redItem);
        inv.setItem(45, redItem);

        ItemStack blackItem = plugin.createItem(Material.BLACK_WOOL,
                cm.getMessage("gui.roulette.black", "&8Black"),
                bets.containsKey("black") ? cm.getMessage("gui.roulette.bet_placed", "&eBet: %amount%", "amount", plugin.formatNumber(bets.get("black")))
                        : cm.getMessage("gui.roulette.click_to_bet_no_amt", "&7Click to bet"));
        if (bets.containsKey("black")) plugin.addGlow(blackItem);
        inv.setItem(46, blackItem);

        ItemStack s1 = plugin.createItem(Material.EMERALD_BLOCK,
                cm.getMessage("gui.roulette.doz1", "&a1st Dozen"),
                bets.containsKey("1st12") ? cm.getMessage("gui.roulette.bet_placed", "&eBet: %amount%", "amount", plugin.formatNumber(bets.get("1st12")))
                        : cm.getMessage("gui.roulette.click_to_bet_no_amt", "&7Click to bet"));
        if (bets.containsKey("1st12")) plugin.addGlow(s1);
        inv.setItem(47, s1);

        ItemStack s2 = plugin.createItem(Material.DIAMOND_BLOCK,
                cm.getMessage("gui.roulette.doz2", "&b2nd Dozen"),
                bets.containsKey("2nd12") ? cm.getMessage("gui.roulette.bet_placed", "&eBet: %amount%", "amount", plugin.formatNumber(bets.get("2nd12")))
                        : cm.getMessage("gui.roulette.click_to_bet_no_amt", "&7Click to bet"));
        if (bets.containsKey("2nd12")) plugin.addGlow(s2);
        inv.setItem(48, s2);

        ItemStack s3 = plugin.createItem(Material.GOLD_BLOCK,
                cm.getMessage("gui.roulette.doz3", "&63rd Dozen"),
                bets.containsKey("3rd12") ? cm.getMessage("gui.roulette.bet_placed", "&eBet: %amount%", "amount", plugin.formatNumber(bets.get("3rd12")))
                        : cm.getMessage("gui.roulette.click_to_bet_no_amt", "&7Click to bet"));
        if (bets.containsKey("3rd12")) plugin.addGlow(s3);
        inv.setItem(49, s3);

        inv.setItem(50, plugin.createItem(Material.SUNFLOWER,
                cm.getMessage("common.bet_size", "&6Bet: %amount%", "amount", plugin.formatNumber(amount)),
                cm.getMessageList("common.bet_lore_shift", Arrays.asList(
                        "&7LMB/RMB +/- " + (minBetLocal >= 1000 ? "1000" : "10"),
                        "&7Shift +/- " + (minBetLocal >= 1000 ? "10000" : "100")
                ))));

        inv.setItem(51, plugin.createItem(Material.GOLD_NUGGET,
                cm.getMessage("gui.roulette.your_chips", "&6Chips: %amount%", "amount", plugin.formatNumber(plugin.getPlayerChips().getOrDefault(uuid, 0)))));

        StringBuilder h = new StringBuilder();
        for (Integer n : plugin.getHistory()) {
            h.append(plugin.isNumRed(n) ? ChatColor.RED : ChatColor.DARK_GRAY).append(n).append(" ");
        }
        inv.setItem(52, plugin.createItem(Material.PAPER,
                cm.getMessage("gui.roulette.history", "&bHistory: %history%", "history", h.toString().trim())));

        inv.setItem(53, plugin.createItem(Material.ARROW, cm.getMessage("common.back", "&cBack")));
    }

    public void handleGameClick(Player p, InventoryClickEvent e) {
        ConfigManager cm = plugin.getConfigManager();
        String prefix = cm.getMessage("messages.prefix", "&7[&6Casino&7] ");

        if (plugin.getGameState() != CasinoRoulette.GameState.WAITING) {
            p.sendMessage(prefix + cm.getMessage("messages.roulette.closed", "&cBets closed!"));
            return;
        }

        int slot = e.getRawSlot();
        if (slot == 53) {
            p.closeInventory();
            openMainMenu(p);
            return;
        }

        int minBet = plugin.getMinBet();
        int maxBet = plugin.getMaxBet();

        if (slot == 50) {
            int current = plugin.getCurrentBetAmount().getOrDefault(p.getUniqueId(), minBet);
            int step = minBet >= 1000 ? 1000 : 10;
            int change = e.isLeftClick() ? step : -step;
            if (e.isShiftClick()) change *= 10;

            int newAmount = current + change;
            if (newAmount > maxBet) newAmount = maxBet;
            if (newAmount < minBet) newAmount = minBet;

            plugin.getCurrentBetAmount().put(p.getUniqueId(), newAmount);
            updateGameInventory(p, e.getInventory());
            return;
        }

        String betKey = null;
        if (slot >= 9 && slot <= 44) {
            ItemStack item = e.getCurrentItem();
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                betKey = ChatColor.stripColor(item.getItemMeta().getDisplayName()).replaceAll("[^0-9]", "");
            }
        } else if (slot == 45) betKey = "red";
        else if (slot == 46) betKey = "black";
        else if (slot == 47) betKey = "1st12";
        else if (slot == 48) betKey = "2nd12";
        else if (slot == 49) betKey = "3rd12";

        if (betKey == null) return;

        Map<String, Integer> bets = plugin.getActiveBets().getOrDefault(p.getUniqueId(), new HashMap<>());
        int amount = plugin.getCurrentBetAmount().getOrDefault(p.getUniqueId(), minBet);
        int chips = plugin.getPlayerChips().getOrDefault(p.getUniqueId(), 0);

        if (bets.containsKey(betKey)) {
            int refunded = bets.remove(betKey);
            plugin.getPlayerChips().put(p.getUniqueId(), chips + refunded);
            if (bets.isEmpty()) plugin.getActiveBets().remove(p.getUniqueId());
            p.sendMessage(prefix + cm.getMessage("messages.roulette.bet_removed", "&eBet removed."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        } else {
            if ((betKey.equals("red") && bets.containsKey("black")) || (betKey.equals("black") && bets.containsKey("red"))) {
                p.sendMessage(prefix + cm.getMessage("messages.roulette.opposing", "&cCannot bet on opposing colors!"));
                return;
            }
            if (betKey.matches("1st12|2nd12|3rd12") && bets.keySet().stream().anyMatch(k -> k.matches("1st12|2nd12|3rd12"))) {
                p.sendMessage(prefix + cm.getMessage("messages.roulette.dozen", "&cYou can only choose one dozen!"));
                return;
            }

            int maxSelections = plugin.getPlayerSelectionLimit(p);
            if (bets.size() >= maxSelections) {
                p.sendMessage(prefix + cm.getMessage("messages.max_selection", "&cBet limit reached: %limit%!", "limit", maxSelections));
                return;
            }
            if (chips < amount) {
                p.sendMessage(prefix + cm.getMessage("messages.insufficient_funds", "&cInsufficient funds!"));
                return;
            }

            plugin.getPlayerChips().put(p.getUniqueId(), chips - amount);
            bets.put(betKey, amount);
            plugin.getActiveBets().put(p.getUniqueId(), bets);
            p.sendMessage(prefix + cm.getMessage("messages.roulette.bet_placed", "&aPlaced %amount% on %bet%", "amount", plugin.formatNumber(amount), "bet", betKey));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
        updateGameInventory(p, e.getInventory());
    }

    public void openDetailedStats(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 27, cm.getMessage("gui.titles.stats", "Detailed Statistics"));
        UUID uuid = p.getUniqueId();

        ItemStack filler = plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(13, plugin.createItem(Material.BOOK, cm.getMessage("gui.stats.title_item", "&6Statistics"),
                cm.getMessage("gui.stats.wins", "&eWins: %amount%", "amount", plugin.getWins().getOrDefault(uuid, 0)),
                cm.getMessage("gui.stats.losses", "&eLosses: %amount%", "amount", plugin.getLosses().getOrDefault(uuid, 0)),
                cm.getMessage("gui.stats.rounds", "&eRounds Played: %amount%", "amount", plugin.getTotalRounds().getOrDefault(uuid, 0)),
                cm.getMessage("gui.stats.won", "&aTotal Won: %amount% chips", "amount", plugin.formatNumber(plugin.getTotalWon().getOrDefault(uuid, 0))),
                cm.getMessage("gui.stats.lost", "&cTotal Lost: %amount% chips", "amount", plugin.formatNumber(plugin.getTotalLost().getOrDefault(uuid, 0)))));

        inv.setItem(26, plugin.createItem(Material.ARROW, cm.getMessage("common.back", "&cBack")));
        p.openInventory(inv);
    }
}