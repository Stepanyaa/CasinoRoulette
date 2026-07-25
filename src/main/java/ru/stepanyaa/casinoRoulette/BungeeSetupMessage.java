package ru.stepanyaa.casinoRoulette;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class BungeeSetupMessage {
    private static final String[][] LANGUAGES = new String[][]{
            {"en", "setup.language.en"},
            {"ru", "setup.language.ru"},
            {"de", "setup.language.de"},
            {"fr", "setup.language.fr"},
            {"tr", "setup.language.tr"},
            {"pl", "setup.language.pl"},
            {"pt_br", "setup.language.pt_br"}
    };

    public static boolean isAvailable() {
        try { Class.forName("net.md_5.bungee.api.chat.BaseComponent"); return true; }
        catch (ClassNotFoundException e) { return false; }
    }

    public static void sendSetupMessage(CasinoRoulette plugin, Player p) {
        try { sendRichSetupMessage(plugin, p); }
        catch (Exception e) {
            plugin.getLogger().warning("Failed to send clickable setup message, falling back to text: " + e.getMessage());
            sendSimpleSetupMessage(plugin, p);
        }
    }

    private static void sendRichSetupMessage(CasinoRoulette plugin, Player p) {
        ConfigManager cm = plugin.getConfigManager();
        p.sendMessage(cm.getMessage("messages.setup.separator", "&6============================================="));
        p.sendMessage(cm.getMessage("messages.setup.wizard_title", "&e[ CasinoRoulette Setup ]"));
        p.sendMessage(cm.getMessage("messages.setup.wizard_subtitle", "&fLet's configure the plugin!"));
        p.sendMessage("");
        if (plugin.getSetupStep() == 0) {
            p.sendMessage(cm.getMessage("messages.setup.step1", "&bStep 1: choose the plugin language:"));
            ComponentBuilder builder = new ComponentBuilder("   ");
            for (int i = 0; i < LANGUAGES.length; i++) {
                String[] lang = LANGUAGES[i];
                BaseComponent[] button = TextComponent.fromLegacyText(cm.getMessage("messages." + lang[1], "&e[ " + lang[0] + " ]"));
                for (BaseComponent c : button) {
                    c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casino setup lang " + lang[0]));
                    c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(cm.getMessage("messages.setup.click", "&7Click"))));
                }
                builder.append(button);
                if (i != LANGUAGES.length - 1) builder.append(new TextComponent("  "));
            }
            p.spigot().sendMessage(builder.create());
        } else if (plugin.getSetupStep() == 1) {
            p.sendMessage(cm.getMessage("messages.setup.step2", "&bStep 2: choose the server currency system:"));
            sendEconomyStep(plugin, p);
        }
        p.sendMessage(cm.getMessage("messages.setup.separator", "&6============================================="));
        }

    private static void sendSimpleSetupMessage(CasinoRoulette plugin, Player p) {
        ConfigManager cm = plugin.getConfigManager();
        p.sendMessage(cm.getMessage("messages.setup.separator", "&6============================================="));
        p.sendMessage(cm.getMessage("messages.setup.wizard_title", "&e[ CasinoRoulette Setup ]"));
        p.sendMessage(cm.getMessage("messages.setup.wizard_subtitle", "&fLet's configure the plugin!"));
        p.sendMessage("");
        if (plugin.getSetupStep() == 0) {
            p.sendMessage(cm.getMessage("messages.setup.step1", "&bStep 1: choose the plugin language:"));
            for (String[] lang : LANGUAGES) p.sendMessage(cm.getMessage("messages." + lang[1], "&e[ " + lang[0] + " ]") + " " + cm.getMessage("messages.setup.command_hint", "&7/casino setup %type% %value%", "type", "lang", "value", lang[0]));
        } else if (plugin.getSetupStep() == 1) {
            p.sendMessage(cm.getMessage("messages.setup.step2", "&bStep 2: choose the server currency system:"));
            sendEconomyStep(plugin, p);
        }
        p.sendMessage(cm.getMessage("messages.setup.separator", "&6============================================="));
        }

    private static void sendEconomyStep(CasinoRoulette plugin, Player p) {
        ConfigManager cm = plugin.getConfigManager();

        boolean vaultInstalled = plugin.getServer().getPluginManager().getPlugin("Vault") != null;
        boolean econInstalled = plugin.setupEconomy();
        boolean ecoReady = vaultInstalled && econInstalled;
        ComponentBuilder builder = new ComponentBuilder("   ");
        BaseComponent[] vault = TextComponent.fromLegacyText(cm.getMessage(ecoReady ? "messages.setup.eco_vault" : "messages.setup.eco_vault_disabled", ecoReady ? "&a&l[ Vault Economy ]" : "&7&l[ Vault Economy ]"));
        for (BaseComponent c : vault) { c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casino setup eco vault")); c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(cm.getMessage("messages.setup.click", "&7Click")))); }
        BaseComponent[] item = TextComponent.fromLegacyText(cm.getMessage("messages.setup.eco_item", "&6&l[ Item Resource ]"));
        for (BaseComponent c : item) { c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casino setup eco item")); c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(cm.getMessage("messages.setup.click", "&7Click")))); }
        builder.append(vault).append(new TextComponent("  ")).append(item);
        p.spigot().sendMessage(builder.create());
    }
}
