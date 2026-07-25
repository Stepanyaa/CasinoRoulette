package ru.stepanyaa.casinoRoulette;

import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class WheelGame {
    private final CasinoRoulette plugin;
    private final Map<UUID, String> selectedColor = new HashMap<>();
    private final Map<UUID, Integer> bet = new HashMap<>();
    private final Set<UUID> spinning = new HashSet<>();
    private final Random random = new Random();
    private final int[] wheelSlots = {10,11,12,13,14,15,16,25,34,43,42,41,40,39,38,37,28,19};

    public WheelGame(CasinoRoulette plugin) { this.plugin = plugin; }

    private String msg(String path, String def, Object... ph) { return plugin.getConfigManager().getMessage(path, def, ph); }

    private String chipsName() { return plugin.getConfigManager().getMessage("common.chips", "chips"); }
    private int chips(UUID uuid) { return plugin.getPlayerChips().getOrDefault(uuid, 0); }

    private int minBet() { return Math.max(1, plugin.getExtra("wheel.yml").get().getInt("wheel.min-bet", 10)); }
    private int maxBet() { return Math.max(minBet(), plugin.getExtra("wheel.yml").get().getInt("wheel.max-bet", 10000)); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    public void open(Player player) {
        if (spinning.contains(player.getUniqueId())) {
            player.sendMessage(msg("messages.wheel.already_running", "&cИгра уже запущена!"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, msg("gui.titles.wheel", "&6Колесо фортуны"));
        draw(player, inv, 0, -1);
        player.openInventory(inv);
    }

    private void draw(Player player, Inventory inv, int offset, int pointerSlot) {
        for (int i = 0; i < 54; i++) inv.setItem(i, plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        List<String> sectors = sectors();
        for (int i = 0; i < wheelSlots.length; i++) {
            String sector = sectors.get((i + offset) % sectors.size());
            String prefix = wheelSlots[i] == pointerSlot ? "&l➤ " : "";
            inv.setItem(wheelSlots[i], plugin.createItem(material(sector), color(prefix + displayName(sector) + " &7x" + multiplier(sector))));
        }
        inv.setItem(22, plugin.createItem(Material.NETHER_STAR, msg("gui.wheel.pointer", "&e&lУказатель"), msg("gui.wheel.pointer_lore", "&7Победный сектор остановится здесь")));

        int start = plugin.getExtra("wheel.yml").get().getInt("gui.color-start-slot", 45);
        int idx = 0;
        for (String sector : configuredColors()) {
            int slot = start + idx++;
            if (slot > 48) break;
            boolean selected = sector.equalsIgnoreCase(selectedColor.get(player.getUniqueId()));
            inv.setItem(slot, plugin.createItem(material(sector),
                    msg("gui.wheel.color", "%selected%&e%color% &7x%multiplier%", "selected", selected ? "&a✔ " : "", "color", displayName(sector), "multiplier", multiplier(sector)),
                    msg("gui.wheel.color_lore", "&7Нажмите чтобы выбрать цвет")));
        }

        int currentBet = bet.getOrDefault(player.getUniqueId(), minBet());
        inv.setItem(50, plugin.createItem(Material.GOLD_NUGGET,
                msg("gui.wheel.bet", "&6Ставка: %amount% %currency%", "amount", plugin.formatNumber(currentBet), "currency", chipsName()),
                msg("gui.wheel.bet_lore", "&7ЛКМ + | ПКМ -"),
                msg("gui.wheel.balance", "&7Баланс: %balance%", "balance", plugin.formatNumber(chips(player.getUniqueId())) + " " + chipsName())));
        inv.setItem(49, plugin.createItem(Material.BARRIER, msg("common.back", "&cНазад")));
        inv.setItem(53, plugin.createItem(Material.EMERALD_BLOCK, msg("gui.wheel.start", "&aЗапустить")));
    }

    private List<String> sectors() {
        List<String> list = new ArrayList<>();
        ConfigurationSection section = plugin.getExtra("wheel.yml").get().getConfigurationSection("wheel.sectors");
        if (section != null) for (String key : section.getKeys(false)) for (int i = 0; i < section.getInt(key); i++) list.add(key);
        if (list.isEmpty()) Collections.addAll(list, "orange", "orange", "orange", "yellow", "yellow", "blue", "red");
        return list;
    }

    private List<String> configuredColors() {
        List<String> list = new ArrayList<>();
        ConfigurationSection section = plugin.getExtra("wheel.yml").get().getConfigurationSection("wheel.multipliers");
        if (section != null) list.addAll(section.getKeys(false));
        if (list.isEmpty()) Collections.addAll(list, "orange", "yellow", "blue", "red");
        return list;
    }

    private Material material(String sector) {
        try { return Material.valueOf(plugin.getExtra("wheel.yml").get().getString("colors." + sector + ".material", sector.toUpperCase() + "_WOOL")); }
        catch (Exception e) { return Material.WHITE_WOOL; }
    }
    private String displayName(String sector) {
        String def = plugin.getExtra("wheel.yml").get().getString("colors." + sector + ".name", sector);
        return plugin.getConfigManager().getMessage("gui.wheel.colors." + sector, def);
    }
    private int multiplier(String sector) { return plugin.getExtra("wheel.yml").get().getInt("wheel.multipliers." + sector, 1); }

    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        int slot = event.getSlot();
        if (slot == 49) { plugin.playMenuSound(player); plugin.getGui().openMainMenu(player); return; }
        int start = plugin.getExtra("wheel.yml").get().getInt("gui.color-start-slot", 45);
        List<String> colors = configuredColors();
        if (slot >= start && slot < start + colors.size()) {
            selectedColor.put(uuid, colors.get(slot - start));
            plugin.playClickSound(player);
            open(player);
            return;
        }
        if (slot == 50) {
            int step = Math.max(1, plugin.getExtra("wheel.yml").get().getInt("wheel.bet-step", 100));
            int current = bet.getOrDefault(uuid, minBet()) + (event.isRightClick() ? -step : step);
            bet.put(uuid, Math.max(minBet(), Math.min(maxBet(), current)));
            plugin.playClickSound(player);
            open(player);
            return;
        }
        if (slot == 53) spin(player);
    }

    private void spin(Player player) {
        UUID uuid = player.getUniqueId();
        if (spinning.contains(uuid)) return;
        String chosen = selectedColor.get(uuid);
        if (chosen == null) { player.sendMessage(msg("messages.wheel.select_color", "&cВыберите цвет!")); return; }
        int amount = bet.getOrDefault(uuid, minBet());

        if (!plugin.takeChips(uuid, amount)) {
            player.sendMessage(msg("messages.no_chips", "&cYou do not have enough chips!"));
            return;
        }
        spinning.add(uuid);
        Inventory inv = player.getOpenInventory().getTopInventory();
        List<String> sectors = sectors();

        final int[] state = {0, 0};
        final int max = 48 + random.nextInt(24);
        CasinoScheduler.timerAtEntity(player, 0L,
                plugin.getExtra("wheel.yml").get().getLong("wheel.animation-tick", 2L), task -> {
                if (!player.isOnline()) { plugin.addChips(uuid, amount); spinning.remove(uuid); task.cancel(); return; }
                state[1]++;
                draw(player, inv, state[1], 13);
                plugin.playSpinSound(player, Math.min(2f, 0.5f + state[0] * 0.02f));
                if (++state[0] >= max) {
                    String win = sectors.get((3 + state[1]) % sectors.size());
                    boolean ok = win.equalsIgnoreCase(chosen);
                    if (ok) {
                        int payout = amount * multiplier(win);
                        plugin.addChips(uuid, payout);
                        plugin.logGameResult(uuid, amount, payout, true);
                        plugin.playWinSound(player);
                        player.sendMessage(msg("messages.wheel.win", "&aПобеда! %color% → +%amount% %currency%", "color", displayName(win), "amount", plugin.formatNumber(payout), "currency", chipsName()));
                    } else {
                        plugin.logGameResult(uuid, amount, 0, false);
                        plugin.playLoseSound(player);
                        player.sendMessage(msg("messages.wheel.loss", "&cВыпало %color%. Ставка проиграна.", "color", displayName(win)));
                    }
                    plugin.getDb().incrementStat(uuid, "wheel_spins", 1, false);
                    spinning.remove(uuid);
                    task.cancel();
                }
        });
    }
}
