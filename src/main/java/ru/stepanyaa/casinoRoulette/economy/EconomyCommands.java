package ru.stepanyaa.casinoRoulette.economy;

import ru.stepanyaa.casinoRoulette.game.GameContext;
import ru.stepanyaa.casinoRoulette.platform.CasinoCommand;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.platform.CasinoSender;
import ru.stepanyaa.casinoRoulette.platform.PlatformAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class EconomyCommands {
    private EconomyCommands() { }

    public static void register(PlatformAdapter adapter, GameContext ctx) {
        adapter.registerCommand(command("bal", "Show balance", (sender, args) -> balance(adapter, ctx, sender, args)));
        adapter.registerCommand(command("balance", "Show balance", (sender, args) -> balance(adapter, ctx, sender, args)));
        adapter.registerCommand(command("baltop", "Show richest players", (sender, args) -> top(adapter, ctx, sender)));
        adapter.registerCommand(command("pay", "Pay another player", (sender, args) -> pay(adapter, ctx, sender, args)));
        adapter.registerCommand(command("eco", "Manage economy", (sender, args) -> manage(adapter, ctx, sender, args)));
    }

    private interface Handler { boolean run(CasinoSender sender, String[] args); }
    private static CasinoCommand command(String name, String description, Handler handler) {
        return new CasinoCommand() {
            @Override public String name() { return name; }
            @Override public String description() { return description; }
            @Override public boolean execute(CasinoSender sender, String[] args) { return handler.run(sender, args); }
        };
    }

    private static boolean balance(PlatformAdapter adapter, GameContext ctx, CasinoSender sender, String[] args) {
        CasinoPlayer target = args.length > 0 ? adapter.player(args[0]).orElse(null)
                : sender instanceof CasinoPlayer ? (CasinoPlayer) sender : null;
        if (target == null) { sender.sendMessage("&cPlayer is not online."); return true; }
        sender.sendMessage("&6Balance of " + target.name() + ": &e" + formatBalance(ctx, target));
        return true;
    }

    private static boolean pay(PlatformAdapter adapter, GameContext ctx, CasinoSender sender, String[] args) {
        if (ctx.itemMode()) {
            sender.sendMessage("&c/pay is disabled for ITEM currency. Items can only be used in casino exchange menus.");
            return true;
        }
        if (!(sender instanceof CasinoPlayer)) { sender.sendMessage("&cPlayers only."); return true; }
        if (args.length < 2) { sender.sendMessage("&eUsage: /pay <player> <amount>"); return true; }
        CasinoPlayer from = (CasinoPlayer) sender;
        CasinoPlayer to = adapter.player(args[0]).orElse(null);
        double amount = amount(args[1]);
        if (to == null || amount <= 0 || to.uuid().equals(from.uuid())) { sender.sendMessage("&cInvalid player or amount."); return true; }
        boolean withdrawn = ctx.economy().withdraw(from.uuid(), amount);
        if (!withdrawn) { sender.sendMessage("&cInsufficient funds."); return true; }
        boolean deposited = ctx.economy().deposit(to.uuid(), amount);
        if (!deposited) {
            ctx.economy().deposit(from.uuid(), amount);
            sender.sendMessage("&cPayment failed."); return true;
        }
        String formatted = formatAmount(ctx, amount);
        sender.sendMessage("&aPaid " + to.name() + " " + formatted);
        to.sendMessage("&aReceived " + formatted + " from " + from.name());
        return true;
    }

    private static boolean top(PlatformAdapter adapter, GameContext ctx, CasinoSender sender) {
        if (ctx.itemMode()) {
            sender.sendMessage("&c/baltop is disabled for ITEM currency.");
            return true;
        }
        List<Map.Entry<UUID, Double>> entries;
        entries = new ArrayList<>(ctx.economy().balances().entrySet());
        entries.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        sender.sendMessage("&6--- Balance Top ---");
        int shown = 0;
        for (Map.Entry<UUID, Double> entry : entries) {
            String name = adapter.player(entry.getKey()).map(CasinoPlayer::name)
                    .orElse(entry.getKey().toString().substring(0, 8));
            sender.sendMessage("&e" + (++shown) + ". &f" + name + " &7- &a" + formatAmount(ctx, entry.getValue()));
            if (shown >= 10) break;
        }
        if (shown == 0) sender.sendMessage("&7No saved balances yet.");
        return true;
    }

    private static boolean manage(PlatformAdapter adapter, GameContext ctx, CasinoSender sender, String[] args) {
        if (ctx.itemMode()) {
            sender.sendMessage("&c/eco is disabled for ITEM currency. Items cannot be given, taken or set by economy commands.");
            return true;
        }
        if (!sender.hasPermission("casino.economy.admin")) { sender.sendMessage("&cNo permission."); return true; }
        if (args.length < 3) { sender.sendMessage("&eUsage: /eco <give|take|set> <player> <amount>"); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        CasinoPlayer target = adapter.player(args[1]).orElse(null);
        double value = amount(args[2]);
        if (target == null || value < 0) { sender.sendMessage("&cInvalid player or amount."); return true; }
        boolean ok;
        if (action.equals("give") || action.equals("add")) ok = ctx.economy().deposit(target.uuid(), value);
        else if (action.equals("take") || action.equals("remove")) ok = ctx.economy().withdraw(target.uuid(), value);
        else if (action.equals("set")) ok = ctx.economy().setBalance(target.uuid(), value);
        else { sender.sendMessage("&eUsage: /eco <give|take|set> <player> <amount>"); return true; }
        sender.sendMessage(ok ? "&aBalance updated: " + formatBalance(ctx, target) : "&cOperation failed.");
        return true;
    }

    private static String formatBalance(GameContext ctx, CasinoPlayer player) {
        return formatAmount(ctx, ctx.currencyBalance(player));
    }

    private static String formatAmount(GameContext ctx, double value) {
        if (ctx.itemMode()) {
            return String.valueOf((long) value) + " " + ctx.currencyName();
        }
        return ctx.economy().format(value);
    }

    private static double amount(String value) {
        try { double parsed = Double.parseDouble(value.replace(',', '.')); return Double.isFinite(parsed) ? parsed : -1; }
        catch (RuntimeException invalid) { return -1; }
    }
}
