package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.PlayerStats;
import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class LeaderboardManager {

    public static final String GUI_CATEGORIES = "casino:leaderboards";
    public static final String GUI_CATEGORY = "casino:leaderboard";

    private static final int SIZE = 54;
    private static final int CATEGORIES_BACK_SLOT = 49;
    private static final int CATEGORY_BACK_SLOT = 45;
    private static final int CATEGORY_ICON_SLOT = 49;

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 22, 24};
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final List<String> CATEGORIES = Arrays.asList(
            "money", "wins", "bets", "games", "biggest_win",
            "biggest_loss", "daily", "wheel", "chips_bought");

    private static final Map<String, String> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("money", "EMERALD");
        ICONS.put("wins", "DIAMOND");
        ICONS.put("bets", "GOLD_INGOT");
        ICONS.put("games", "CLOCK");
        ICONS.put("biggest_win", "NETHER_STAR");
        ICONS.put("biggest_loss", "REDSTONE");
        ICONS.put("daily", "CHEST");
        ICONS.put("wheel", "ORANGE_WOOL");
        ICONS.put("chips_bought", "GOLD_BLOCK");
    }

    private final GameContext ctx;

    private final Map<String, List<PlayerStats>> cache = new ConcurrentHashMap<>();

    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    private Consumer<CasinoPlayer> mainMenu = player -> { };

    public LeaderboardManager(GameContext ctx) {
        this.ctx = ctx;
    }

    public void onBack(Consumer<CasinoPlayer> mainMenu) {
        if (mainMenu != null) {
            this.mainMenu = mainMenu;
        }
    }

    public void start() {
        long period = 20L * ctx.document(GameContext.LEADERBOARD)
                .getLong("leaderboard.update-time", 300L);

        CasinoScheduler.asyncTimer(20L, Math.max(20L, period), task -> refresh());
    }

    public void refresh() {
        List<PlayerStats> all;
        try {
            all = ctx.stats().loadAll();
        } catch (Throwable failure) {

            ctx.logger().warning("Leaderboards could not be refreshed: " + failure);
            return;
        }
        if (all == null) {
            return;
        }
        for (String category : CATEGORIES) {
            List<PlayerStats> sorted = new ArrayList<>(all);
            sorted.sort(Comparator.comparingLong((PlayerStats stats) -> value(category, stats)).reversed());
            cache.put(category, sorted);
        }
    }

    private long value(String category, PlayerStats stats) {
        switch (category) {
            case "money":
                return stats.chips;
            case "wins":
                return stats.wins;
            case "bets":
                return stats.bets;
            case "games":
                return stats.rounds;
            case "biggest_win":
                return stats.biggestWin;
            case "biggest_loss":
                return stats.biggestLoss;
            case "daily":
                return stats.dailyUses;
            case "wheel":
                return stats.wheelSpins;
            case "chips_bought":
                return stats.chipsBought;
            default:
                return 0L;
        }
    }

    public String placeholder(String category, int position) {
        List<PlayerStats> list = cached(category);
        if (list == null || position < 1 || position > list.size()) {
            return "-";
        }
        PlayerStats stats = list.get(position - 1);
        return playerName(stats) + " - " + value(category, stats);
    }

    public List<PlayerStats> top(String category, int limit) {
        List<PlayerStats> list = cached(category);
        if (list == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list.subList(0, Math.min(Math.max(0, limit), list.size())));
    }

    private List<PlayerStats> cached(String category) {
        List<PlayerStats> list = cache.get(category);
        if (list == null) {
            refresh();
            list = cache.get(category);
        }
        return list;
    }

    public void openCategories(CasinoPlayer player) {
        CasinoInventory screen = ctx.inventory(GUI_CATEGORIES,
                ctx.msg("gui.titles.leaderboards", "&8\u2726 Leaderboards \u2726"), SIZE);
        fill(screen);

        for (int i = 0; i < CATEGORIES.size() && i < CATEGORY_SLOTS.length; i++) {
            String category = CATEGORIES.get(i);
            List<PlayerStats> top = top(category, 1);
            String leader = top.isEmpty()
                    ? ctx.msg("leaderboard.empty", "&7No data yet")
                    : "&f" + playerName(top.get(0)) + " &8\u2014 &a"
                            + ctx.formatNumber(value(category, top.get(0)));
            screen.setItem(CATEGORY_SLOTS[i], ctx.item(ICONS.getOrDefault(category, "PAPER"),
                    ctx.msg("leaderboard.category_name", "&e&l%category%", "category", name(category)),
                    ctx.msg("leaderboard.category_leader", "&7Leader: %leader%", "leader", leader),
                    ctx.msg("leaderboard.category_click", "&aClick to open")));
        }

        screen.setItem(CATEGORIES_BACK_SLOT, ctx.item("BARRIER", ctx.msg("common.back", "&cBack")));
        screen.refresh();
        player.openInventory(screen);
        viewers.add(player.uuid());
    }

    public void open(CasinoPlayer player, String category) {
        String title = ctx.document(GameContext.LEADERBOARD)
                .getString("gui.title", "&8\u2726 %category% \u2726")
                .replace("%category%", name(category));
        CasinoInventory screen = ctx.inventory(GUI_CATEGORY, title, SIZE);
        fill(screen);

        List<PlayerStats> list = top(category, ENTRY_SLOTS.length);
        for (int i = 0; i < list.size(); i++) {
            PlayerStats stats = list.get(i);
            screen.setItem(ENTRY_SLOTS[i], CasinoItem.of("PLAYER_HEAD")
                    .skullOwner(stats.uuid)
                    .name(ctx.msg("leaderboard.entry_name", "&6#%place% &f%player%",
                            "place", i + 1, "player", playerName(stats)))
                    .lore(Arrays.asList(
                            ctx.msg("leaderboard.entry_value", "&eValue: &a%value%",
                                    "value", ctx.formatNumber(value(category, stats))),
                            ctx.msg("leaderboard.entry_wins", "&7Wins: &f%wins%",
                                    "wins", ctx.formatNumber(stats.wins)),
                            ctx.msg("leaderboard.entry_games", "&7Games: &f%games%",
                                    "games", ctx.formatNumber(stats.rounds))))
                    .hideAttributes(true)
                    .build());
        }

        if (list.isEmpty()) {
            screen.setItem(22, ctx.item("BOOK", ctx.msg("leaderboard.empty", "&7No data yet")));
        }
        screen.setItem(CATEGORY_BACK_SLOT, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));
        screen.setItem(CATEGORY_ICON_SLOT, ctx.item(ICONS.getOrDefault(category, "NETHER_STAR"),
                ctx.msg("leaderboard.current_category", "&e%category%", "category", name(category))));
        screen.refresh();

        player.openInventory(screen);
        viewers.add(player.uuid());
    }

    public void handleClick(CasinoEvents.InventoryClick event) {
        if (!event.isTopInventory()) {
            return;
        }
        String guiId = event.guiId();
        CasinoPlayer player = event.player();

        if (GUI_CATEGORIES.equals(guiId)) {
            if (event.slot() == CATEGORIES_BACK_SLOT) {
                ctx.playMenu(player);
                forget(player.uuid());
                mainMenu.accept(player);
                return;
            }
            String category = categoryAt(event.slot());
            if (category != null) {
                ctx.playClick(player);
                open(player, category);
            }
            return;
        }

        if (GUI_CATEGORY.equals(guiId) && event.slot() == CATEGORY_BACK_SLOT) {
            ctx.playMenu(player);
            openCategories(player);
        }
    }

    public void handleClose(CasinoEvents.InventoryClose event) {
        if (GUI_CATEGORIES.equals(event.guiId()) || GUI_CATEGORY.equals(event.guiId())) {
            forget(event.player().uuid());
        }
    }

    private String categoryAt(int slot) {
        for (int i = 0; i < CATEGORIES.size() && i < CATEGORY_SLOTS.length; i++) {
            if (CATEGORY_SLOTS[i] == slot) {
                return CATEGORIES.get(i);
            }
        }
        return null;
    }

    public boolean isViewing(UUID uuid) {
        return viewers.contains(uuid);
    }

    public void forget(UUID uuid) {
        viewers.remove(uuid);
    }

    private void fill(CasinoInventory screen) {
        CasinoItem filler = ctx.item("BLACK_STAINED_GLASS_PANE", " ");
        for (int slot = 0; slot < screen.size(); slot++) {
            screen.setItem(slot, filler);
        }
    }

    private String playerName(PlayerStats stats) {
        return stats.name == null || stats.name.isEmpty()
                ? stats.uuid.toString().substring(0, 8)
                : stats.name;
    }

    private String name(String category) {
        String configured = ctx.document(GameContext.LEADERBOARD)
                .getString("categories." + category, category);
        return ctx.msg("leaderboard.cat." + category, configured);
    }
}
