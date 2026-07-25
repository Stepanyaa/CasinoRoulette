package ru.stepanyaa.casinoRoulette.platform.impl.bukkit;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class BukkitCasinoPlayer extends BukkitCasinoSender implements CasinoPlayer {

    private final Player player;
    private final Function<CasinoItem, ItemStack> itemFactory;

    public BukkitCasinoPlayer(Player player, Function<CasinoItem, ItemStack> itemFactory) {
        super(player);
        this.player = player;
        this.itemFactory = itemFactory;
    }

    public Player bukkit() {
        return player;
    }

    @Override
    public UUID uuid() {
        return player.getUniqueId();
    }

    @Override
    public boolean isOnline() {
        return player.isOnline();
    }

    @Override
    public void openInventory(CasinoInventory inventory) {
        Object handle = inventory.handle();
        if (!(handle instanceof Inventory)) {
            return;
        }

        runOwning(() -> player.openInventory((Inventory) handle));
    }

    @Override
    public void closeInventory() {
        runOwning(player::closeInventory);
    }

    @Override
    public void giveItem(CasinoItem item) {
        final ItemStack stack = itemFactory.apply(item);
        runOwning(() -> {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);

            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        });
    }

    @Override
    public void playSound(String soundId, float volume, float pitch) {
        final Sound sound = resolveSound(soundId);
        if (sound == null) {
            return;
        }
        runOwning(() -> {
            try {
                player.playSound(player.getLocation(), sound, volume, clampPitch(pitch));
            } catch (Throwable ignored) {
            }
        });
    }

    private static float clampPitch(float pitch) {
        return Math.max(0.5F, Math.min(2.0F, pitch));
    }

    private static Sound resolveSound(String soundId) {
        if (soundId == null || soundId.isEmpty()) {
            return null;
        }
        try {
            return Sound.valueOf(soundId.toUpperCase(Locale.ROOT).replace('.', '_'));
        } catch (IllegalArgumentException notFound) {
            return null;
        }
    }

    @Override
    public void spawnParticle(String particleId, int count,
                              double offsetX, double offsetY, double offsetZ) {
        final Particle particle = resolveParticle(particleId);
        if (particle == null) {
            return;
        }
        final Location location = player.getLocation();

        CasinoScheduler.atLocation(location, () -> {
            try {
                player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ);
            } catch (Throwable ignored) {
            }
        });
    }

    private static Particle resolveParticle(String particleId) {
        if (particleId == null || particleId.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(particleId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notFound) {
            return null;
        }
    }

    @Override
    public void sendActionBar(String message) {
        final String text = colorise(message);
        runOwning(() -> {
            try {
                player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text));
            } catch (Throwable ignored) {

                player.sendMessage(text);
            }
        });
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        final String mainText = colorise(title == null ? "" : title);
        final String subText = colorise(subtitle == null ? "" : subtitle);
        runOwning(() -> {
            try {
                player.sendTitle(mainText, subText, fadeIn, stay, fadeOut);
            } catch (NoSuchMethodError legacy) {

                player.sendTitle(mainText, subText);
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public void runOwning(Runnable task) {
        if (CasinoScheduler.isReady() && !CasinoScheduler.backend().isOnOwningThread(player)) {
            CasinoScheduler.atEntity(player, task);
            return;
        }
        if (!CasinoScheduler.isReady()) {
            task.run();
            return;
        }
        task.run();
    }
}
