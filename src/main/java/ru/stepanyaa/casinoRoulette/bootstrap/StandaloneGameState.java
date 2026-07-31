
package ru.stepanyaa.casinoRoulette.bootstrap;

import ru.stepanyaa.casinoRoulette.PlayerStats;
import ru.stepanyaa.casinoRoulette.game.ChipsBank;
import ru.stepanyaa.casinoRoulette.game.GameContext;
import ru.stepanyaa.casinoRoulette.game.GameHub;
import ru.stepanyaa.casinoRoulette.game.RouletteGUI;
import ru.stepanyaa.casinoRoulette.game.RouletteHost;
import ru.stepanyaa.casinoRoulette.game.StatsStore;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class StandaloneGameState implements ChipsBank, StatsStore, RouletteHost {

    private static final String FILE_NAME = "players.properties";

    private static final long SAVE_INTERVAL_TICKS = 20L * 60L;

    private final File file;
    private final Logger logger;
    private final Random random = new Random();

    private final Map<UUID, PlayerStats> players = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    private GameContext ctx;
    private GameHub games;

    private final Map<UUID, Map<String, Integer>> activeBets = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentBetAmount = new ConcurrentHashMap<>();
    private final Set<UUID> playersInGame = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> awaitingInput = new ConcurrentHashMap<>();
    private final LinkedList<Integer> history = new LinkedList<>();

    private volatile State state = State.WAITING;
    private volatile int timer = 30;
    private CasinoTask roundTask;
    private CasinoTask saveTask;

    public StandaloneGameState(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, FILE_NAME);
        this.logger = logger;
        load();
    }

    public void attach(GameContext ctx, GameHub games) {
        this.ctx = ctx;
        this.games = games;
        this.timer = timerDuration();
        this.saveTask = CasinoScheduler.asyncTimer(SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS,
                task -> saveIfDirty());
    }

    private PlayerStats stats(UUID player) {
        return players.computeIfAbsent(player, PlayerStats::new);
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        Properties data = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            data.load(in);
        } catch (Exception failure) {
            logger.warning("Could not read " + FILE_NAME + ": " + failure);
            return;
        }

        Set<String> ids = new HashSet<>();
        for (String key : data.stringPropertyNames()) {
            int dot = key.indexOf('.');
            if (dot > 0) {
                ids.add(key.substring(0, dot));
            }
        }
        for (String id : ids) {
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException malformed) {
                continue;
            }
            PlayerStats stats = new PlayerStats(uuid);
            stats.name = data.getProperty(id + ".name", "");
            stats.chips = number(data, id, "chips");
            stats.wins = number(data, id, "wins");
            stats.losses = number(data, id, "losses");
            stats.totalWon = number(data, id, "totalWon");
            stats.totalLost = number(data, id, "totalLost");
            stats.rounds = number(data, id, "rounds");
            stats.bets = number(data, id, "bets");
            stats.biggestWin = number(data, id, "biggestWin");
            stats.biggestLoss = number(data, id, "biggestLoss");
            stats.dailyUses = number(data, id, "dailyUses");
            stats.casesOpened = number(data, id, "casesOpened");
            stats.wheelSpins = number(data, id, "wheelSpins");
            stats.chipsBought = number(data, id, "chipsBought");
            stats.dailyTotalWon = number(data, id, "dailyTotalWon");
            stats.mostValuableDailyReward = number(data, id, "mostValuableDailyReward");
            stats.lastDailyReward = data.getProperty(id + ".lastDailyReward", "");
            stats.lastDailyUse = number(data, id, "lastDailyUse");
            players.put(uuid, stats);
        }
        logger.info("Loaded " + players.size() + " player records.");
    }

    private static long number(Properties data, String id, String field) {
        try {
            return Long.parseLong(data.getProperty(id + "." + field, "0").trim());
        } catch (NumberFormatException malformed) {
            return 0L;
        }
    }

    public synchronized void save() {
        Properties data = new Properties();
        for (PlayerStats stats : players.values()) {
            String id = stats.uuid.toString();
            data.setProperty(id + ".name", stats.name == null ? "" : stats.name);
            data.setProperty(id + ".chips", Long.toString(stats.chips));
            data.setProperty(id + ".wins", Long.toString(stats.wins));
            data.setProperty(id + ".losses", Long.toString(stats.losses));
            data.setProperty(id + ".totalWon", Long.toString(stats.totalWon));
            data.setProperty(id + ".totalLost", Long.toString(stats.totalLost));
            data.setProperty(id + ".rounds", Long.toString(stats.rounds));
            data.setProperty(id + ".bets", Long.toString(stats.bets));
            data.setProperty(id + ".biggestWin", Long.toString(stats.biggestWin));
            data.setProperty(id + ".biggestLoss", Long.toString(stats.biggestLoss));
            data.setProperty(id + ".dailyUses", Long.toString(stats.dailyUses));
            data.setProperty(id + ".casesOpened", Long.toString(stats.casesOpened));
            data.setProperty(id + ".wheelSpins", Long.toString(stats.wheelSpins));
            data.setProperty(id + ".chipsBought", Long.toString(stats.chipsBought));
            data.setProperty(id + ".dailyTotalWon", Long.toString(stats.dailyTotalWon));
            data.setProperty(id + ".mostValuableDailyReward",
                    Long.toString(stats.mostValuableDailyReward));
            data.setProperty(id + ".lastDailyReward",
                    stats.lastDailyReward == null ? "" : stats.lastDailyReward);
            data.setProperty(id + ".lastDailyUse", Long.toString(stats.lastDailyUse));
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                logger.warning("Could not create " + parent + "; player data was not saved.");
                return;
            }
            try (OutputStream out = new FileOutputStream(file)) {
                data.store(out, "CasinoRoulette player data");
            }
            dirty = false;
        } catch (Exception failure) {
            logger.warning("Could not write " + FILE_NAME + ": " + failure);
        }
    }

    private void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void shutdown() {
        if (roundTask != null) {
            roundTask.cancel();
            roundTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        refundOpenBets();
        save();
    }

    private void refundOpenBets() {
        for (Map.Entry<UUID, Map<String, Integer>> entry : activeBets.entrySet()) {
            int total = 0;
            for (int amount : entry.getValue().values()) {
                total += amount;
            }
            if (total > 0) {
                add(entry.getKey(), total);
            }
        }
        activeBets.clear();
    }

    public void seen(UUID player, String name) {
        PlayerStats stats = stats(player);
        if (name != null && !name.equals(stats.name)) {
            stats.name = name;
            dirty = true;
        }
    }

    @Override
    public int chips(UUID player) {
        long held = stats(player).chips;
        return held > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) held;
    }

    @Override
    public void add(UUID player, int amount) {
        if (amount <= 0) {
            return;
        }
        synchronized (players) {
            stats(player).chips += amount;
        }
        dirty = true;
    }

    @Override
    public boolean take(UUID player, int amount) {
        if (amount <= 0) {
            return true;
        }

        synchronized (players) {
            PlayerStats stats = stats(player);
            if (stats.chips < amount) {
                return false;
            }
            stats.chips -= amount;
        }
        dirty = true;
        return true;
    }

    @Override
    public List<PlayerStats> loadAll() {
        return new ArrayList<>(players.values());
    }

    @Override
    public PlayerStats snapshot(UUID player) {
        return stats(player);
    }

    @Override
    public long stat(UUID player, String column) {
        PlayerStats stats = stats(player);
        switch (column == null ? "" : column) {
            case "chips": return stats.chips;
            case "wins": return stats.wins;
            case "losses": return stats.losses;
            case "total_won": return stats.totalWon;
            case "total_lost": return stats.totalLost;
            case "rounds": return stats.rounds;
            case "bets": return stats.bets;
            case "biggest_win": return stats.biggestWin;
            case "biggest_loss": return stats.biggestLoss;
            case "daily_uses": return stats.dailyUses;
            case "cases_opened": return stats.casesOpened;
            case "wheel_spins": return stats.wheelSpins;
            case "chips_bought": return stats.chipsBought;
            case "daily_total_won": return stats.dailyTotalWon;
            case "most_valuable_daily_reward": return stats.mostValuableDailyReward;
            case "last_daily_use": return stats.lastDailyUse;
            default: return 0L;
        }
    }

    @Override
    public void increment(UUID player, String column, long value, boolean highestOnly) {
        PlayerStats stats = stats(player);
        synchronized (players) {
            switch (column == null ? "" : column) {
                case "bets": stats.bets = merge(stats.bets, value, highestOnly); break;
                case "wins": stats.wins = merge(stats.wins, value, highestOnly); break;
                case "losses": stats.losses = merge(stats.losses, value, highestOnly); break;
                case "rounds": stats.rounds = merge(stats.rounds, value, highestOnly); break;
                case "total_won": stats.totalWon = merge(stats.totalWon, value, highestOnly); break;
                case "total_lost": stats.totalLost = merge(stats.totalLost, value, highestOnly); break;
                case "biggest_win": stats.biggestWin = merge(stats.biggestWin, value, highestOnly); break;
                case "biggest_loss": stats.biggestLoss = merge(stats.biggestLoss, value, highestOnly); break;
                case "cases_opened": stats.casesOpened = merge(stats.casesOpened, value, highestOnly); break;
                case "wheel_spins": stats.wheelSpins = merge(stats.wheelSpins, value, highestOnly); break;
                case "chips_bought": stats.chipsBought = merge(stats.chipsBought, value, highestOnly); break;
                default: return;
            }
        }
        dirty = true;
    }

    private static long merge(long current, long value, boolean highestOnly) {
        return highestOnly ? Math.max(current, value) : current + value;
    }

    @Override
    public void logGameResult(UUID player, int bet, int payout, boolean win) {
        PlayerStats stats = stats(player);
        synchronized (players) {
            stats.bets++;
            stats.rounds++;
            if (win) {
                stats.wins++;
                stats.totalWon += Math.max(0, payout);
                stats.biggestWin = Math.max(stats.biggestWin, Math.max(0, payout));
            } else {
                stats.losses++;
                stats.totalLost += Math.max(0, bet);
                stats.biggestLoss = Math.max(stats.biggestLoss, Math.max(0, bet));
            }
        }
        dirty = true;
    }

    @Override
    public long lastDaily(UUID player) {
        return stats(player).lastDailyUse;
    }

    @Override
    public void setDaily(UUID player, long usedAt, String reward, long value) {
        PlayerStats stats = stats(player);
        synchronized (players) {
            stats.lastDailyUse = usedAt;
            stats.lastDailyReward = reward == null ? "" : reward;
            stats.dailyUses++;
            stats.dailyTotalWon += Math.max(0, value);
            stats.mostValuableDailyReward = Math.max(stats.mostValuableDailyReward, Math.max(0, value));
        }
        dirty = true;
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public int timer() {
        return timer;
    }

    @Override
    public Map<UUID, Map<String, Integer>> activeBets() {
        return activeBets;
    }

    @Override
    public Map<UUID, Integer> currentBetAmount() {
        return currentBetAmount;
    }

    @Override
    public Set<UUID> playersInGame() {
        return playersInGame;
    }

    @Override
    public List<Integer> history() {
        return history;
    }

    @Override
    public void awaitInput(UUID player, String kind) {
        awaitingInput.put(player, kind);
    }

    public Map<UUID, String> awaitingInput() {
        return awaitingInput;
    }

    private int timerDuration() {
        return ctx == null ? 30 : Math.max(5, ctx.config().getInt("roulette.timer_duration", 30));
    }

    @Override
    public void checkGameLoop() {
        if (ctx == null || playersInGame.isEmpty()) {
            return;
        }
        if (roundTask != null && !roundTask.isCancelled()) {
            return;
        }
        timer = timerDuration();
        state = State.WAITING;
        roundTask = CasinoScheduler.timer(0L, 20L, this::tick);
    }

    private void tick(CasinoTask task) {
        try {
            if (playersInGame.isEmpty()) {
                state = State.WAITING;
                timer = timerDuration();
                roundTask = null;
                task.cancel();
                return;
            }
            if (state == State.SPINNING) {
                return;
            }
            if (timer > 0) {
                timer--;
                if (timer <= 5 || timer % 5 == 0) {
                    float pitch = 0.7F + (30 - Math.min(30, timer)) * 0.03F;
                    for (UUID uuid : new java.util.ArrayList<>(playersInGame)) {
                        CasinoPlayer player = ctx.player(uuid).orElse(null);
                        if (player != null) ctx.playSpin(player, pitch);
                    }
                }
                if (games != null) {
                    games.gui().updateAllTables();
                }
                return;
            }
            state = State.SPINNING;
            if (games != null) {
                games.gui().updateAllTables();
            }
            spin();
        } catch (Throwable failure) {

            logger.warning("Roulette round failed: " + failure);
            state = State.WAITING;
            timer = timerDuration();
            roundTask = null;
        }
    }

    private void spin() {
        final int winning = random.nextInt(37);

        history.addFirst(winning);
        while (history.size() > 10) {
            history.removeLast();
        }

        Map<UUID, Map<String, Integer>> round = new HashMap<>(activeBets);
        activeBets.clear();

        for (Map.Entry<UUID, Map<String, Integer>> entry : round.entrySet()) {
            settle(entry.getKey(), entry.getValue(), winning);
        }

        state = State.WAITING;
        timer = timerDuration();
        games.gui().updateAllTables();
    }

    private void settle(UUID uuid, Map<String, Integer> bets, int winning) {
        int staked = 0;
        int payout = 0;
        for (Map.Entry<String, Integer> bet : bets.entrySet()) {
            int amount = bet.getValue() == null ? 0 : bet.getValue();
            if (amount <= 0) {
                continue;
            }
            staked += amount;
            payout += amount * multiplier(bet.getKey(), winning);
        }
        if (staked <= 0) {
            return;
        }

        boolean win = payout > 0;
        if (win) {
            add(uuid, payout);
        }
        logGameResult(uuid, staked, payout, win);

        CasinoPlayer player = ctx.player(uuid).orElse(null);
        if (player == null) {
            return;
        }
        String colour = RouletteGUI.isRed(winning) ? "&c" : winning == 0 ? "&a" : "&8";
        if (win) {
            ctx.playWin(player);
            ctx.tell(player, "messages.roulette.win",
                    "&aResult: %color%%number% &a-> Won: %payout% chips!",
                    "color", colour, "number", winning, "payout", ctx.formatNumber(payout));
        } else {
            ctx.playLose(player);
            ctx.tell(player, "messages.roulette.loss",
                    "&cResult: %color%%number% &c-> Lost: %bet% chips.",
                    "color", colour, "number", winning, "bet", ctx.formatNumber(staked));
        }
    }

    private int multiplier(String key, int winning) {
        if (key == null) {
            return 0;
        }
        int number = ctx.config().getInt("roulette.payouts.number", 36);
        int colour = ctx.config().getInt("roulette.payouts.color", 2);
        int sector = ctx.config().getInt("roulette.payouts.sector", 3);

        switch (key) {
            case "red":
                return winning != 0 && RouletteGUI.isRed(winning) ? colour : 0;
            case "black":
                return winning != 0 && !RouletteGUI.isRed(winning) ? colour : 0;
            case "1st12":
                return winning >= 1 && winning <= 12 ? sector : 0;
            case "2nd12":
                return winning >= 13 && winning <= 24 ? sector : 0;
            case "3rd12":
                return winning >= 25 && winning <= 36 ? sector : 0;
            default:
                try {
                    return Integer.parseInt(key) == winning ? number : 0;
                } catch (NumberFormatException notANumber) {
                    return 0;
                }
        }
    }
}
