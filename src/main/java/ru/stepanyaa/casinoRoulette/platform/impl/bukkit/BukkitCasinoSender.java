package ru.stepanyaa.casinoRoulette.platform.impl.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import ru.stepanyaa.casinoRoulette.platform.CasinoSender;

public class BukkitCasinoSender implements CasinoSender {

    protected final CommandSender sender;

    public BukkitCasinoSender(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public void sendMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(colorise(message));
    }

    public static String colorise(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return permission == null || permission.isEmpty() || sender.hasPermission(permission);
    }

    @Override
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    @Override
    public boolean isConsole() {
        return sender instanceof ConsoleCommandSender;
    }

    @Override
    public Object handle() {
        return sender;
    }
}
