
package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.PlayerStats;
import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class RouletteGUI {

    public static final String GUI_MAIN = "casino:menu";
    public static final String GUI_EXCHANGE = "casino:exchange";
    public static final String GUI_BUY = "casino:buy";
    public static final String GUI_SELL = "casino:sell";
    public static final String GUI_TABLE = "casino:roulette";
    public static final String GUI_STATS = "casino:stats";

    private static final String[][] MAIN_MENU_BUTTONS = {
            {"roulette", "roulette-slot", "20"},
            {"wheel", "fortune-wheel-slot", "22"},
            {"daily", "daily-casino-slot", "24"},
            {"slots", "slots-slot", "29"},
            {"leaderboards", "leaderboards-slot", "31"},
            {"crash", "crash-slot", "33"},
            {"profile", "profile-slot", "45"},
            {"exchange", "exchange-slot", "49"},
            {"stats", "stats-slot", "53"},
    };

    private static final Set<Integer> RED_NUMBERS = new java.util.HashSet<>(Arrays.asList(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36));

    private static final int FIRST_NUMBER_SLOT = 9;
    private static final int LAST_NUMBER_SLOT = 44;
    private static final int RED_SLOT = 45;
    private static final int BLACK_SLOT = 46;
    private static final int DOZEN1_SLOT = 47;
    private static final int DOZEN2_SLOT = 48;
    private static final int DOZEN3_SLOT = 49;
    private static final int TABLE_BET_SLOT = 50;
    private static final int TABLE_CHIPS_SLOT = 51;
    private static final int TABLE_HISTORY_SLOT = 52;
    private static final int TABLE_BACK_SLOT = 53;

    private final GameContext ctx;
    private final RouletteHost host;

    private final Map<String, Consumer<CasinoPlayer>> actions = new ConcurrentHashMap<>();

    private final Map<UUID, CasinoInventory> tables = new ConcurrentHashMap<>();

    private final Set<String> warnedMenuSlots = ConcurrentHashMap.newKeySet();

    private LeaderboardManager leaderboards;

    public RouletteGUI(GameContext ctx, RouletteHost host) {
        this.ctx = ctx;
        this.host = host;
    }

    public void setLeaderboards(LeaderboardManager leaderboards) {
        this.leaderboards = leaderboards;
    }

    public void action(String name, Consumer<CasinoPlayer> handler) {
        if (name != null && handler != null) {
            actions.put(name, handler);
        }
    }

    public static boolean isRed(int number) {
        return RED_NUMBERS.contains(number);
    }

    public Map<Integer, String> mainMenuActions() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (String[] button : MAIN_MENU_BUTTONS) {
            String action = button[0];
            if (action.equals("wheel") && !ctx.config().getBoolean("features.fortune-wheel", true)) {
                continue;
            }
            if (action.equals("daily") && !ctx.config().getBoolean("features.free-wheel", true)) {
                continue;
            }
            if (action.equals("leaderboards") && !ctx.config().getBoolean("features.leaderboards", true)) {
                continue;
            }
            if (action.equals("roulette") && !ctx.config().getBoolean("roulette.enabled", true)) {
                continue;
            }
            if (action.equals("slots") && !ctx.config().getBoolean("slots.enabled", true)) {
                continue;
            }
            if (action.equals("crash") && !ctx.config().getBoolean("crash.enabled", true)) {
                continue;
            }

            int fallback = Integer.parseInt(button[2]);
            int slot = ctx.config().getInt("main-menu.buttons." + button[1], fallback);
            if (slot < 0 || slot > 53) {
                if (warnedMenuSlots.add(action)) {
                    ctx.logger().warning("main-menu.buttons." + button[1] + " = " + slot
                            + " is outside 0-53, using the default slot " + fallback + ".");
                }
                slot = fallback;
            }
            String taken = map.get(slot);
            if (taken != null) {
                if (warnedMenuSlots.add(action)) {
                    ctx.logger().warning("Main menu slot " + slot + " is used by both '" + taken
                            + "' and '" + action + "'; '" + action + "' was skipped.");
                }
                continue;
            }
            map.put(slot, action);
        }
        return map;
    }

    public void openMainMenu(CasinoPlayer player) {
        CasinoInventory screen = ctx.inventory(GUI_MAIN,
                ctx.msg("gui.titles.main_menu", "Casino: Main Menu"), 54);
        CasinoItem filler = ctx.item("BLACK_STAINED_GLASS_PANE", " ");
        for (int slot = 0; slot < 54; slot++) {
            screen.setItem(slot, filler);
        }

        paintTopWinners(screen);

        for (Map.Entry<Integer, String> entry : mainMenuActions().entrySet()) {
            int slot = entry.getKey();
            switch (entry.getValue()) {
                case "roulette":
                    screen.setItem(slot, ctx.item("DIAMOND",
                            ctx.msg("gui.main.play_roulette", "&a&lPLAY ROULETTE")));
                    break;
                case "wheel":
                    screen.setItem(slot, ctx.item("ORANGE_WOOL",
                            ctx.msg("gui.main.wheel", "&6&lFortune Wheel")));
                    break;
                case "daily":
                    screen.setItem(slot, ctx.item("CHEST",
                            ctx.msg("gui.main.daily", "&a&lFREE FORTUNE WHEEL")));
                    break;
                case "slots":
                    screen.setItem(slot, ctx.item("TNT", ctx.msg("gui.main.slots", "&c&lSLOTS")));
                    break;
                case "leaderboards":
                    screen.setItem(slot, ctx.item("NETHER_STAR",
                            ctx.msg("gui.main.leaderboards", "&e&lLeaderboards")));
                    break;
                case "crash":
                    screen.setItem(slot, ctx.item("FIRE_CHARGE", ctx.msg("gui.main.crash", "&c&lCRASH")));
                    break;
                case "exchange":
                    screen.setItem(slot, ctx.item("GOLD_INGOT", ctx.msg("gui.main.exchange", "&eExchange")));
                    break;
                case "stats":
                    screen.setItem(slot, ctx.item("BOOK", ctx.msg("gui.main.stats", "&bStats")));
                    break;
                case "profile":
                    screen.setItem(slot, profileHead(player));
                    break;
                default:
                    break;
            }
        }

        screen.refresh();
        player.openInventory(screen);
        ctx.playMenu(player);
    }

    private CasinoItem profileHead(CasinoPlayer player) {
        return CasinoItem.of("PLAYER_HEAD")
                .skullOwner(player.uuid())
                .name(ctx.msg("gui.main.profile_name", "&6Profile"))
                .lore(ctx.msgList("gui.main.profile_lore",
                        Arrays.asList("&eChips: %chips%", "&eBalance: %balance%"),
                        "chips", ctx.formatNumber(ctx.chips().chips(player.uuid())),
                        "balance", formatBalance(player)))
                .hideAttributes(true)
                .build();
    }

    private void paintTopWinners(CasinoInventory screen) {
        int count = Math.max(0, Math.min(9, ctx.config().getInt("main-menu.top-winners-count", 9)));
        if (!ctx.config().getBoolean("main-menu.top-winners-heads", true) || leaderboards == null) {
            count = 0;
        }
        List<PlayerStats> top = count == 0
                ? Collections.<PlayerStats>emptyList()
                : leaderboards.top("biggest_win", count);

        for (int i = 0; i < top.size() && i < 9; i++) {
            PlayerStats stats = top.get(i);
            String name = stats.name == null || stats.name.isEmpty()
                    ? stats.uuid.toString().substring(0, 8)
                    : stats.name;
            screen.setItem(i, CasinoItem.of("PLAYER_HEAD")
                    .skullOwner(stats.uuid)
                    .name(ctx.msg("gui.main.top_win_name", "&e#%place% &f%player%",
                            "place", i + 1, "player", name))
                    .lore(ctx.msgList("gui.main.top_win_lore",
                            Arrays.asList("&7Won on this server:", "&a%amount% chips"),
                            "amount", ctx.formatNumber(stats.totalWon)))
                    .hideAttributes(true)
                    .build());
        }
    }

    public void openExchangeMenu(CasinoPlayer player) {
        CasinoInventory screen = ctx.inventory(GUI_EXCHANGE,
                ctx.msg("gui.titles.exchange", "Casino: Exchange"), 27);
        fill(screen, 27);

        String currency = ctx.currencyName();
        screen.setItem(13, chipsItem(player.uuid()));
        screen.setItem(11, ctx.item("EMERALD_BLOCK",
                ctx.msg("gui.exchange.buy_btn", "&a&lBUY"),
                ctx.msg("gui.exchange.buy_lore", "&7Exchange %currency% for chips", "currency", currency)));
        screen.setItem(15, ctx.item("REDSTONE_BLOCK",
                ctx.msg("gui.exchange.sell_btn", "&c&lSELL"),
                ctx.msg("gui.exchange.sell_lore", "&7Convert chips to %currency%", "currency", currency)));
        screen.setItem(26, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));

        screen.refresh();
        player.openInventory(screen);
        ctx.playMenu(player);
    }

    private int packageSize(int index) {
        int min = ctx.minExchange();
        if (index == 0) {
            return Math.max(10, min);
        }
        if (index == 1) {
            return Math.max(100, min * 10);
        }
        return Math.max(1000, min * 50);
    }

    private double tax() {
        return ctx.config().getDouble("settings.commission_percent", 10.0D) / 100.0D;
    }

    public void openBuyChipsMenu(CasinoPlayer player) {
        CasinoInventory screen = ctx.inventory(GUI_BUY,
                ctx.msg("gui.titles.buy_chips", "Casino: Buy Chips"), 27);
        fill(screen, 27);

        double tax = tax();
        String currency = ctx.currencyName();
        String[] icons = {"GOLD_NUGGET", "GOLD_INGOT", "GOLD_BLOCK"};

        for (int i = 0; i < 3; i++) {
            int price = packageSize(i);
            screen.setItem(10 + i, ctx.item(icons[i],
                    ctx.msg("gui.buy.item_name", "&aBuy %amount%", "amount", String.valueOf(price)),
                    ctx.msg("gui.buy.price", "&7Price: %price% %currency%",
                            "price", price, "currency", currency),
                    ctx.msg("gui.buy.receive", "&eReceive: %chips% chips",
                            "chips", (int) (price * (1 - tax)))));
        }

        screen.setItem(13, ctx.item("WRITABLE_BOOK",
                ctx.msg("gui.buy.custom", "&aCustom amount"),
                ctx.msg("gui.buy.custom_lore", "&7Type the amount in chat"),
                ctx.msg("gui.exchange.commission", "&6Fee: %fee%",
                        "fee", ctx.config().getDouble("settings.commission_percent", 10.0D))));
        screen.setItem(20, chipsItem(player.uuid()));
        screen.setItem(26, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));

        screen.refresh();
        player.openInventory(screen);
        ctx.playMenu(player);
    }

    public void openSellChipsMenu(CasinoPlayer player) {
        CasinoInventory screen = ctx.inventory(GUI_SELL,
                ctx.msg("gui.titles.sell_chips", "Casino: Sell Chips"), 27);
        fill(screen, 27);

        double tax = tax();
        String currency = ctx.currencyName();
        String[] icons = {"IRON_NUGGET", "IRON_INGOT", "IRON_BLOCK"};

        for (int i = 0; i < 3; i++) {
            int chips = packageSize(i);
            screen.setItem(10 + i, ctx.item(icons[i],
                    ctx.msg("gui.sell.item_name", "&cSell %amount%", "amount", String.valueOf(chips)),
                    ctx.msg("gui.sell.price", "&7Price: %price% chips", "price", chips),
                    ctx.msg("gui.sell.receive", "&eReceive: %coins% %currency%",
                            "coins", (int) (chips * (1 - tax)), "currency", currency)));
        }

        screen.setItem(13, ctx.item("WRITABLE_BOOK",
                ctx.msg("gui.sell.custom", "&cCustom amount"),
                ctx.msg("gui.sell.custom_lore", "&7Type the amount in chat"),
                ctx.msg("gui.exchange.commission", "&6Fee: %fee%",
                        "fee", ctx.config().getDouble("settings.commission_percent", 10.0D))));
        screen.setItem(20, chipsItem(player.uuid()));
        screen.setItem(26, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));

        screen.refresh();
        player.openInventory(screen);
        ctx.playMenu(player);
    }

    private void handleBuyClick(CasinoPlayer player, int slot) {
        UUID uuid = player.uuid();
        String prefix = ctx.messages().prefix();

        if (slot == 26) {
            openExchangeMenu(player);
            return;
        }
        if (slot == 13) {
            player.closeInventory();
            host.awaitInput(uuid, "buy");
            player.sendMessage(prefix + ctx.msg("messages.enter_buy_enhanced",
                    "&eEnter the amount of %currency% to spend on chips:", "currency", ctx.currencyName()));
            player.sendMessage(ctx.msg("messages.current_chips", "&7(You have: %chips% chips)",
                    "chips", ctx.formatNumber(ctx.chips().chips(uuid)))
                    + " " + ctx.msg("messages.cancel_hint", "&7(Type 'cancel' to abort)"));
            return;
        }
        if (slot < 10 || slot > 12) {
            return;
        }

        int cost = packageSize(slot - 10);
        int gain = (int) (cost * (1 - tax()));

        if (!ctx.withdrawCurrency(player, cost)) {
            player.sendMessage(prefix + ctx.msg("messages.insufficient_funds", "&cInsufficient funds!"));
            return;
        }
        ctx.chips().add(uuid, gain);
        player.sendMessage(prefix + ctx.msg("messages.exchange.buy_success",
                "&aPurchased %chips% chips for %coins% %currency%.",
                "chips", gain, "coins", cost, "currency", ctx.currencyName()));
        ctx.sound(player, "bet", "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f);
        openBuyChipsMenu(player);
    }

    private void handleSellClick(CasinoPlayer player, int slot) {
        UUID uuid = player.uuid();
        String prefix = ctx.messages().prefix();

        if (slot == 26) {
            openExchangeMenu(player);
            return;
        }
        if (slot == 13) {
            player.closeInventory();
            host.awaitInput(uuid, "sell");
            player.sendMessage(prefix + ctx.msg("messages.enter_sell_enhanced",
                    "&eEnter the amount of chips to sell for %currency%:", "currency", ctx.currencyName()));
            player.sendMessage(ctx.msg("messages.current_chips", "&7(You have: %chips% chips)",
                    "chips", ctx.formatNumber(ctx.chips().chips(uuid)))
                    + " " + ctx.msg("messages.cancel_hint", "&7(Type 'cancel' to abort)"));
            return;
        }
        if (slot < 10 || slot > 12) {
            return;
        }

        int chipsCost = packageSize(slot - 10);
        int payout = (int) (chipsCost * (1 - tax()));

        if (!ctx.chips().take(uuid, chipsCost)) {
            player.sendMessage(prefix + ctx.msg("messages.no_chips", "&cYou do not have enough chips!"));
            return;
        }

        if (!ctx.depositCurrency(player, payout)) {
            ctx.chips().add(uuid, chipsCost);
            player.sendMessage(prefix + ctx.msg("messages.economy_unavailable",
                    "&cEconomy is unavailable, your chips were returned."));
            return;
        }

        player.sendMessage(prefix + ctx.msg("messages.exchange.sell_success",
                "&aSold %chips% chips for %coins% %currency%.",
                "chips", chipsCost, "coins", payout, "currency", ctx.currencyName()));
        ctx.sound(player, "bet", "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f);
        openSellChipsMenu(player);
    }

    public void openGameMenu(CasinoPlayer player) {
        UUID uuid = player.uuid();
        CasinoInventory screen = ctx.inventory(GUI_TABLE,
                ctx.msg("gui.titles.roulette", "Roulette Room"), 54);

        tables.put(uuid, screen);
        host.activeBets().putIfAbsent(uuid, new HashMap<>());
        host.currentBetAmount().putIfAbsent(uuid, ctx.minBet());
        host.playersInGame().add(uuid);

        updateTable(uuid);
        player.openInventory(screen);
        ctx.playMenu(player);
        host.checkGameLoop();
    }

    public void updateTable(UUID uuid) {
        CasinoInventory screen = tables.get(uuid);
        if (screen == null) {
            return;
        }

        Map<String, Integer> bets = host.activeBets().getOrDefault(uuid, Collections.<String, Integer>emptyMap());
        int amount = host.currentBetAmount().getOrDefault(uuid, ctx.minBet());
        int minBet = ctx.minBet();

        CasinoItem filler = ctx.item("BLACK_STAINED_GLASS_PANE", " ");
        for (int slot = 0; slot < 54; slot++) {
            screen.setItem(slot, filler);
        }

        if (host.state() == RouletteHost.State.WAITING) {
            screen.setItem(4, ctx.item("CLOCK",
                    ctx.msg("gui.roulette.accepting", "&6Accepting bets..."),
                    ctx.msg("gui.roulette.spinning_in", "&eSpinning in: %time%s", "time", host.timer())));
        } else {
            screen.setItem(4, ctx.item("REDSTONE_TORCH",
                    ctx.msg("gui.roulette.in_progress", "&cSPIN IN PROGRESS")));
        }

        for (int number = 1; number <= 36; number++) {
            boolean red = isRed(number);
            String key = String.valueOf(number);
            boolean placed = bets.containsKey(key);
            String lore = placed
                    ? ctx.msg("gui.roulette.bet_placed", "&eBet: %amount%",
                            "amount", ctx.formatNumber(bets.get(key)))
                    : ctx.msg("gui.roulette.click_to_bet", "&7Click \u2014 bet %amount%",
                            "amount", ctx.formatNumber(amount));
            screen.setItem(FIRST_NUMBER_SLOT + number - 1, CasinoItem.of(red ? "RED_WOOL" : "BLACK_WOOL")
                    .name((red ? "&c" : "&8") + "&l" + number)
                    .lore(lore)
                    .glowing(placed)
                    .hideAttributes(true)
                    .build());
        }

        outsideBet(screen, RED_SLOT, "RED_WOOL", "red", "gui.roulette.red", "&cRed", bets);
        outsideBet(screen, BLACK_SLOT, "BLACK_WOOL", "black", "gui.roulette.black", "&8Black", bets);
        outsideBet(screen, DOZEN1_SLOT, "EMERALD_BLOCK", "1st12", "gui.roulette.doz1", "&a1st Dozen", bets);
        outsideBet(screen, DOZEN2_SLOT, "DIAMOND_BLOCK", "2nd12", "gui.roulette.doz2", "&b2nd Dozen", bets);
        outsideBet(screen, DOZEN3_SLOT, "GOLD_BLOCK", "3rd12", "gui.roulette.doz3", "&63rd Dozen", bets);

        screen.setItem(TABLE_BET_SLOT, ctx.item("SUNFLOWER",
                ctx.msg("common.bet_size", "&6Bet: %amount%", "amount", ctx.formatNumber(amount)),
                ctx.msgList("common.bet_lore_shift", Arrays.asList(
                        "&7LMB/RMB +/- " + (minBet >= 1000 ? "1000" : "10"),
                        "&7Shift +/- " + (minBet >= 1000 ? "10000" : "100")))));

        screen.setItem(TABLE_CHIPS_SLOT, chipsItem(uuid));

        StringBuilder history = new StringBuilder();
        for (Integer number : host.history()) {
            history.append(isRed(number) ? "&c" : "&8").append(number).append(' ');
        }
        screen.setItem(TABLE_HISTORY_SLOT, ctx.item("PAPER",
                ctx.msg("gui.roulette.history", "&bHistory: %history%",
                        "history", history.toString().trim())));

        screen.setItem(TABLE_BACK_SLOT, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));
        screen.refresh();
    }

    public void updateAllTables() {
        for (UUID uuid : tables.keySet()) {
            ctx.player(uuid).ifPresent(player -> player.runOwning(() -> updateTable(uuid)));
        }
    }

    private void outsideBet(CasinoInventory screen, int slot, String material, String key,
                            String path, String def, Map<String, Integer> bets) {
        boolean placed = bets.containsKey(key);
        String lore = placed
                ? ctx.msg("gui.roulette.bet_placed", "&eBet: %amount%",
                        "amount", ctx.formatNumber(bets.get(key)))
                : ctx.msg("gui.roulette.click_to_bet_no_amt", "&7Click to bet");
        screen.setItem(slot, CasinoItem.of(material)
                .name(ctx.msg(path, def))
                .lore(lore)
                .glowing(placed)
                .hideAttributes(true)
                .build());
    }

    private String betKeyFor(int slot) {
        if (slot >= FIRST_NUMBER_SLOT && slot <= LAST_NUMBER_SLOT) {

            return String.valueOf(slot - FIRST_NUMBER_SLOT + 1);
        }
        switch (slot) {
            case RED_SLOT:
                return "red";
            case BLACK_SLOT:
                return "black";
            case DOZEN1_SLOT:
                return "1st12";
            case DOZEN2_SLOT:
                return "2nd12";
            case DOZEN3_SLOT:
                return "3rd12";
            default:
                return null;
        }
    }

    private void handleTableClick(CasinoEvents.InventoryClick event) {
        CasinoPlayer player = event.player();
        UUID uuid = player.uuid();
        int slot = event.slot();
        String prefix = ctx.messages().prefix();

        if (slot == TABLE_BACK_SLOT) {
            player.closeInventory();
            openMainMenu(player);
            return;
        }

        if (host.state() != RouletteHost.State.WAITING) {
            player.sendMessage(prefix + ctx.msg("messages.roulette.closed", "&cBets are closed!"));
            return;
        }

        int minBet = ctx.minBet();
        int maxBet = ctx.maxBet();

        if (slot == TABLE_BET_SLOT) {
            int step = minBet >= 1000 ? 1000 : 10;
            int change = event.clickType().isLeft() ? step : -step;
            if (event.clickType().isShift()) {
                change *= 10;
            }
            int updated = Math.max(minBet, Math.min(maxBet,
                    host.currentBetAmount().getOrDefault(uuid, minBet) + change));
            host.currentBetAmount().put(uuid, updated);
            updateTable(uuid);
            ctx.playClick(player);
            return;
        }

        String betKey = betKeyFor(slot);
        if (betKey == null) {
            return;
        }

        Map<String, Integer> bets = host.activeBets().computeIfAbsent(uuid, key -> new HashMap<>());
        int amount = host.currentBetAmount().getOrDefault(uuid, minBet);

        if (bets.containsKey(betKey)) {
            int refunded = bets.remove(betKey);
            ctx.chips().add(uuid, refunded);
            if (bets.isEmpty()) {
                host.activeBets().remove(uuid);
            }
            player.sendMessage(prefix + ctx.msg("messages.roulette.bet_removed", "&eBet removed."));
            ctx.sound(player, "click", "BLOCK_NOTE_BLOCK_BASS", 1.0f);
            updateTable(uuid);
            return;
        }

        if ((betKey.equals("red") && bets.containsKey("black"))
                || (betKey.equals("black") && bets.containsKey("red"))) {
            player.sendMessage(prefix + ctx.msg("messages.roulette.opposing",
                    "&cYou cannot bet on both colours!"));
            return;
        }
        if (betKey.matches("1st12|2nd12|3rd12")
                && bets.keySet().stream().anyMatch(key -> key.matches("1st12|2nd12|3rd12"))) {
            player.sendMessage(prefix + ctx.msg("messages.roulette.dozen",
                    "&cYou can only choose one dozen!"));
            return;
        }

        int limit = selectionLimit(player);
        if (bets.size() >= limit) {
            player.sendMessage(prefix + ctx.msg("messages.max_selection",
                    "&cBet limit reached: %limit%!", "limit", limit));
            return;
        }
        if (!ctx.chips().take(uuid, amount)) {
            player.sendMessage(prefix + ctx.msg("messages.insufficient_funds", "&cInsufficient funds!"));
            return;
        }

        bets.put(betKey, amount);
        host.activeBets().put(uuid, bets);
        player.sendMessage(prefix + ctx.msg("messages.roulette.bet_placed",
                "&aPlaced %amount% on %bet%", "amount", ctx.formatNumber(amount), "bet", betKey));
        ctx.playClick(player);
        updateTable(uuid);
    }

    public int selectionLimit(CasinoPlayer player) {
        if (player.hasPermission("casino.limit.admin")) {
            return ctx.config().getInt("selection_limits.admin", 25);
        }
        if (player.hasPermission("casino.limit.vip")) {
            return ctx.config().getInt("selection_limits.vip", 10);
        }
        return ctx.config().getInt("selection_limits.default", 5);
    }

    public void openDetailedStats(CasinoPlayer player) {
        CasinoInventory screen = ctx.inventory(GUI_STATS,
                ctx.msg("gui.titles.stats", "Detailed Statistics"), 27);
        fill(screen, 27);

        PlayerStats stats = ctx.stats().snapshot(player.uuid());
        screen.setItem(13, ctx.item("BOOK",
                ctx.msg("gui.stats.title_item", "&6Statistics"),
                Arrays.asList(
                        ctx.msg("gui.stats.wins", "&eWins: %amount%", "amount", stats.wins),
                        ctx.msg("gui.stats.losses", "&eLosses: %amount%", "amount", stats.losses),
                        ctx.msg("gui.stats.rounds", "&eRounds played: %amount%", "amount", stats.rounds),
                        ctx.msg("gui.stats.won", "&aTotal won: %amount% chips",
                                "amount", ctx.formatNumber(stats.totalWon)),
                        ctx.msg("gui.stats.lost", "&cTotal lost: %amount% chips",
                                "amount", ctx.formatNumber(stats.totalLost)))));
        screen.setItem(26, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));

        screen.refresh();
        player.openInventory(screen);
    }

    public void handleClick(CasinoEvents.InventoryClick event) {
        if (!event.isTopInventory()) {
            return;
        }
        CasinoPlayer player = event.player();
        String guiId = event.guiId();

        if (GUI_MAIN.equals(guiId)) {
            handleMainMenuClick(player, event.slot());
            return;
        }
        if (GUI_EXCHANGE.equals(guiId)) {
            ctx.playClick(player);
            if (event.slot() == 11) {
                openBuyChipsMenu(player);
            } else if (event.slot() == 15) {
                openSellChipsMenu(player);
            } else if (event.slot() == 26) {
                openMainMenu(player);
            }
            return;
        }
        if (GUI_BUY.equals(guiId)) {
            handleBuyClick(player, event.slot());
            return;
        }
        if (GUI_SELL.equals(guiId)) {
            handleSellClick(player, event.slot());
            return;
        }
        if (GUI_TABLE.equals(guiId)) {
            handleTableClick(event);
            return;
        }
        if (GUI_STATS.equals(guiId) && event.slot() == 26) {
            ctx.playClick(player);
            openMainMenu(player);
        }
    }

    private void handleMainMenuClick(CasinoPlayer player, int slot) {
        String action = mainMenuActions().get(slot);
        if (action == null) {
            return;
        }
        ctx.playClick(player);

        switch (action) {
            case "roulette":
                openGameMenu(player);
                break;
            case "exchange":
                openExchangeMenu(player);
                break;
            case "stats":
                openDetailedStats(player);
                break;
            case "profile":

                break;
            default:
                Consumer<CasinoPlayer> handler = actions.get(action);
                if (handler != null) {
                    handler.accept(player);
                } else {
                    player.sendMessage(ctx.messages().prefix()
                            + ctx.msg("messages.game_disabled", "&cThis game is disabled."));
                }
                break;
        }
    }

    public void handleClose(CasinoEvents.InventoryClose event) {
        if (GUI_TABLE.equals(event.guiId())) {
            UUID uuid = event.player().uuid();
            tables.remove(uuid);
            host.playersInGame().remove(uuid);
        }
    }

    public void forget(UUID uuid) {
        tables.remove(uuid);
        host.playersInGame().remove(uuid);
    }

    private void fill(CasinoInventory screen, int size) {
        CasinoItem filler = ctx.item("BLACK_STAINED_GLASS_PANE", " ");
        for (int slot = 0; slot < size; slot++) {
            screen.setItem(slot, filler);
        }
    }

    private CasinoItem chipsItem(UUID uuid) {
        return ctx.item("GOLD_NUGGET", ctx.msg("gui.roulette.your_chips", "&6Chips: %amount%",
                "amount", ctx.formatNumber(ctx.chips().chips(uuid))));
    }

    private String formatBalance(CasinoPlayer player) {
        if (ctx.itemMode()) {
            return ctx.formatNumber((long) ctx.currencyBalance(player)) + " " + ctx.currencyName();
        }
        if (ctx.economy() == null || !ctx.economy().isAvailable()) {
            return "-";
        }
        return ctx.economy().format(ctx.economy().balance(player.uuid()));
    }
}
