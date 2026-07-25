package ru.stepanyaa.casinoRoulette;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class DailyWheel {

    private static final int SIZE = 45;

    private static final int[] RING = {10, 11, 12, 13, 14, 15, 16, 25, 34, 33, 32, 31, 30, 29, 28, 19};

    private static final int CENTER = 22;
    private static final int SPIN_SLOT = 40;
    private static final int BACK_SLOT = 36;

    private static final int SLOWDOWN_FRAMES = 14;

    private final CasinoRoulette plugin;

    private final Set<UUID> spinning = new HashSet<>();

    private final Random random = new Random();

    public DailyWheel(CasinoRoulette plugin) {
        this.plugin = plugin;
    }

    private String msg(String path, String def, Object... placeholders) {
        return plugin.getConfigManager().getMessage(path, def, placeholders);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private ConfigurationSection rewardsSection() {
        return plugin.getExtra("dailywheel.yml").get().getConfigurationSection("rewards");
    }

    private long cooldownMillis() {
        return plugin.getExtra("dailywheel.yml").get().getLong("settings.cooldown-hours", 24) * 3600000L;
    }

    private long cooldownLeft(UUID uuid) {
        return plugin.getDb().getLastDaily(uuid) + cooldownMillis() - System.currentTimeMillis();
    }

    private String formatTime(long millis) {
        long minutes = Math.max(0L, millis) / 60000L;
        long hours = minutes / 60L;
        minutes %= 60L;
        return msg("common.time_short", "%hours%h %minutes%m", "hours", hours, "minutes", minutes);
    }

    private static final class Reward {
        final String id;
        final String type;
        final int min;
        final int max;
        final double chance;
        final Material icon;
        final String display;
        final String message;
        final List<String> commands;

        Reward(String id, String type, int min, int max, double chance, Material icon,
               String display, String message, List<String> commands) {
            this.id = id;
            this.type = type;
            this.min = min;
            this.max = max;
            this.chance = chance;
            this.icon = icon;
            this.display = display;
            this.message = message;
            this.commands = commands;
        }
    }

    private List<Reward> loadRewards() {
        List<Reward> rewards = new ArrayList<>();
        ConfigurationSection section = rewardsSection();
        if (section == null) {
            return rewards;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String type = entry.getString("type", "chips").trim().toLowerCase(Locale.ROOT);
            int min = Math.max(0, entry.getInt("min", 1));
            int max = Math.max(min, entry.getInt("max", min));
            double chance = Math.max(0.0D, entry.getDouble("chance", 1.0D));
            Material icon = resolveIcon(entry, type);
            String display = entry.getString("display", prettify(id));
            rewards.add(new Reward(id, type, min, max, chance, icon, display,
                    entry.getString("message", null), entry.getStringList("commands")));
        }
        return rewards;
    }

    private Material resolveIcon(ConfigurationSection entry, String type) {
        String configured = entry.getString("icon", entry.getString("material", null));
        if (configured != null) {
            Material material = Material.matchMaterial(configured.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                return material;
            }
            plugin.getLogger().warning("dailywheel.yml: unknown material '" + configured
                    + "' for reward '" + entry.getName() + "', using a default icon instead.");
        }
        if (type.equals("money")) {
            return Material.GOLD_INGOT;
        }
        if (type.equals("chips")) {
            return Material.SUNFLOWER;
        }
        return Material.CHEST;
    }

    private String prettify(String id) {
        String cleaned = id.replace('_', ' ').replace('-', ' ').trim();
        return cleaned.isEmpty() ? id : Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private String displayName(Reward reward) {
        return msg("gui.daily.rewards." + reward.id, reward.display);
    }

    private Reward draw(List<Reward> rewards) {
        double total = 0.0D;
        for (Reward reward : rewards) {
            total += reward.chance;
        }
        if (total <= 0.0D) {
            return rewards.get(random.nextInt(rewards.size()));
        }
        double roll = random.nextDouble() * total;
        for (Reward reward : rewards) {
            roll -= reward.chance;
            if (roll < 0.0D) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    private int chancePercent(Reward reward, List<Reward> all) {
        double total = 0.0D;
        for (Reward other : all) {
            total += other.chance;
        }
        return total <= 0.0D ? 0 : (int) Math.round(reward.chance / total * 100.0D);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, msg("gui.titles.daily", "&aFree Fortune Wheel"));
        render(player, inv, loadRewards(), -1);
        player.openInventory(inv);
    }

    private void render(Player player, Inventory inv, List<Reward> rewards, int highlight) {
        for (int slot = 0; slot < SIZE; slot++) {
            inv.setItem(slot, plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        if (rewards.isEmpty()) {
            inv.setItem(CENTER, plugin.createItem(Material.BARRIER,
                    msg("messages.daily.no_rewards", "&cRewards are not configured")));
            inv.setItem(BACK_SLOT, plugin.createItem(Material.ARROW, msg("common.back", "&cBack")));
            return;
        }

        for (int i = 0; i < RING.length; i++) {
            Reward reward = rewards.get(i % rewards.size());
            boolean lit = i == highlight;
            ItemStack item = plugin.createItem(reward.icon,
                    color((lit ? "&l> " : "&f") + displayName(reward)),
                    msg("gui.daily.reward_lore", "&7Chance: &f%chance%%",
                            "chance", chancePercent(reward, rewards)));
            if (lit) {
                plugin.addGlow(item);
            }
            inv.setItem(RING[i], item);
        }

        if (highlight >= 0) {
            Reward current = rewards.get(highlight % rewards.size());
            inv.setItem(CENTER, plugin.createItem(current.icon,
                    color("&e&l" + displayName(current)),
                    msg("gui.daily.spinning", "&7Spinning...")));
        } else {
            inv.setItem(CENTER, plugin.createItem(Material.NETHER_STAR,
                    msg("gui.daily.pointer", "&e&lPrize"),
                    msg("gui.daily.pointer_lore", "&7The wheel stops here")));
        }

        long left = cooldownLeft(player.getUniqueId());
        if (spinning.contains(player.getUniqueId())) {
            inv.setItem(SPIN_SLOT, plugin.createItem(Material.CLOCK,
                    msg("gui.daily.spinning", "&e&lSpinning...")));
        } else if (left > 0) {
            inv.setItem(SPIN_SLOT, plugin.createItem(Material.REDSTONE_BLOCK,
                    msg("gui.daily.cooldown", "&cCooldown"),
                    msg("gui.daily.cooldown_lore", "&7Next spin in: %time%", "time", formatTime(left))));
        } else {
            inv.setItem(SPIN_SLOT, plugin.createItem(Material.EMERALD_BLOCK,
                    msg("gui.daily.spin", "&a&lSPIN"),
                    msg("gui.daily.spin_lore", "&7Free once every %hours%h",
                            "hours", cooldownMillis() / 3600000L)));
        }

        inv.setItem(BACK_SLOT, plugin.createItem(Material.ARROW, msg("common.back", "&cBack")));
    }

    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == BACK_SLOT) {
            plugin.playMenuSound(player);
            plugin.getGui().openMainMenu(player);
            return;
        }
        if (slot != SPIN_SLOT) {
            return;
        }
        spin(player);
    }

    private void spin(Player player) {
        UUID uuid = player.getUniqueId();
        if (spinning.contains(uuid)) {
            player.sendMessage(msg("messages.daily.already_spinning", "&cThe wheel is already spinning!"));
            return;
        }

        long left = cooldownLeft(uuid);
        if (left > 0) {
            if (plugin.getExtra("dailywheel.yml").get().getBoolean("settings.chat-cooldown-message", true)) {
                player.sendMessage(color(msg("messages.daily.cooldown",
                        "&cNext attempt in %time%", "time", formatTime(left))));
            }
            open(player);
            return;
        }

        final List<Reward> rewards = loadRewards();
        if (rewards.isEmpty()) {
            player.sendMessage(msg("messages.daily.no_rewards", "&cRewards are not configured"));
            return;
        }

        final Reward winner = draw(rewards);
        final int winnerIndex = rewards.indexOf(winner);

        List<Integer> landings = new ArrayList<>();
        for (int i = 0; i < RING.length; i++) {
            if (i % rewards.size() == winnerIndex) {
                landings.add(i);
            }
        }
        final int target = landings.get(random.nextInt(landings.size()));
        final int laps = 2 + random.nextInt(2);
        final int totalSteps = laps * RING.length + target;

        spinning.add(uuid);
        final Inventory inv = player.getOpenInventory().getTopInventory();

        final int[] state = {0, 0};
        final long tick = Math.max(1L, plugin.getExtra("dailywheel.yml").get()
                .getLong("settings.animation-tick", 1L));

        CasinoScheduler.timerAtEntity(player, 0L, tick, task -> {
            if (!player.isOnline()) {

                spinning.remove(uuid);
                task.cancel();
                return;
            }
            if (state[1] > 0) {
                state[1]--;
                return;
            }

            int position = state[0] % RING.length;
            render(player, inv, rewards, position);
            plugin.playSpinSound(player, Math.min(2.0F, 0.5F + state[0] * 0.02F));

            state[0]++;
            if (state[0] > totalSteps) {
                finish(player, inv, rewards, winner, target);
                spinning.remove(uuid);
                task.cancel();
                return;
            }
            state[1] = frameDelay(totalSteps - state[0]);
        });
    }

    private int frameDelay(int remainingSteps) {
        if (remainingSteps > SLOWDOWN_FRAMES) {
            return 0;
        }
        return (SLOWDOWN_FRAMES - remainingSteps) / 2;
    }

    private void finish(Player player, Inventory inv, List<Reward> rewards, Reward winner, int target) {
        render(player, inv, rewards, target);
        plugin.playWinSound(player);
        give(player, winner);
    }

    private void give(Player player, Reward reward) {
        UUID uuid = player.getUniqueId();
        int amount = reward.min + random.nextInt(Math.max(1, reward.max - reward.min + 1));
        String currency = plugin.getCurrencyName();
        String defaultMessage;

        if (reward.type.equals("money")) {

            if (!plugin.depositBalance(player, amount)) {
                plugin.getLogger().warning("Daily wheel reward '" + reward.id + "' could not be paid to "
                        + player.getName() + ": no economy is available. The free spin was not consumed.");
                player.sendMessage(color(msg("messages.economy_unavailable",
                        "&cEconomy is unavailable, your reward could not be paid.")));
                return;
            }
            defaultMessage = msg("messages.daily.reward_money", "&aYou received %amount% %currency%!",
                    "amount", plugin.formatNumber(amount), "currency", currency);
        } else if (reward.type.equals("chips")) {
            plugin.addChips(uuid, amount);
            defaultMessage = msg("messages.daily.reward_chips", "&aYou received %amount% chips!",
                    "amount", plugin.formatNumber(amount));
        } else {
            Map<Integer, ItemStack> overflow =
                    player.getInventory().addItem(new ItemStack(reward.icon, amount));
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            defaultMessage = msg("messages.daily.reward_item", "&bYou received %reward% x%amount%!",
                    "reward", displayName(reward), "amount", amount);
        }

        plugin.getDb().setDaily(uuid, System.currentTimeMillis(), reward.id, amount);

        for (String command : reward.commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(amount)));
        }

        String custom = reward.message;
        String text = custom == null || custom.trim().isEmpty() ? defaultMessage : custom
                .replace("%reward%", displayName(reward))
                .replace("%amount%", String.valueOf(amount))
                .replace("%currency%", currency);
        player.sendMessage(color(text));
    }
}
