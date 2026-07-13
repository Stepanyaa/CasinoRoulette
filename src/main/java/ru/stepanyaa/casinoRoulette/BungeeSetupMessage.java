/*
MIT License

Copyright (c) 2026 Stepanyaa

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package ru.stepanyaa.casinoRoulette;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class BungeeSetupMessage {

    public static boolean isAvailable() {
        try {
            Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void sendSetupMessage(CasinoRoulette plugin, Player p) {
        ConfigManager cm = plugin.getConfigManager();

        try {
            p.sendMessage(cm.getMessage("messages.setup.separator", "&6=============================================", new Object[0]));
            p.sendMessage(cm.getMessage("messages.setup.wizard_title", "&e [CasinoRoulette Setup Wizard]", new Object[0]));
            p.sendMessage(cm.getMessage("messages.setup.wizard_subtitle", "&f Let's configure the plugin to your liking!", new Object[0]));
            p.sendMessage("");

            if (plugin.getSetupStep() == 0) {
                p.sendMessage(cm.getMessage("messages.setup.step1", "&b Step 1: Select your preferred language:", new Object[0]));
                ComponentBuilder builder = new ComponentBuilder("   ");
                BaseComponent[] enBtn = TextComponent.fromLegacyText(cm.getMessage("messages.setup.btn_en", "&a&l[ English ]", new Object[0]));

                for(BaseComponent c : enBtn) {
                    c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casino setup lang en"));
                    c.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(cm.getMessage("messages.setup.hover_en", "&7Click to choose English", new Object[0]))));
                }

                builder.append(enBtn);
                builder.append(new TextComponent("  "));
                BaseComponent[] ruBtn = TextComponent.fromLegacyText(cm.getMessage("messages.setup.btn_ru", "&c&l[ Russian ]", new Object[0]));

                for(BaseComponent c : ruBtn) {
                    c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casino setup lang ru"));
                    c.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(cm.getMessage("messages.setup.hover_ru", "&7Нажмите, чтобы выбрать русский язык", new Object[0]))));
                }

                builder.append(ruBtn);
                p.spigot().sendMessage(builder.create());
            } else if (plugin.getSetupStep() == 1) {
                p.sendMessage(cm.getMessage("messages.setup.step2", "&b Step 2: Select server currency system:", new Object[0]));
                boolean vaultInstalled = plugin.getServer().getPluginManager().getPlugin("Vault") != null;
                boolean econInstalled = plugin.setupEconomy();
                boolean ecoReady = vaultInstalled && econInstalled;
                String vaultStatus = ecoReady ? cm.getMessage("messages.setup.status_eco_found", "&a (Economy system detected)", new Object[0]) : cm.getMessage("messages.setup.status_eco_missing", "&c (No economy plugins found!)", new Object[0]);
                String btnVaultText = ecoReady ? cm.getMessage("messages.setup.btn_vault_ready", "&a&l[ Vault Economy ]", new Object[0]) : cm.getMessage("messages.setup.btn_vault_missing", "&7&l[ Vault Economy ]", new Object[0]);
                BaseComponent[] vaultBtn = TextComponent.fromLegacyText(btnVaultText);
                String hoverVault = ecoReady ? cm.getMessage("messages.setup.hover_vault", "&7Use server digital balance", new Object[0]) : cm.getMessage("messages.setup.hover_vault_warn", "&7Use server digital balance &c(Warning: Missing dependencies!)", new Object[0]);

                for(BaseComponent c : vaultBtn) {
                    c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casino setup eco vault"));
                    c.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hoverVault)));
                }

                BaseComponent[] itemBtn = TextComponent.fromLegacyText(cm.getMessage("messages.setup.btn_item", "&6&l[ Item Resource ]", new Object[0]));

                for(BaseComponent c : itemBtn) {
                    c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casino setup eco item"));
                    c.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(cm.getMessage("messages.setup.hover_item", "&7Use items (e.g. Diamonds) from inventory", new Object[0]))));
                }

                p.spigot().sendMessage(vaultBtn);
                p.sendMessage(cm.getMessage("messages.setup.status", "&8   Status: %status%", new Object[]{"status", vaultStatus}));
                p.sendMessage("");
                p.spigot().sendMessage(itemBtn);
                p.sendMessage(cm.getMessage("messages.setup.status", "&8   Status: %status%", new Object[]{"status", cm.getMessage("messages.setup.status_item", "&a(Always available - customizable in config.yml)", new Object[0])}));
            }

            p.sendMessage(cm.getMessage("messages.setup.separator", "&6=============================================", new Object[0]));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Bungee setup message, falling back to text: " + e.getMessage());
            sendSimpleSetupMessage(plugin, p);
        }
    }

    private static void sendSimpleSetupMessage(CasinoRoulette plugin, Player p) {
        ConfigManager cm = plugin.getConfigManager();
        p.sendMessage(cm.getMessage("messages.setup.separator", "&6=============================================", new Object[0]));
        p.sendMessage(cm.getMessage("messages.setup.wizard_title", "&e [CasinoRoulette Setup Wizard]", new Object[0]));
        p.sendMessage(cm.getMessage("messages.setup.wizard_subtitle", "&f Let's configure the plugin to your liking!", new Object[0]));
        p.sendMessage("");

        if (plugin.getSetupStep() == 0) {
            p.sendMessage(cm.getMessage("messages.setup.step1", "&b Step 1: Select your preferred language:", new Object[0]));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&l[ English ] &7- Type: /casino setup lang en"));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c&l[ Russian ] &7- Type: /casino setup lang ru"));
        } else if (plugin.getSetupStep() == 1) {
            p.sendMessage(cm.getMessage("messages.setup.step2", "&b Step 2: Select server currency system:", new Object[0]));
            boolean vaultInstalled = plugin.getServer().getPluginManager().getPlugin("Vault") != null;
            boolean econInstalled = plugin.setupEconomy();
            boolean ecoReady = vaultInstalled && econInstalled;
            String vaultStatus = ecoReady ? cm.getMessage("messages.setup.status_eco_found", "&a (Economy system detected)", new Object[0]) : cm.getMessage("messages.setup.status_eco_missing", "&c (No economy plugins found!)", new Object[0]);

            if (ecoReady) {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&l[ Vault Economy ] &7- Type: /casino setup eco vault"));
            } else {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7&l[ Vault Economy ] &7- Type: /casino setup eco vault"));
            }
            p.sendMessage(cm.getMessage("messages.setup.status", "&8   Status: %status%", new Object[]{"status", vaultStatus}));
            p.sendMessage("");
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&l[ Item Resource ] &7- Type: /casino setup eco item"));
            p.sendMessage(cm.getMessage("messages.setup.status", "&8   Status: %status%", new Object[]{"status", cm.getMessage("messages.setup.status_item", "&a(Always available - customizable in config.yml)", new Object[0])}));
        }

        p.sendMessage(cm.getMessage("messages.setup.separator", "&6=============================================", new Object[0]));
    }
}