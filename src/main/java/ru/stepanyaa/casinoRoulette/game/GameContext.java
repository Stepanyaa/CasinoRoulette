package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.config.ConfigService;
import ru.stepanyaa.casinoRoulette.config.YamlDocument;
import ru.stepanyaa.casinoRoulette.economy.EconomyProvider;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.platform.PlatformAdapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class GameContext {

    public static final String CONFIG = "config.yml";
    public static final String WHEEL = "wheel.yml";
    public static final String DAILY_WHEEL = "dailywheel.yml";
    public static final String LEADERBOARD = "leaderboard.yml";
    public static final String MENUS = "menus.yml";

    private final PlatformAdapter adapter;
    private final ConfigService configs;
    private final Messages messages;
    private final ChipsBank chips;
    private final StatsStore stats;

    private final Map<String, YamlDocument> documents = new ConcurrentHashMap<>();

    public GameContext(PlatformAdapter adapter, ConfigService configs, Messages messages,
                       ChipsBank chips, StatsStore stats) {
        this.adapter = adapter;
        this.configs = configs;
        this.messages = messages;
        this.chips = chips;
        this.stats = stats;
    }

    public PlatformAdapter adapter() {
        return adapter;
    }

    public Logger logger() {
        return adapter.logger();
    }

    public Messages messages() {
        return messages;
    }

    public ChipsBank chips() {
        return chips;
    }

    public StatsStore stats() {
        return stats;
    }

    public EconomyProvider economy() {
        return adapter.economy();
    }

    public Optional<CasinoPlayer> player(UUID uuid) {
        return adapter.player(uuid);
    }

    public YamlDocument config() {
        return document(CONFIG);
    }

    public YamlDocument document(String name) {
        YamlDocument cached = documents.get(name);
        if (cached != null) {
            return cached;
        }
        YamlDocument loaded = YamlDocument.load(configs.install(name));
        YamlDocument raced = documents.putIfAbsent(name, loaded);
        return raced == null ? loaded : raced;
    }

    public void reload() {
        documents.clear();
    }

    public String msg(String path, String def, Object... placeholders) {
        return messages.get(path, def, placeholders);
    }

    public List<String> msgList(String path, List<String> def, Object... placeholders) {
        return messages.getList(path, def, placeholders);
    }

    public void tell(CasinoPlayer player, String path, String def, Object... placeholders) {
        if (player != null && player.isOnline()) {
            player.sendMessage(messages.chat(path, def, placeholders));
        }
    }

    public String formatNumber(long number) {
        return String.format("%,d", number).replace(',', ' ');
    }

    public String currencyName() {
        String custom = config().getString("settings.custom-currency-name", "").trim();
        if (!custom.isEmpty()) {
            return custom;
        }
        if (itemMode()) {
            return itemCurrencyMaterial().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        }
        String fromEconomy = economy() == null ? "" : String.valueOf(economy().currencyName()).trim();
        if (!fromEconomy.isEmpty() && !fromEconomy.equals("null")) {
            return fromEconomy;
        }
        return msg("common.currency_name", "coins");
    }

    public String chipsName() {
        return msg("common.chips", "chips");
    }

    public boolean itemMode() {
        String mode = config().getString("settings.economy-mode",
                config().getString("economy-mode", "AUTO"));
        mode = mode == null ? "" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        return mode.equals("ITEM") || mode.equals("ITEMS") || mode.equals("RESOURCE");
    }

    public String itemCurrencyMaterial() {
        return config().getString("settings.item-resource",
                config().getString("item-resource", "DIAMOND"));
    }

    public double currencyBalance(CasinoPlayer player) {
        if (player == null) return 0.0D;
        return itemMode() ? player.countItem(itemCurrencyMaterial())
                : economy() == null ? 0.0D : economy().balance(player.uuid());
    }

    public boolean withdrawCurrency(CasinoPlayer player, int amount) {
        if (player == null || amount <= 0) return amount <= 0;
        return itemMode() ? player.takeItem(itemCurrencyMaterial(), amount)
                : economy() != null && economy().withdraw(player.uuid(), amount);
    }

    public boolean depositCurrency(CasinoPlayer player, int amount) {
        if (player == null || amount <= 0) return amount <= 0;
        if (!itemMode()) return economy() != null && economy().deposit(player.uuid(), amount);
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(64, remaining);
            player.giveItem(CasinoItem.of(itemCurrencyMaterial()).amount(stack).build());
            remaining -= stack;
        }
        return true;
    }

    public int minBet() {
        boolean item = itemMode();
        return config().getInt("economy." + (item ? "item" : "vault") + ".min_bet", item ? 10 : 1000);
    }

    public int maxBet() {
        boolean item = itemMode();
        return Math.max(minBet(),
                config().getInt("economy." + (item ? "item" : "vault") + ".max_bet", item ? 1000 : 10000));
    }

    public int minExchange() {
        boolean item = itemMode();
        return config().getInt("economy." + (item ? "item" : "vault") + ".min_exchange", item ? 10 : 100);
    }

    public String betCurrencyName() {
        return itemMode() ? currencyName() : chipsName();
    }

    public CasinoInventory inventory(String guiId, String title, int size) {
        return adapter.createInventory(title, size, guiId);
    }

    public CasinoItem item(String materialId, String name, List<String> lore) {
        return CasinoItem.of(materialId).name(name).lore(lore).hideAttributes(true).build();
    }

    public CasinoItem item(String materialId, String name, String... lore) {
        return CasinoItem.of(materialId).name(name).lore(lore).hideAttributes(true).build();
    }

    public CasinoItem filler() {
        String material = config().getString("main-menu.filler-material", "GRAY_STAINED_GLASS_PANE");
        return CasinoItem.of(material).name(" ").hideAttributes(true).build();
    }

    public void sound(CasinoPlayer player, String key, String fallbackId, float pitch) {
        if (player == null || !player.isOnline()) {
            return;
        }
        YamlDocument config = config();
        if (!config.getBoolean("sounds.enabled", true)) {
            return;
        }
        String id = config.getString("sounds." + key, fallbackId);
        if (id == null || id.isEmpty() || id.equalsIgnoreCase("none")) {
            return;
        }
        player.playSound(id, 1.0f, pitch);
    }

    public void playClick(CasinoPlayer player) {
        sound(player, "click", "UI_BUTTON_CLICK", 1.0f);
    }

    public void playMenu(CasinoPlayer player) {
        sound(player, "open_menu", "BLOCK_CHEST_OPEN", 1.0f);
    }

    public void playSpin(CasinoPlayer player, float pitch) {
        sound(player, "spin", "BLOCK_NOTE_BLOCK_PLING", pitch);
    }

    public void playWin(CasinoPlayer player) {
        sound(player, "win", "ENTITY_PLAYER_LEVELUP", 1.0f);
    }

    public void playLose(CasinoPlayer player) {
        sound(player, "lose", "ENTITY_VILLAGER_NO", 1.0f);
    }
}
