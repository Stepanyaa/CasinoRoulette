package ru.stepanyaa.casinoRoulette.events;

import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;

public final class CasinoEvents {

    private CasinoEvents() {
    }

    public abstract static class PlayerEvent {
        private final CasinoPlayer player;

        protected PlayerEvent(CasinoPlayer player) {
            this.player = player;
        }

        public CasinoPlayer player() {
            return player;
        }
    }

    public interface Cancellable {
        boolean isCancelled();

        void setCancelled(boolean cancelled);
    }

    public static final class PlayerJoin extends PlayerEvent {
        public PlayerJoin(CasinoPlayer player) {
            super(player);
        }
    }

    public static final class PlayerQuit extends PlayerEvent {
        public PlayerQuit(CasinoPlayer player) {
            super(player);
        }
    }

    public static final class InventoryClick extends PlayerEvent implements Cancellable {
        private final String guiId;
        private final int slot;
        private final ClickType clickType;
        private final boolean topInventory;
        private boolean cancelled;

        public InventoryClick(CasinoPlayer player, String guiId, int slot,
                              ClickType clickType, boolean topInventory) {
            super(player);
            this.guiId = guiId;
            this.slot = slot;
            this.clickType = clickType;
            this.topInventory = topInventory;
        }

        public String guiId() {
            return guiId;
        }

        public int slot() {
            return slot;
        }

        public ClickType clickType() {
            return clickType;
        }

        public boolean isTopInventory() {
            return topInventory;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    public static final class InventoryClose extends PlayerEvent {
        private final String guiId;

        public InventoryClose(CasinoPlayer player, String guiId) {
            super(player);
            this.guiId = guiId;
        }

        public String guiId() {
            return guiId;
        }
    }

    public enum ClickType {
        LEFT,
        RIGHT,
        SHIFT_LEFT,
        SHIFT_RIGHT,
        MIDDLE,
        NUMBER_KEY,
        DOUBLE_CLICK,
        DROP,
        OTHER;

        public boolean isLeft() {
            return this == LEFT || this == SHIFT_LEFT;
        }

        public boolean isRight() {
            return this == RIGHT || this == SHIFT_RIGHT;
        }

        public boolean isShift() {
            return this == SHIFT_LEFT || this == SHIFT_RIGHT;
        }
    }

    public static final class PlayerMove extends PlayerEvent implements Cancellable {
        private final double fromX;
        private final double fromY;
        private final double fromZ;
        private final double toX;
        private final double toY;
        private final double toZ;
        private boolean cancelled;

        public PlayerMove(CasinoPlayer player, double fromX, double fromY, double fromZ,
                          double toX, double toY, double toZ) {
            super(player);
            this.fromX = fromX;
            this.fromY = fromY;
            this.fromZ = fromZ;
            this.toX = toX;
            this.toY = toY;
            this.toZ = toZ;
        }

        public boolean movedBlock() {
            return (int) fromX != (int) toX || (int) fromY != (int) toY || (int) fromZ != (int) toZ;
        }

        public double toX() {
            return toX;
        }

        public double toY() {
            return toY;
        }

        public double toZ() {
            return toZ;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    public static final class PlayerInteract extends PlayerEvent implements Cancellable {
        private final Object targetHandle;
        private final boolean rightClick;
        private boolean cancelled;

        public PlayerInteract(CasinoPlayer player, Object targetHandle, boolean rightClick) {
            super(player);
            this.targetHandle = targetHandle;
            this.rightClick = rightClick;
        }

        public Object targetHandle() {
            return targetHandle;
        }

        public boolean isRightClick() {
            return rightClick;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    public static final class EntityDamage implements Cancellable {
        private final Object entityHandle;
        private final double amount;
        private boolean cancelled;

        public EntityDamage(Object entityHandle, double amount) {
            this.entityHandle = entityHandle;
            this.amount = amount;
        }

        public Object entityHandle() {
            return entityHandle;
        }

        public double amount() {
            return amount;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }
}
