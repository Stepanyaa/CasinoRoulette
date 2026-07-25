package ru.stepanyaa.casinoRoulette.platform.impl.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.events.EventBridge;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;

import java.util.function.Function;

public final class BukkitEventBridge extends EventBridge implements Listener {

    private final Plugin plugin;
    private final Function<CasinoItem, ItemStack> itemFactory;

    public BukkitEventBridge(Plugin plugin, Function<CasinoItem, ItemStack> itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unregister() {
        HandlerList.unregisterAll(this);
        super.unregister();
    }

    @Override
    protected void logHandlerFailure(Object event, Throwable throwable) {
        plugin.getLogger().warning(
                "Handler for " + event.getClass().getSimpleName() + " failed: " + throwable);
    }

    private BukkitCasinoPlayer wrap(Player player) {
        return new BukkitCasinoPlayer(player, itemFactory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        post(new CasinoEvents.PlayerJoin(wrap(event.getPlayer())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        post(new CasinoEvents.PlayerQuit(wrap(event.getPlayer())));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        String guiId = BukkitCasinoInventory.guiIdOf(event.getView().getTopInventory());
        if (guiId == null) {
            return;
        }

        boolean top = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        event.setCancelled(true);

        CasinoEvents.InventoryClick shared = new CasinoEvents.InventoryClick(
                wrap((Player) event.getWhoClicked()),
                guiId,
                event.getSlot(),
                translate(event.getClick()),
                top);
        shared.setCancelled(true);

        post(shared);
        event.setCancelled(shared.isCancelled());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (BukkitCasinoInventory.guiIdOf(event.getView().getTopInventory()) == null) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        String guiId = BukkitCasinoInventory.guiIdOf(event.getInventory());
        if (guiId == null) {
            return;
        }
        post(new CasinoEvents.InventoryClose(wrap((Player) event.getPlayer()), guiId));
    }

    private static CasinoEvents.ClickType translate(org.bukkit.event.inventory.ClickType click) {
        if (click == null) {
            return CasinoEvents.ClickType.OTHER;
        }
        switch (click) {
            case LEFT:
                return CasinoEvents.ClickType.LEFT;
            case RIGHT:
                return CasinoEvents.ClickType.RIGHT;
            case SHIFT_LEFT:
                return CasinoEvents.ClickType.SHIFT_LEFT;
            case SHIFT_RIGHT:
                return CasinoEvents.ClickType.SHIFT_RIGHT;
            case MIDDLE:
                return CasinoEvents.ClickType.MIDDLE;
            case NUMBER_KEY:
                return CasinoEvents.ClickType.NUMBER_KEY;
            case DOUBLE_CLICK:
                return CasinoEvents.ClickType.DOUBLE_CLICK;
            case DROP:
            case CONTROL_DROP:
                return CasinoEvents.ClickType.DROP;
            default:
                return CasinoEvents.ClickType.OTHER;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!hasSubscribers(CasinoEvents.PlayerMove.class) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        CasinoEvents.PlayerMove shared = new CasinoEvents.PlayerMove(
                wrap(event.getPlayer()),
                event.getFrom().getX(), event.getFrom().getY(), event.getFrom().getZ(),
                event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());
        post(shared);
        if (shared.isCancelled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!hasSubscribers(CasinoEvents.PlayerInteract.class)) {
            return;
        }
        boolean right = event.getAction().name().startsWith("RIGHT_");
        CasinoEvents.PlayerInteract shared = new CasinoEvents.PlayerInteract(
                wrap(event.getPlayer()), event.getClickedBlock(), right);
        post(shared);
        if (shared.isCancelled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!hasSubscribers(CasinoEvents.PlayerInteract.class)) {
            return;
        }
        CasinoEvents.PlayerInteract shared = new CasinoEvents.PlayerInteract(
                wrap(event.getPlayer()), event.getRightClicked(), true);
        post(shared);
        if (shared.isCancelled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!hasSubscribers(CasinoEvents.EntityDamage.class)) {
            return;
        }
        CasinoEvents.EntityDamage shared =
                new CasinoEvents.EntityDamage(event.getEntity(), event.getDamage());
        post(shared);
        if (shared.isCancelled()) {
            event.setCancelled(true);
        }
    }
}
