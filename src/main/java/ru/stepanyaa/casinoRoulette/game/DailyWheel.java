
package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.config.YamlDocument;
import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class DailyWheel {

    public static final String GUI_ID = "casino:daily";

    private static final int SIZE = 45;

    private static final int[] RING = {10, 11, 12, 13, 14, 15, 16, 25, 34, 33, 32, 31, 30, 29, 28, 19};

    private static final int CENTER = 22;
    private static final int SPIN_SLOT = 40;
    private static final int BACK_SLOT = 36;

    private static final int SLOWDOWN_FRAMES = 14;

    private final GameContext ctx;
    private final Random random = new Random();

    private final Set<UUID> spinning = ConcurrentHashMap.newKeySet();

    private final Map<UUID, CasinoInventory> screens = new ConcurrentHashMap<>();

    private final Set<String> warnedIcons = ConcurrentHashMap.newKeySet();

    private Consumer<CasinoPlayer> mainMenu = player -> { };

    public DailyWheel(GameContext ctx) {
        this.ctx = ctx;
    }

    public void onBack(Consumer<CasinoPlayer> mainMenu) {
        if (mainMenu != null) {
            this.mainMenu = mainMenu;
        }
    }

    private YamlDocument daily() {
        return ctx.document(GameContext.DAILY_WHEEL);
    }

    private long cooldownMillis() {
        return daily().getLong("settings.cooldown-hours", 24L) * 3600000L;
    }

    private long cooldownLeft(UUID uuid) {
        return ctx.stats().lastDaily(uuid) + cooldownMillis() - System.currentTimeMillis();
    }

    private String formatTime(long millis) {
        long minutes = Math.max(0L, millis) / 60000L;
        long hours = minutes / 60L;
        minutes %= 60L;
        return ctx.msg("common.time_short", "%hours%h %minutes%m", "hours", hours, "minutes", minutes);
    }

    private static final class Reward {
        final String id;
        final String type;
        final int min;
        final int max;
        final double chance;
        final String icon;
        final String display;
        final String message;
        final List<String> commands;

        Reward(String id, String type, int min, int max, double chance, String icon,
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
        YamlDocument document = daily();
        for (String id : document.keys("rewards")) {
            String base = "rewards." + id + ".";
            String type = document.getString(base + "type", "chips").trim().toLowerCase(Locale.ROOT);
            int min = Math.max(0, document.getInt(base + "min", 1));
            int max = Math.max(min, document.getInt(base + "max", min));
            double chance = Math.max(0.0D, document.getDouble(base + "chance", 1.0D));
            rewards.add(new Reward(id, type, min, max, chance,
                    resolveIcon(document, base, id, type),
                    document.getString(base + "display", prettify(id)),
                    document.getString(base + "message", null),
                    document.getStringList(base + "commands")));
        }
        return rewards;
    }

    private String resolveIcon(YamlDocument document, String base, String id, String type) {
        String configured = document.getString(base + "icon", document.getString(base + "material", null));
        if (configured != null && !configured.trim().isEmpty()) {
            String material = configured.trim().toUpperCase(Locale.ROOT);
            if (ctx.adapter().materialExists(material)) {
                return material;
            }
            if (warnedIcons.add(id)) {
                ctx.logger().warning("dailywheel.yml: unknown material '" + configured
                        + "' for reward '" + id + "', using a default icon instead.");
            }
        }
        if (type.equals("money")) {
            return "GOLD_INGOT";
        }
        if (type.equals("chips")) {
            return "SUNFLOWER";
        }
        return "CHEST";
    }

    private String prettify(String id) {
        String cleaned = id.replace('_', ' ').replace('-', ' ').trim();
        return cleaned.isEmpty() ? id : Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private String displayName(Reward reward) {
        return ctx.msg("gui.daily.rewards." + reward.id, reward.display);
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

    public void open(CasinoPlayer player) {
        CasinoInventory screen = ctx.inventory(GUI_ID,
                ctx.msg("gui.titles.daily", "&aFree Fortune Wheel"), SIZE);
        screens.put(player.uuid(), screen);
        render(player.uuid(), screen, loadRewards(), -1);
        player.openInventory(screen);
        ctx.playMenu(player);
    }

    private void render(UUID uuid, CasinoInventory screen, List<Reward> rewards, int highlight) {
        CasinoItem filler = ctx.item("BLACK_STAINED_GLASS_PANE", " ");
        for (int slot = 0; slot < SIZE; slot++) {
            screen.setItem(slot, filler);
        }

        if (rewards.isEmpty()) {
            screen.setItem(CENTER, ctx.item("BARRIER",
                    ctx.msg("messages.daily.no_rewards", "&cRewards are not configured")));
            screen.setItem(BACK_SLOT, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));
            screen.refresh();
            return;
        }

        for (int i = 0; i < RING.length; i++) {
            Reward reward = rewards.get(i % rewards.size());
            boolean lit = i == highlight;
            screen.setItem(RING[i], CasinoItem.of(reward.icon)
                    .name((lit ? "&l> " : "&f") + displayName(reward))
                    .lore(ctx.msg("gui.daily.reward_lore", "&7Chance: &f%chance%%",
                            "chance", chancePercent(reward, rewards)))
                    .glowing(lit)
                    .hideAttributes(true)
                    .build());
        }

        if (highlight >= 0) {
            Reward current = rewards.get(highlight % rewards.size());
            screen.setItem(CENTER, ctx.item(current.icon,
                    "&e&l" + displayName(current),
                    ctx.msg("gui.daily.spinning", "&7Spinning...")));
        } else {
            screen.setItem(CENTER, ctx.item("NETHER_STAR",
                    ctx.msg("gui.daily.pointer", "&e&lPrize"),
                    ctx.msg("gui.daily.pointer_lore", "&7The wheel stops here")));
        }

        long left = cooldownLeft(uuid);
        if (spinning.contains(uuid)) {
            screen.setItem(SPIN_SLOT, ctx.item("CLOCK", ctx.msg("gui.daily.spinning", "&e&lSpinning...")));
        } else if (left > 0) {
            screen.setItem(SPIN_SLOT, ctx.item("REDSTONE_BLOCK",
                    ctx.msg("gui.daily.cooldown", "&cCooldown"),
                    ctx.msg("gui.daily.cooldown_lore", "&7Next spin in: %time%",
                            "time", formatTime(left))));
        } else {
            screen.setItem(SPIN_SLOT, ctx.item("EMERALD_BLOCK",
                    ctx.msg("gui.daily.spin", "&a&lSPIN"),
                    ctx.msg("gui.daily.spin_lore", "&7Free once every %hours%h",
                            "hours", cooldownMillis() / 3600000L)));
        }

        screen.setItem(BACK_SLOT, ctx.item("ARROW", ctx.msg("common.back", "&cBack")));
        screen.refresh();
    }

    public void handleClick(CasinoEvents.InventoryClick event) {
        if (!GUI_ID.equals(event.guiId()) || !event.isTopInventory()) {
            return;
        }
        CasinoPlayer player = event.player();

        if (event.slot() == BACK_SLOT) {
            ctx.playMenu(player);
            mainMenu.accept(player);
            return;
        }
        if (event.slot() == SPIN_SLOT) {
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
        String prefix = ctx.messages().prefix();

        if (spinning.contains(uuid)) {
            player.sendMessage(prefix + ctx.msg("messages.daily.already_spinning",
                    "&cThe wheel is already spinning!"));
            return;
        }

        long left = cooldownLeft(uuid);
        if (left > 0) {
            if (daily().getBoolean("settings.chat-cooldown-message", true)) {
                player.sendMessage(prefix + ctx.msg("messages.daily.cooldown",
                        "&cNext attempt in %time%", "time", formatTime(left)));
            }
            open(player);
            return;
        }

        final List<Reward> rewards = loadRewards();
        if (rewards.isEmpty()) {
            player.sendMessage(prefix + ctx.msg("messages.daily.no_rewards",
                    "&cRewards are not configured"));
            return;
        }

        final CasinoInventory screen = screens.get(uuid);
        if (screen == null) {
            open(player);
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

        final int[] state = {0, 0};
        final long tick = Math.max(1L, daily().getLong("settings.animation-tick", 1L));

        CasinoScheduler.timerAtEntity(player.handle(), 0L, tick, task -> {
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
            render(uuid, screen, rewards, position);
            ctx.playSpin(player, Math.min(2.0F, 0.5F + state[0] * 0.02F));

            state[0]++;
            if (state[0] > totalSteps) {
                task.cancel();
                render(uuid, screen, rewards, target);
                ctx.playWin(player);
                give(player, winner);
                spinning.remove(uuid);
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

    private void give(CasinoPlayer player, Reward reward) {
        UUID uuid = player.uuid();
        int amount = reward.min + random.nextInt(Math.max(1, reward.max - reward.min + 1));
        String currency = ctx.currencyName();
        String defaultMessage;

        if (reward.type.equals("money")) {

            if (ctx.economy() == null || !ctx.economy().deposit(uuid, amount)) {
                ctx.logger().warning("Daily wheel reward '" + reward.id + "' could not be paid to "
                        + player.name() + ": no economy is available. The free spin was not consumed.");
                player.sendMessage(ctx.messages().prefix() + ctx.msg("messages.economy_unavailable",
                        "&cEconomy is unavailable, your reward could not be paid."));
                return;
            }
            defaultMessage = ctx.msg("messages.daily.reward_money", "&aYou received %amount% %currency%!",
                    "amount", ctx.formatNumber(amount), "currency", currency);
        } else if (reward.type.equals("chips")) {
            ctx.chips().add(uuid, amount);
            defaultMessage = ctx.msg("messages.daily.reward_chips", "&aYou received %amount% chips!",
                    "amount", ctx.formatNumber(amount));
        } else {
            player.giveItem(CasinoItem.of(reward.icon)
                    .amount(amount)
                    .name(displayName(reward))
                    .build());
            defaultMessage = ctx.msg("messages.daily.reward_item", "&bYou received %reward% x%amount%!",
                    "reward", displayName(reward), "amount", amount);
        }

        ctx.stats().setDaily(uuid, System.currentTimeMillis(), reward.id, amount);
        ctx.stats().increment(uuid, "daily_uses", 1, false);

        for (String command : reward.commands) {
            ctx.adapter().dispatchConsoleCommand(command
                    .replace("%player%", player.name())
                    .replace("%amount%", String.valueOf(amount)));
        }

        String custom = reward.message;
        String text = custom == null || custom.trim().isEmpty() ? defaultMessage : custom
                .replace("%reward%", displayName(reward))
                .replace("%amount%", String.valueOf(amount))
                .replace("%currency%", currency);
        player.sendMessage(text);
    }

    public void forget(UUID uuid) {
        spinning.remove(uuid);
        screens.remove(uuid);
    }
}
