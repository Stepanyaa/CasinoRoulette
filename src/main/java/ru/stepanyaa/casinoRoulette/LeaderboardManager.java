package ru.stepanyaa.casinoRoulette;

import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.*;

public class LeaderboardManager {
    private final CasinoRoulette plugin;
    private final Map<String, List<PlayerStats>> cache = new HashMap<>();
    private final List<String> cats = Arrays.asList("money", "wins", "bets", "games", "biggest_win", "biggest_loss", "daily", "wheel", "chips_bought");
    private final Map<Integer, String> categorySlots = new HashMap<>();
    private final Map<String, Material> icons = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();

    public LeaderboardManager(CasinoRoulette p) {
        plugin = p;
        icons.put("money", Material.EMERALD);
        icons.put("wins", Material.DIAMOND);
        icons.put("bets", Material.GOLD_INGOT);
        icons.put("games", Material.CLOCK);
        icons.put("biggest_win", Material.NETHER_STAR);
        icons.put("biggest_loss", Material.REDSTONE);
        icons.put("daily", Material.CHEST);
        icons.put("wheel", Material.ORANGE_WOOL);
        icons.put("chips_bought", Material.GOLD_BLOCK);
        start();
    }

    public void start() {
        long t = 20L * plugin.getExtra("leaderboard.yml").get().getLong("leaderboard.update-time", 300);

        CasinoScheduler.asyncTimer(20L, t, task -> refresh());
    }

    public void refresh() {
        List<PlayerStats> all = plugin.getDb().loadAllStats();
        try {
            java.util.Set<UUID> live = new java.util.HashSet<>();
            live.addAll(plugin.getPlayerChips().keySet());
            live.addAll(plugin.getWins().keySet());
            live.addAll(plugin.getLosses().keySet());
            live.addAll(plugin.getTotalWon().keySet());
            live.addAll(plugin.getTotalLost().keySet());
            live.addAll(plugin.getTotalRounds().keySet());
            for (PlayerStats s : all) {
                if (!live.contains(s.uuid)) continue;
                s.chips = plugin.getPlayerChips().getOrDefault(s.uuid, (int) s.chips);
                s.wins = plugin.getWins().getOrDefault(s.uuid, (int) s.wins);
                s.losses = plugin.getLosses().getOrDefault(s.uuid, (int) s.losses);
                s.totalWon = plugin.getTotalWon().getOrDefault(s.uuid, (int) s.totalWon);
                s.totalLost = plugin.getTotalLost().getOrDefault(s.uuid, (int) s.totalLost);
                s.rounds = plugin.getTotalRounds().getOrDefault(s.uuid, (int) s.rounds);
            }
        } catch (Exception ignored) {}
        sort("money", all, Comparator.comparingLong(a -> -a.chips));
        sort("wins", all, Comparator.comparingLong(a -> -a.wins));
        sort("bets", all, Comparator.comparingLong(a -> -a.bets));
        sort("games", all, Comparator.comparingLong(a -> -a.rounds));
        sort("biggest_win", all, Comparator.comparingLong(a -> -a.biggestWin));
        sort("biggest_loss", all, Comparator.comparingLong(a -> -a.biggestLoss));
        sort("daily", all, Comparator.comparingLong(a -> -a.dailyUses));
        sort("wheel", all, Comparator.comparingLong(a -> -a.wheelSpins));
        sort("chips_bought", all, Comparator.comparingLong(a -> -a.chipsBought));
    }

    private void sort(String k, List<PlayerStats> src, Comparator<PlayerStats> c) {
        List<PlayerStats> l = new ArrayList<>(src);
        l.sort(c);
        cache.put(k, l);
    }

    public String placeholder(String cat, int pos) {
        refresh();
        List<PlayerStats> l = cache.get(cat);
        if (l == null || pos < 1 || pos > l.size()) return "-";
        PlayerStats s = l.get(pos - 1);
        return playerName(s) + " - " + value(cat, s);
    }

    public List<PlayerStats> top(String cat, int limit) {
        refresh();
        List<PlayerStats> list = cache.getOrDefault(cat, new ArrayList<>());
        return new ArrayList<>(list.subList(0, Math.min(limit, list.size())));
    }

    private long value(String c, PlayerStats s) {
        switch (c) {
            case "money": return s.chips;
            case "wins": return s.wins;
            case "bets": return s.bets;
            case "games": return s.rounds;
            case "biggest_win": return s.biggestWin;
            case "biggest_loss": return s.biggestLoss;
            case "daily": return s.dailyUses;
            case "wheel": return s.wheelSpins;
            case "chips_bought": return s.chipsBought;
        }
        return 0;
    }

    public void openCategories(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 54, cm.getMessage("gui.titles.leaderboards", "&8✦ Leaderboards ✦"));
        fill(inv);
        categorySlots.clear();
        int[] slots = {10,11,12,13,14,15,16,20,22,24};
        for (int i = 0; i < cats.size() && i < slots.length; i++) {
            String cat = cats.get(i);
            categorySlots.put(slots[i], cat);
            List<PlayerStats> top = top(cat, 1);
            String leader = top.isEmpty() ? cm.getMessage("leaderboard.empty", "&7No data yet") : "&f" + playerName(top.get(0)) + " &8— &a" + plugin.formatNumber((int)value(cat, top.get(0)));
            inv.setItem(slots[i], plugin.createItem(icons.getOrDefault(cat, Material.PAPER),
                    cm.getMessage("leaderboard.category_name", "&e&l%category%", "category", name(cat)),
                    cm.getMessage("leaderboard.category_leader", "&7Leader: %leader%", "leader", leader),
                    cm.getMessage("leaderboard.category_click", "&aClick to open"),
                    color("&8category:" + cat)));
        }
        inv.setItem(49, plugin.createItem(Material.BARRIER, cm.getMessage("common.back", "&cBack")));
        p.openInventory(inv);
        viewers.add(p.getUniqueId());
    }

    public void open(Player p, String cat) {
        refresh();
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, 54, color(plugin.getExtra("leaderboard.yml").get().getString("gui.title", "&8✦ %category% ✦").replace("%category%", name(cat))));
        fill(inv);
        List<PlayerStats> l = cache.getOrDefault(cat, new ArrayList<>());
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        for (int i = 0; i < Math.min(slots.length, l.size()); i++) {
            PlayerStats s = l.get(i);
            ItemStack it = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) it.getItemMeta();
            OfflinePlayer op = Bukkit.getOfflinePlayer(s.uuid);
            m.setOwningPlayer(op);
            m.setDisplayName(cm.getMessage("leaderboard.entry_name", "&6#%place% &f%player%", "place", i + 1, "player", playerName(s)));
            m.setLore(Arrays.asList(
                    cm.getMessage("leaderboard.entry_value", "&eValue: &a%value%", "value", plugin.formatNumber((int)value(cat, s))),
                    cm.getMessage("leaderboard.entry_wins", "&7Wins: &f%wins%", "wins", plugin.formatNumber((int)s.wins)),
                    cm.getMessage("leaderboard.entry_games", "&7Games: &f%games%", "games", plugin.formatNumber((int)s.rounds))
            ));
            it.setItemMeta(m);
            inv.setItem(slots[i], it);
        }
        if (l.isEmpty()) inv.setItem(22, plugin.createItem(Material.BOOK, cm.getMessage("leaderboard.empty", "&7No data yet")));
        inv.setItem(45, plugin.createItem(Material.ARROW, cm.getMessage("common.back", "&cBack")));
        inv.setItem(49, plugin.createItem(icons.getOrDefault(cat, Material.NETHER_STAR), cm.getMessage("leaderboard.current_category", "&e%category%", "category", name(cat))));
        p.openInventory(inv);
        viewers.add(p.getUniqueId());
    }

    public void handleClick(Player p, InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        String title = ChatColor.stripColor(e.getView().getTitle());
        String catsTitle = ChatColor.stripColor(plugin.getConfigManager().getMessage("gui.titles.leaderboards", "&8✦ Leaderboards ✦"));
        if (title.equals(catsTitle)) {
            if (slot == 49) { plugin.playMenuSound(p); removeViewer(p.getUniqueId()); plugin.getGui().openMainMenu(p); return; }
            String cat = categorySlots.get(slot);
            if (cat != null) { plugin.playClickSound(p); open(p, cat); }
        } else if (slot == 45) {
            plugin.playMenuSound(p);
            openCategories(p);
        }
    }

    public boolean isViewing(UUID uuid) { return viewers.contains(uuid); }
    public void removeViewer(UUID uuid) { viewers.remove(uuid); }

    private void fill(Inventory inv) {
        ItemStack filler = plugin.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }
    private String playerName(PlayerStats s) { return s.name == null || s.name.isEmpty() ? s.uuid.toString().substring(0, 8) : s.name; }
    private String name(String c) { String def = plugin.getExtra("leaderboard.yml").get().getString("categories." + c, c); return plugin.getConfigManager().getMessage("leaderboard.cat." + c, def); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
