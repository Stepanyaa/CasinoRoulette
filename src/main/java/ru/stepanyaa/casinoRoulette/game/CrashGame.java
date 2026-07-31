
package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CrashGame {

    public static final String GUI_ID = "casino:crash";

    private static final int SIZE = 27;
    private static final int MULTIPLIER_SLOT = 4;
    private static final int BET_SLOT = 11;
    private static final int PLACE_BET_SLOT = 13;
    private static final int CHIPS_SLOT = 20;
    private static final int CASHOUT_SLOT = 22;
    private static final int BACK_SLOT = 26;

    private final GameContext ctx;
    private final Random random = new Random();

    private final Map<UUID, Integer> playerBets = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> cashedOut = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentBetAmount = new ConcurrentHashMap<>();
    private final Map<UUID, CasinoInventory> screens = new ConcurrentHashMap<>();

    private volatile boolean gameRunning = false;
    private volatile double currentMultiplier = 0.10D;
    private volatile double crashPoint = 0.0D;

    private Consumer<CasinoPlayer> mainMenu = player -> { };

    public CrashGame(GameContext ctx) {
        this.ctx = ctx;
    }

    public void onBack(Consumer<CasinoPlayer> mainMenu) {
        if (mainMenu != null) {
            this.mainMenu = mainMenu;
        }
    }

    private static String twoDecimals(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public void open(CasinoPlayer player) {
        UUID uuid = player.uuid();
        CasinoInventory screen = ctx.inventory(GUI_ID, ctx.msg("gui.titles.crash", "Crash Game"), SIZE);

        CasinoItem filler = ctx.item("BLACK_STAINED_GLASS_PANE", " ");
        for (int slot = 0; slot < SIZE; slot++) {
            screen.setItem(slot, filler);
        }

        currentBetAmount.putIfAbsent(uuid, ctx.minBet());

        screen.setItem(BET_SLOT, betItem(uuid));
        screen.setItem(PLACE_BET_SLOT, placeBetItem(uuid));
        screen.setItem(MULTIPLIER_SLOT, ctx.item("NETHER_STAR",
                ctx.msg("gui.crash.multiplier", "&6x%multiplier%", "multiplier", "0.10")));
        screen.setItem(CASHOUT_SLOT, ctx.item("BARRIER",
                ctx.msg("gui.crash.awaiting", "&7Awaiting bet...")));
        screen.setItem(CHIPS_SLOT, chipsItem(uuid));
        screen.setItem(BACK_SLOT, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));
        screen.refresh();

        screens.put(uuid, screen);
        player.openInventory(screen);
        ctx.playMenu(player);

        if (gameRunning) {
            update(player);
        }
    }

    private CasinoItem betItem(UUID uuid) {
        int amount = currentBetAmount.getOrDefault(uuid, ctx.minBet());
        return ctx.item("GOLD_INGOT",
                ctx.msg("common.bet_size", "&6Bet: %amount%", "amount", ctx.formatNumber(amount)),
                ctx.msgList("common.bet_lore", Arrays.asList("&7Left click + | Right click -"),
                        "min", ctx.minBet(), "max", ctx.formatNumber(ctx.maxBet())));
    }

    private CasinoItem placeBetItem(UUID uuid) {
        return ctx.item("SUNFLOWER",
                ctx.msg("gui.crash.place_bet", "&ePLACE BET"),
                ctx.msg("gui.crash.place_bet_lore", "&7Amount: %amount%",
                        "amount", ctx.formatNumber(currentBetAmount.getOrDefault(uuid, ctx.minBet()))));
    }

    private CasinoItem chipsItem(UUID uuid) {
        return ctx.item("GOLD_NUGGET", ctx.msg("gui.roulette.your_chips", "&6Chips: %amount%",
                "amount", ctx.formatNumber(ctx.chips().chips(uuid))));
    }

    public void handleClick(CasinoEvents.InventoryClick event) {
        if (!GUI_ID.equals(event.guiId()) || !event.isTopInventory()) {
            return;
        }
        CasinoPlayer player = event.player();
        UUID uuid = player.uuid();
        int slot = event.slot();
        String prefix = ctx.messages().prefix();

        if (slot == BACK_SLOT) {
            player.closeInventory();
            ctx.playMenu(player);
            mainMenu.accept(player);
            return;
        }

        if (slot == BET_SLOT) {
            if (gameRunning) {
                player.sendMessage(prefix + ctx.msg("messages.crash.no_change", "&cCannot change the bet now!"));
                return;
            }
            int step = ctx.minBet() >= 1000 ? 1000 : 10;
            int amount = currentBetAmount.getOrDefault(uuid, ctx.minBet());
            if (event.clickType().isLeft()) {
                amount += step;
            } else if (event.clickType().isRight()) {
                amount -= step;
            } else {
                return;
            }
            amount = Math.max(ctx.minBet(), Math.min(ctx.maxBet(), amount));
            currentBetAmount.put(uuid, amount);

            CasinoInventory screen = screens.get(uuid);
            if (screen != null) {
                screen.setItem(BET_SLOT, betItem(uuid));
                screen.setItem(PLACE_BET_SLOT, placeBetItem(uuid));
                screen.refresh();
            }
            ctx.playClick(player);
            return;
        }

        if (slot == PLACE_BET_SLOT) {
            placeBet(player, prefix);
            return;
        }

        if (slot == CASHOUT_SLOT) {
            cashOut(player, prefix);
        }
    }

    private void placeBet(CasinoPlayer player, String prefix) {
        UUID uuid = player.uuid();
        if (gameRunning) {
            player.sendMessage(prefix + ctx.msg("messages.crash.started", "&cThe round has already started!"));
            return;
        }
        if (playerBets.containsKey(uuid)) {

            player.sendMessage(prefix + ctx.msg("messages.crash.started", "&cYour bet is already placed!"));
            return;
        }

        int bet = Math.max(ctx.minBet(), Math.min(ctx.maxBet(),
                currentBetAmount.getOrDefault(uuid, ctx.minBet())));
        if (!ctx.chips().take(uuid, bet)) {
            player.sendMessage(prefix + ctx.msg("messages.insufficient_funds", "&cInsufficient funds!"));
            return;
        }

        playerBets.put(uuid, bet);
        cashedOut.put(uuid, false);

        player.sendMessage(prefix + ctx.msg("messages.crash.locked", "&aBet %bet% locked.",
                "bet", ctx.formatNumber(bet)));
        ctx.sound(player, "bet", "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f);

        if (!gameRunning) {
            startNewRound();
        }
        updateAll();
    }

    private void cashOut(CasinoPlayer player, String prefix) {
        UUID uuid = player.uuid();
        if (!gameRunning || !playerBets.containsKey(uuid)) {
            return;
        }

        if (Boolean.TRUE.equals(cashedOut.put(uuid, true))) {
            return;
        }

        double multiplier = currentMultiplier;
        int bet = playerBets.getOrDefault(uuid, 0);
        int win = (int) (bet * multiplier);

        ctx.chips().add(uuid, win);
        ctx.stats().logGameResult(uuid, bet, win, true);

        player.sendMessage(prefix + ctx.msg("messages.crash.cashed_out",
                "&6CASHED OUT! x%mult% \u2192 +%win%",
                "mult", twoDecimals(multiplier), "win", ctx.formatNumber(win)));
        ctx.playWin(player);
        updateAll();
    }

    public void handleClose(CasinoEvents.InventoryClose event) {
        if (GUI_ID.equals(event.guiId())) {
            screens.remove(event.player().uuid());
        }
    }

    private void startNewRound() {
        gameRunning = true;
        currentMultiplier = ctx.config().getDouble("crash.start_multiplier", 0.10D);
        crashPoint = generateCrashPoint();

        long speed = Math.max(1L, ctx.config().getLong("crash.tick_speed", 2L));
        double step = ctx.config().getDouble("crash.increment", 0.01D);

        CasinoScheduler.timer(0L, speed, task -> {
            if (!gameRunning) {
                task.cancel();
                return;
            }
            currentMultiplier += step;
            if (currentMultiplier < crashPoint) {
                updateAll();
                return;
            }
            gameRunning = false;
            explode();
            task.cancel();
        });
    }

    private void explode() {
        String message = ctx.messages().prefix() + ctx.msg("messages.crash.exploded",
                "&cCRASHED AT x%mult%!", "mult", twoDecimals(crashPoint));

        for (Map.Entry<UUID, Integer> entry : playerBets.entrySet()) {
            UUID uuid = entry.getKey();
            int bet = entry.getValue();
            if (cashedOut.getOrDefault(uuid, false)) {
                continue;
            }
            ctx.stats().logGameResult(uuid, bet, 0, false);
            ctx.player(uuid).ifPresent(player -> player.runOwning(() -> {
                player.sendMessage(message);
                ctx.sound(player, "crash", "ENTITY_GENERIC_EXPLODE", 0.8f);
            }));
        }

        playerBets.clear();
        cashedOut.clear();
        updateAll();
    }

    private double generateCrashPoint() {
        double roll = random.nextDouble();
        double instantChance = ctx.config().getDouble("crash.generation.instant_crash_chance", 0.30D);
        double lowChance = ctx.config().getDouble("crash.generation.low_crash_chance", 0.60D);

        if (roll < instantChance) {
            return 1.00D;
        }
        if (roll < lowChance) {
            return 0.50D + random.nextDouble() * 0.60D;
        }
        return 1.50D + random.nextDouble() * 8.50D;
    }

    private void updateAll() {
        for (UUID uuid : screens.keySet()) {
            ctx.player(uuid).ifPresent(player -> player.runOwning(() -> update(player)));
        }
    }

    private void update(CasinoPlayer player) {
        UUID uuid = player.uuid();
        CasinoInventory screen = screens.get(uuid);
        if (screen == null) {
            return;
        }

        screen.setItem(MULTIPLIER_SLOT, gameRunning
                ? ctx.item("NETHER_STAR", ctx.msg("gui.crash.multiplier", "&6x%multiplier%",
                        "multiplier", twoDecimals(currentMultiplier)))
                : ctx.item("BARRIER", ctx.msg("gui.crash.crashed", "&cCrashed at x%multiplier%",
                        "multiplier", twoDecimals(crashPoint))));

        boolean hasBet = playerBets.containsKey(uuid);
        boolean canCashOut = gameRunning && hasBet && !cashedOut.getOrDefault(uuid, false);

        String name;
        if (canCashOut) {
            name = ctx.msg("gui.crash.cashout", "&aCASH OUT x%multiplier%",
                    "multiplier", twoDecimals(currentMultiplier));
        } else if (hasBet) {
            name = ctx.msg("gui.crash.already_claimed", "&cAlready cashed out");
        } else {
            name = ctx.msg("gui.crash.no_bet", "&7No bet");
        }

        String lore = canCashOut
                ? ctx.msg("gui.crash.cashout_lore", "&eMultiplier: x%multiplier% &7| &ePayout: %payout%",
                        "multiplier", twoDecimals(currentMultiplier), "payout",
                        ctx.formatNumber((long) (playerBets.getOrDefault(uuid, 0) * currentMultiplier)))
                : ctx.msg("gui.crash.click_secure", "&8Click");

        screen.setItem(CASHOUT_SLOT, ctx.item(canCashOut ? "EMERALD_BLOCK" : "BARRIER", name, lore));
        screen.setItem(CHIPS_SLOT, chipsItem(uuid));
        screen.refresh();
    }

    public void handlePlayerDisconnect(UUID uuid) {
        Integer bet = playerBets.remove(uuid);
        if (bet != null && !cashedOut.getOrDefault(uuid, false)) {
            ctx.chips().add(uuid, bet);
            ctx.logger().info("Refunded " + bet + " chips to " + uuid
                    + " from the crash game after a disconnect.");
        }
        cashedOut.remove(uuid);
        currentBetAmount.remove(uuid);
        screens.remove(uuid);
    }
}
