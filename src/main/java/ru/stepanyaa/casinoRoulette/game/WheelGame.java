
package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.config.YamlDocument;
import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class WheelGame {

    public static final String GUI_ID = "casino:wheel";

    private static final int SIZE = 54;
    private static final int POINTER_SLOT = 22;
    private static final int BET_SLOT = 50;
    private static final int BACK_SLOT = 49;
    private static final int START_SLOT = 53;

    private static final int[] WHEEL_SLOTS =
            {10, 11, 12, 13, 14, 15, 16, 25, 34, 43, 42, 41, 40, 39, 38, 37, 28, 19};

    private static final int POINTER_INDEX = 3;

    private final GameContext ctx;
    private final Random random = new Random();

    private final Map<UUID, String> selectedColor = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bet = new ConcurrentHashMap<>();
    private final Map<UUID, CasinoInventory> screens = new ConcurrentHashMap<>();
    private final Set<UUID> spinning = ConcurrentHashMap.newKeySet();

    private Consumer<CasinoPlayer> mainMenu = player -> { };

    public WheelGame(GameContext ctx) {
        this.ctx = ctx;
    }

    public void onBack(Consumer<CasinoPlayer> mainMenu) {
        if (mainMenu != null) {
            this.mainMenu = mainMenu;
        }
    }

    private YamlDocument wheel() {
        return ctx.document(GameContext.WHEEL);
    }

    private String chipsName() {
        return ctx.chipsName();
    }

    private int minBet() {
        return Math.max(1, wheel().getInt("wheel.min-bet", 10));
    }

    private int maxBet() {
        return Math.max(minBet(), wheel().getInt("wheel.max-bet", 10000));
    }

    public void open(CasinoPlayer player) {
        if (spinning.contains(player.uuid())) {
            player.sendMessage(ctx.messages().prefix()
                    + ctx.msg("messages.wheel.already_running", "&cThe wheel is already spinning!"));
            return;
        }
        CasinoInventory screen = ctx.inventory(GUI_ID,
                ctx.msg("gui.titles.wheel", "&6Fortune Wheel"), SIZE);
        screens.put(player.uuid(), screen);
        draw(player.uuid(), screen, 0, -1);
        player.openInventory(screen);
        ctx.playMenu(player);
    }

    private void draw(UUID uuid, CasinoInventory screen, int offset, int pointerSlot) {
        CasinoItem filler = ctx.item("BLACK_STAINED_GLASS_PANE", " ");
        for (int slot = 0; slot < SIZE; slot++) {
            screen.setItem(slot, filler);
        }

        List<String> sectors = sectors();
        for (int i = 0; i < WHEEL_SLOTS.length; i++) {
            String sector = sectors.get(Math.floorMod(i + offset, sectors.size()));
            String prefix = WHEEL_SLOTS[i] == pointerSlot ? "&l\u27a4 " : "";
            screen.setItem(WHEEL_SLOTS[i], ctx.item(material(sector),
                    prefix + displayName(sector) + " &7x" + multiplier(sector)));
        }

        screen.setItem(POINTER_SLOT, ctx.item("NETHER_STAR",
                ctx.msg("gui.wheel.pointer", "&e&lPointer"),
                ctx.msg("gui.wheel.pointer_lore", "&7The winning sector stops here")));

        int start = wheel().getInt("gui.color-start-slot", 45);
        int index = 0;
        for (String sector : configuredColors()) {
            int slot = start + index++;
            if (slot > 48) {
                break;
            }
            boolean selected = sector.equalsIgnoreCase(selectedColor.get(uuid));
            screen.setItem(slot, ctx.item(material(sector),
                    ctx.msg("gui.wheel.color", "%selected%&e%color% &7x%multiplier%",
                            "selected", selected ? "&a\u2714 " : "",
                            "color", displayName(sector),
                            "multiplier", multiplier(sector)),
                    ctx.msg("gui.wheel.color_lore", "&7Click to pick this colour")));
        }

        int currentBet = bet.getOrDefault(uuid, minBet());
        screen.setItem(BET_SLOT, ctx.item("GOLD_NUGGET",
                ctx.msg("gui.wheel.bet", "&6Bet: %amount% %currency%",
                        "amount", ctx.formatNumber(currentBet), "currency", chipsName()),
                ctx.msg("gui.wheel.bet_lore", "&7Left click + | Right click -"),
                ctx.msg("gui.wheel.balance", "&7Balance: %balance%",
                        "balance", ctx.formatNumber(ctx.chips().chips(uuid)) + " " + chipsName())));
        screen.setItem(BACK_SLOT, ctx.item("BARRIER", ctx.msg("common.back", "&cBack")));
        screen.setItem(START_SLOT, ctx.item("EMERALD_BLOCK", ctx.msg("gui.wheel.start", "&aSpin")));
        screen.refresh();
    }

    private List<String> sectors() {
        List<String> list = new ArrayList<>();
        for (String key : wheel().keys("wheel.sectors")) {
            int count = wheel().getInt("wheel.sectors." + key, 0);
            for (int i = 0; i < count; i++) {
                list.add(key);
            }
        }
        if (list.isEmpty()) {
            Collections.addAll(list, "orange", "orange", "orange", "yellow", "yellow", "blue", "red");
        }
        return list;
    }

    private List<String> configuredColors() {
        List<String> list = new ArrayList<>(wheel().keys("wheel.multipliers"));
        if (list.isEmpty()) {
            Collections.addAll(list, "orange", "yellow", "blue", "red");
        }
        return list;
    }

    private String material(String sector) {
        String configured = wheel().getString("colors." + sector + ".material",
                sector.toUpperCase(Locale.ROOT) + "_WOOL");
        return ctx.adapter().materialExists(configured) ? configured : "WHITE_WOOL";
    }

    private String displayName(String sector) {
        String def = wheel().getString("colors." + sector + ".name", sector);
        return ctx.msg("gui.wheel.colors." + sector, def);
    }

    private int multiplier(String sector) {
        return wheel().getInt("wheel.multipliers." + sector, 1);
    }

    public void handleClick(CasinoEvents.InventoryClick event) {
        if (!GUI_ID.equals(event.guiId()) || !event.isTopInventory()) {
            return;
        }
        CasinoPlayer player = event.player();
        UUID uuid = player.uuid();
        int slot = event.slot();

        if (slot == BACK_SLOT) {
            ctx.playMenu(player);
            mainMenu.accept(player);
            return;
        }
        if (spinning.contains(uuid)) {
            return;
        }

        int start = wheel().getInt("gui.color-start-slot", 45);
        List<String> colors = configuredColors();
        if (slot >= start && slot < start + colors.size()) {
            selectedColor.put(uuid, colors.get(slot - start));
            ctx.playClick(player);
            open(player);
            return;
        }

        if (slot == BET_SLOT) {
            int step = Math.max(1, wheel().getInt("wheel.bet-step", 100));
            int current = bet.getOrDefault(uuid, minBet()) + (event.clickType().isRight() ? -step : step);
            bet.put(uuid, Math.max(minBet(), Math.min(maxBet(), current)));
            ctx.playClick(player);
            open(player);
            return;
        }

        if (slot == START_SLOT) {
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
        if (spinning.contains(uuid)) {
            return;
        }
        String chosen = selectedColor.get(uuid);
        if (chosen == null) {
            player.sendMessage(ctx.messages().prefix()
                    + ctx.msg("messages.wheel.select_color", "&cPick a colour first!"));
            return;
        }

        CasinoInventory screen = screens.get(uuid);
        if (screen == null) {
            return;
        }

        final int amount = Math.max(minBet(), Math.min(maxBet(), bet.getOrDefault(uuid, minBet())));

        if (!ctx.chips().take(uuid, amount)) {
            player.sendMessage(ctx.messages().prefix()
                    + ctx.msg("messages.no_chips", "&cYou do not have enough chips!"));
            return;
        }

        spinning.add(uuid);
        final List<String> sectors = sectors();

        final int[] state = {0, 0};
        final int frames = 48 + random.nextInt(24);

        CasinoScheduler.timerAtEntity(player.handle(), 0L,
                Math.max(1L, wheel().getLong("wheel.animation-tick", 2L)), task -> {
                    if (!player.isOnline()) {
                        ctx.chips().add(uuid, amount);
                        spinning.remove(uuid);
                        task.cancel();
                        return;
                    }

                    state[1]++;
                    draw(uuid, screen, state[1], WHEEL_SLOTS[POINTER_INDEX]);
                    ctx.playSpin(player, Math.min(2f, 0.5f + state[0] * 0.02f));

                    if (++state[0] < frames) {
                        return;
                    }
                    task.cancel();
                    settle(player, sectors, state[1], chosen, amount);
                });
    }

    private void settle(CasinoPlayer player, List<String> sectors, int offset, String chosen, int amount) {
        UUID uuid = player.uuid();
        String win = sectors.get(Math.floorMod(POINTER_INDEX + offset, sectors.size()));
        String prefix = ctx.messages().prefix();

        if (win.equalsIgnoreCase(chosen)) {
            int payout = amount * multiplier(win);
            ctx.chips().add(uuid, payout);
            ctx.stats().logGameResult(uuid, amount, payout, true);
            ctx.playWin(player);
            player.sendMessage(prefix + ctx.msg("messages.wheel.win",
                    "&aWin! %color% \u2192 +%amount% %currency%",
                    "color", displayName(win),
                    "amount", ctx.formatNumber(payout),
                    "currency", chipsName()));
        } else {
            ctx.stats().logGameResult(uuid, amount, 0, false);
            ctx.playLose(player);
            player.sendMessage(prefix + ctx.msg("messages.wheel.loss",
                    "&c%color% came up. The bet is lost.", "color", displayName(win)));
        }

        ctx.stats().increment(uuid, "wheel_spins", 1, false);
        spinning.remove(uuid);
    }

    public void forget(UUID uuid) {
        if (spinning.remove(uuid)) {
            int amount = bet.getOrDefault(uuid, 0);
            if (amount > 0) {
                ctx.chips().add(uuid, amount);
            }
        }
        selectedColor.remove(uuid);
        bet.remove(uuid);
        screens.remove(uuid);
    }
}
