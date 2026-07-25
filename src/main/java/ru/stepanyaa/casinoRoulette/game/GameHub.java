
package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;

import java.util.UUID;

public final class GameHub {

    private final GameContext ctx;

    private final RouletteGUI gui;
    private final WheelGame wheel;
    private final DailyWheel daily;
    private final SlotMachine slots;
    private final CrashGame crash;
    private final LeaderboardManager leaderboards;

    private volatile boolean started;

    public GameHub(GameContext ctx, RouletteHost host) {
        this.ctx = ctx;
        this.gui = new RouletteGUI(ctx, host);
        this.wheel = new WheelGame(ctx);
        this.daily = new DailyWheel(ctx);
        this.slots = new SlotMachine(ctx);
        this.crash = new CrashGame(ctx);
        this.leaderboards = new LeaderboardManager(ctx);

        gui.action("wheel", player -> openIfEnabled(player, "features.fortune-wheel", wheel::open));
        gui.action("daily", player -> openIfEnabled(player, "features.free-wheel", daily::open));
        gui.action("slots", player -> openIfEnabled(player, "slots.enabled", slots::open));
        gui.action("crash", player -> openIfEnabled(player, "crash.enabled", crash::open));
        gui.action("leaderboards",
                player -> openIfEnabled(player, "features.leaderboards", leaderboards::openCategories));

        gui.setLeaderboards(leaderboards);

        wheel.onBack(gui::openMainMenu);
        daily.onBack(gui::openMainMenu);
        slots.onBack(gui::openMainMenu);
        crash.onBack(gui::openMainMenu);
        leaderboards.onBack(gui::openMainMenu);
    }

    private interface Opener {
        void open(CasinoPlayer player);
    }

    private void openIfEnabled(CasinoPlayer player, String toggle, Opener opener) {
        if (ctx.config().getBoolean(toggle, true)) {
            opener.open(player);
            return;
        }
        player.sendMessage(ctx.messages().prefix()
                + ctx.msg("messages.game_disabled", "&cThis game is disabled."));
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;

        ctx.adapter().events().subscribe(CasinoEvents.InventoryClick.class, this::onClick);
        ctx.adapter().events().subscribe(CasinoEvents.InventoryClose.class, this::onClose);
        ctx.adapter().events().subscribe(CasinoEvents.PlayerQuit.class,
                event -> forget(event.player().uuid()));

        leaderboards.start();
    }

    private void onClick(CasinoEvents.InventoryClick event) {
        String guiId = event.guiId();
        if (guiId == null || !guiId.startsWith("casino:")) {
            return;
        }

        event.setCancelled(true);

        gui.handleClick(event);
        wheel.handleClick(event);
        daily.handleClick(event);
        slots.handleClick(event);
        crash.handleClick(event);
        leaderboards.handleClick(event);
    }

    private void onClose(CasinoEvents.InventoryClose event) {
        gui.handleClose(event);
        wheel.handleClose(event);
        daily.handleClose(event);
        slots.handleClose(event);
        crash.handleClose(event);
        leaderboards.handleClose(event);
    }

    public void forget(UUID uuid) {
        gui.forget(uuid);
        wheel.forget(uuid);
        daily.forget(uuid);
        slots.handlePlayerDisconnect(uuid);
        crash.handlePlayerDisconnect(uuid);
        leaderboards.forget(uuid);
    }

    public void reload() {
        ctx.reload();
        slots.loadConfigValues();
        leaderboards.refresh();
    }

    public GameContext context() {
        return ctx;
    }

    public RouletteGUI gui() {
        return gui;
    }

    public WheelGame wheel() {
        return wheel;
    }

    public DailyWheel daily() {
        return daily;
    }

    public SlotMachine slots() {
        return slots;
    }

    public CrashGame crash() {
        return crash;
    }

    public LeaderboardManager leaderboards() {
        return leaderboards;
    }
}
