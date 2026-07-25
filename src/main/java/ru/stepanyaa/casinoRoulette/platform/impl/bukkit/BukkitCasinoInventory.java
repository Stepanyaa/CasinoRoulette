package ru.stepanyaa.casinoRoulette.platform.impl.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.function.Function;

public final class BukkitCasinoInventory implements CasinoInventory {

    public static final class Holder implements InventoryHolder {
        private final String guiId;
        private Inventory inventory;

        Holder(String guiId) {
            this.guiId = guiId;
        }

        public String guiId() {
            return guiId;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final String guiId;
    private final String title;
    private final int size;
    private final Inventory inventory;
    private final Function<CasinoItem, ItemStack> itemFactory;

    public BukkitCasinoInventory(String guiId, String title, int size,
                                 Function<CasinoItem, ItemStack> itemFactory) {
        this.guiId = guiId;
        this.title = title;

        this.size = clampSize(size);
        this.itemFactory = itemFactory;

        Holder holder = new Holder(guiId);
        this.inventory = Bukkit.createInventory(holder, this.size,
                BukkitCasinoSender.colorise(title));
        holder.attach(this.inventory);
    }

    private static int clampSize(int requested) {
        int rounded = ((Math.max(1, requested) + 8) / 9) * 9;
        return Math.min(54, rounded);
    }

    public static String guiIdOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Holder ? ((Holder) holder).guiId() : null;
    }

    @Override
    public String guiId() {
        return guiId;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void setItem(int slot, CasinoItem item) {
        if (slot < 0 || slot >= size) {
            return;
        }
        inventory.setItem(slot, item == null ? null : itemFactory.apply(item));
    }

    @Override
    public void clear(int slot) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, null);
        }
    }

    @Override
    public void fillEmpty(CasinoItem item) {
        ItemStack stack = itemFactory.apply(item);
        for (int slot = 0; slot < size; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().name().equals("AIR")) {
                inventory.setItem(slot, stack.clone());
            }
        }
    }

    @Override
    public void refresh() {
        for (HumanEntity viewer : new java.util.ArrayList<>(inventory.getViewers())) {
            if (!(viewer instanceof Player)) {
                continue;
            }
            final Player player = (Player) viewer;
            CasinoScheduler.atEntity(player, () -> {
                try {
                    player.updateInventory();
                } catch (Throwable ignored) {

                }
            });
        }
    }

    @Override
    public Object handle() {
        return inventory;
    }
}
