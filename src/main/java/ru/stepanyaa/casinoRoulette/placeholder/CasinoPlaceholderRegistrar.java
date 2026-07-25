package ru.stepanyaa.casinoRoulette.placeholder;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class CasinoPlaceholderRegistrar {

    public interface StatsSource {

        double balance(UUID player);

        long chips(UUID player);

        int level(UUID player);

        long lastWin(UUID player);

        long totalGames(UUID player);

        long totalWins(UUID player);

        long totalLosses(UUID player);

        long biggestWin(UUID player);

        long totalWon(UUID player);

        long totalLost(UUID player);

        long dailyCooldownMillis(UUID player);

        String topName(String board, int place);

        double topValue(String board, int place);

        String formatMoney(double amount);
    }

    private static final String EMPTY_PLACE = "---";

    private static final String READY = "Ready";

    private CasinoPlaceholderRegistrar() {
    }

    public static void registerAll(PlaceholderService service, final StatsSource stats) {

        service.register("balance", (uuid, params) ->
                stats.formatMoney(stats.balance(uuid)));

        service.register("balance_raw", (uuid, params) ->
                String.valueOf(stats.balance(uuid)));

        service.register("chips", (uuid, params) ->
                String.valueOf(stats.chips(uuid)));

        service.register("level", (uuid, params) ->
                String.valueOf(stats.level(uuid)));

        service.register("last_win", (uuid, params) ->
                String.valueOf(stats.lastWin(uuid)));

        service.register("total_games", (uuid, params) ->
                String.valueOf(stats.totalGames(uuid)));

        service.register("total_wins", (uuid, params) ->
                String.valueOf(stats.totalWins(uuid)));

        service.register("total_losses", (uuid, params) ->
                String.valueOf(stats.totalLosses(uuid)));

        service.register("biggest_win", (uuid, params) ->
                String.valueOf(stats.biggestWin(uuid)));

        service.register("total_won", (uuid, params) ->
                String.valueOf(stats.totalWon(uuid)));

        service.register("total_lost", (uuid, params) ->
                String.valueOf(stats.totalLost(uuid)));

        service.register("profit", (uuid, params) ->
                String.valueOf(stats.totalWon(uuid) - stats.totalLost(uuid)));

        service.register("winrate", (uuid, params) -> {
            long games = stats.totalGames(uuid);
            if (games <= 0) {
                return "0.0";
            }
            double rate = (stats.totalWins(uuid) * 100.0D) / games;
            return String.format(Locale.ROOT, "%.1f", rate);
        });

        service.register("daily_time", (uuid, params) -> {
            long remaining = stats.dailyCooldownMillis(uuid);
            return remaining <= 0 ? READY : formatDuration(remaining);
        });

        service.register("daily_ready", (uuid, params) ->
                stats.dailyCooldownMillis(uuid) <= 0 ? "true" : "false");

        service.register("daily_seconds", (uuid, params) ->
                String.valueOf(Math.max(0L,
                        TimeUnit.MILLISECONDS.toSeconds(stats.dailyCooldownMillis(uuid)))));

        service.registerPrefix("top_", (uuid, params) -> {
            String request = params.substring("top_".length());

            boolean wantsValue = request.endsWith("_value");
            if (wantsValue) {
                request = request.substring(0, request.length() - "_value".length());
            }

            int separator = request.lastIndexOf('_');
            if (separator <= 0 || separator == request.length() - 1) {
                return PlaceholderService.UNKNOWN;
            }

            String board = request.substring(0, separator).toLowerCase(Locale.ROOT);
            int place;
            try {
                place = Integer.parseInt(request.substring(separator + 1));
            } catch (NumberFormatException notANumber) {
                return PlaceholderService.UNKNOWN;
            }
            if (place < 1) {
                return PlaceholderService.UNKNOWN;
            }

            if (wantsValue) {
                return "money".equals(board)
                        ? stats.formatMoney(stats.topValue(board, place))
                        : String.valueOf((long) stats.topValue(board, place));
            }

            String name = stats.topName(board, place);
            return name == null || name.isEmpty() ? EMPTY_PLACE : name;
        });
    }

    private static String formatDuration(long millis) {
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}
