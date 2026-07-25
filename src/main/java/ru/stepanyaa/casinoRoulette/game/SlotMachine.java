
package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.config.YamlDocument;
import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SlotMachine {

    public static final String GUI_ID = "casino:slots";

    private static final int SIZE = 27;
    private static final int BET_SLOT = 9;
    private static final int SPIN_SLOT = 17;
    private static final int CHIPS_SLOT = 20;
    private static final int BACK_SLOT = 26;
    private static final int FIRST_REEL = 10;
    private static final int REELS = 7;

    private static final int SPIN_FRAMES = 20;

    private final GameContext ctx;
    private final Random random = new Random();

    private final Map<UUID, Integer> currentBet = new ConcurrentHashMap<>();
    private final Map<UUID, CasinoInventory> screens = new ConcurrentHashMap<>();
    private final Set<UUID> spinning = ConcurrentHashMap.newKeySet();

    private final Map<String, Integer> weights = new LinkedHashMap<>();

    private Consumer<CasinoPlayer> mainMenu = player -> { };

    public SlotMachine(GameContext ctx) {
        this.ctx = ctx;
        loadConfigValues();
    }

    public void onBack(Consumer<CasinoPlayer> mainMenu) {
        if (mainMenu != null) {
            this.mainMenu = mainMenu;
        }
    }

    public void loadConfigValues() {
        weights.clear();
        YamlDocument config = ctx.config();
        for (String key : config.keys("slots.weights")) {
            String symbol = key.trim().toUpperCase(Locale.ROOT);
            int weight = config.getInt("slots.weights." + key, 0);

            if (weight > 0 && ctx.adapter().materialExists(symbol)) {
                weights.put(symbol, weight);
            }
        }
        if (weights.isEmpty()) {
            weights.put("COAL", 40);
            weights.put("IRON_INGOT", 25);
            weights.put("GOLD_INGOT", 5);
            weights.put("EMERALD", 3);
            weights.put("DIAMOND", 2);
        }
    }

    public void open(CasinoPlayer player) {
        CasinoInventory screen = ctx.inventory(GUI_ID, ctx.msg("gui.titles.slots", "Slot Machine"), SIZE);
        CasinoItem filler = ctx.item("BLACK_STAINED_GLASS_PANE", " ");
        for (int slot = 0; slot < SIZE; slot++) {
            screen.setItem(slot, filler);
        }

        int bet = clampBet(currentBet.getOrDefault(player.uuid(), ctx.minBet()));
        currentBet.put(player.uuid(), bet);

        screen.setItem(BET_SLOT, betItem(bet));
        screen.setItem(SPIN_SLOT, ctx.item("LEVER", ctx.msg("gui.slots.spin", "&6SPIN!")));
        for (int i = 0; i < REELS; i++) {
            screen.setItem(FIRST_REEL + i, ctx.item("BARRIER", ctx.msg("common.awaiting", "&7Awaiting...")));
        }
        screen.setItem(CHIPS_SLOT, chipsItem(player.uuid()));
        screen.setItem(BACK_SLOT, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));
        screen.refresh();

        screens.put(player.uuid(), screen);
        player.openInventory(screen);
        ctx.playMenu(player);
    }

    private CasinoItem betItem(int amount) {
        return ctx.item("SUNFLOWER",
                ctx.msg("common.bet_size", "&6Bet: %amount%", "amount", ctx.formatNumber(amount)),
                ctx.msgList("common.bet_lore", Arrays.asList("&7Left click + | Right click -"),
                        "min", ctx.minBet(), "max", ctx.formatNumber(ctx.maxBet())));
    }

    private CasinoItem chipsItem(UUID uuid) {
        return ctx.item("GOLD_NUGGET", ctx.msg("gui.roulette.your_chips", "&6Chips: %amount%",
                "amount", ctx.formatNumber(ctx.chips().chips(uuid))));
    }

    private int clampBet(int amount) {
        return Math.max(ctx.minBet(), Math.min(ctx.maxBet(), amount));
    }

    public void handleClick(CasinoEvents.InventoryClick event) {
        if (!GUI_ID.equals(event.guiId()) || !event.isTopInventory()) {
            return;
        }
        CasinoPlayer player = event.player();
        UUID uuid = player.uuid();
        int slot = event.slot();

        if (slot == BACK_SLOT) {
            player.closeInventory();
            ctx.playMenu(player);
            mainMenu.accept(player);
            return;
        }

        if (spinning.contains(uuid)) {
            return;
        }

        if (slot == BET_SLOT) {
            int step = ctx.minBet() >= 1000 ? 1000 : 10;
            int bet = currentBet.getOrDefault(uuid, ctx.minBet());
            if (event.clickType().isRight()) {
                bet -= step;
            } else if (event.clickType().isLeft()) {
                bet += step;
            } else {
                return;
            }
            bet = clampBet(bet);
            currentBet.put(uuid, bet);

            CasinoInventory screen = screens.get(uuid);
            if (screen != null) {
                screen.setItem(BET_SLOT, betItem(bet));
                screen.refresh();
            }
            ctx.playClick(player);
            return;
        }

        if (slot == SPIN_SLOT) {
            spin(player);
        }
    }

    public void handleClose(CasinoEvents.InventoryClose event) {
        if (GUI_ID.equals(event.guiId()) && !spinning.contains(event.player().uuid())) {
            screens.remove(event.player().uuid());
        }
    }

    private void spin(CasinoPlayer player) {
        UUID uuid = player.uuid();
        if (!spinning.add(uuid)) {
            return;
        }

        CasinoInventory screen = screens.get(uuid);
        if (screen == null) {
            spinning.remove(uuid);
            return;
        }

        final int bet = clampBet(currentBet.getOrDefault(uuid, ctx.minBet()));
        if (!ctx.chips().take(uuid, bet)) {
            spinning.remove(uuid);
            player.sendMessage(ctx.messages().prefix()
                    + ctx.msg("messages.insufficient_funds", "&cYou do not have enough funds!"));
            return;
        }

        screen.setItem(SPIN_SLOT, ctx.item("REDSTONE_BLOCK", ctx.msg("gui.slots.spinning", "&cSPINNING...")));
        screen.refresh();

        final List<String> symbols = new ArrayList<>(weights.keySet());
        final int[] frame = {0};

        CasinoScheduler.timerAtEntity(player.handle(), 0L, 2L, task -> {
            if (!player.isOnline()) {

                task.cancel();
                return;
            }

            frame[0]++;
            for (int i = 0; i < REELS; i++) {
                screen.setItem(FIRST_REEL + i,
                        ctx.item(symbols.get(random.nextInt(symbols.size())), " "));
            }
            screen.refresh();
            ctx.playSpin(player, 0.6f + frame[0] * 0.03f);

            if (frame[0] < SPIN_FRAMES) {
                return;
            }
            task.cancel();
            settle(player, screen, bet);
        });
    }

    private void settle(CasinoPlayer player, CasinoInventory screen, int bet) {
        UUID uuid = player.uuid();

        String[] rolled = new String[REELS];
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < REELS; i++) {
            rolled[i] = weightedSymbol();
            counts.merge(rolled[i], 1, Integer::sum);
            screen.setItem(FIRST_REEL + i, ctx.item(rolled[i], "&l" + rolled[i]));
        }

        int matches = 0;
        String winner = rolled[0];
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > matches) {
                matches = entry.getValue();
                winner = entry.getKey();
            }
        }

        String prefix = ctx.messages().prefix();
        if (matches >= 3) {
            int baseMultiplier = ctx.config().getInt("slots.multipliers." + winner,
                    ctx.config().getInt("slots.multipliers.DEFAULT", 5));
            int progressive = (int) Math.pow(2, matches - 3);
            int payout = bet * baseMultiplier * progressive;

            ctx.chips().add(uuid, payout);
            ctx.stats().logGameResult(uuid, bet, payout, true);
            ctx.playWin(player);
            player.sendMessage(prefix + ctx.msg("messages.slots.win", "&6WIN! x%mult% \u2192 %payout%",
                    "matches", matches,
                    "symbol", winner,
                    "mult", baseMultiplier * progressive,
                    "payout", ctx.formatNumber(payout)));
        } else {
            ctx.stats().logGameResult(uuid, bet, 0, false);
            ctx.playLose(player);
            player.sendMessage(prefix + ctx.msg("messages.slots.loss", "&cNo match!"));
        }

        screen.setItem(SPIN_SLOT, ctx.item("LEVER", ctx.msg("gui.slots.spin", "&6SPIN!")));
        screen.setItem(CHIPS_SLOT, chipsItem(uuid));
        screen.refresh();
        spinning.remove(uuid);
    }

    private String weightedSymbol() {
        int total = 0;
        for (int weight : weights.values()) {
            total += weight;
        }
        if (total <= 0) {
            return "COAL";
        }
        int roll = random.nextInt(total);
        int running = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            running += entry.getValue();
            if (roll < running) {
                return entry.getKey();
            }
        }
        return "COAL";
    }

    public void handlePlayerDisconnect(UUID uuid) {
        if (spinning.remove(uuid)) {
            int bet = currentBet.getOrDefault(uuid, 0);
            if (bet > 0) {
                ctx.chips().add(uuid, bet);
                ctx.logger().info("Refunded " + bet + " chips to " + uuid
                        + " from the slot machine after a disconnect mid spin.");
            }
        }
        currentBet.remove(uuid);
        screens.remove(uuid);
    }
}
