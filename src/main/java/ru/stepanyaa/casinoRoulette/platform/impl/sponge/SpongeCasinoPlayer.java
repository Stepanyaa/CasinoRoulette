package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.UUID;
import java.util.function.Function;

final class SpongeCasinoPlayer extends SpongeCasinoSender implements CasinoPlayer {

    private final UUID uuid;
    private final Function<CasinoItem, Object> itemFactory;

    SpongeCasinoPlayer(Object player, UUID uuid, String name,
                       Function<CasinoItem, Object> itemFactory) {
        super(player, name, false);
        this.uuid = uuid;
        this.itemFactory = itemFactory;
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public boolean isOnline() {
        return SpongeReflection.onlinePlayer(uuid).isPresent();
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public void openInventory(CasinoInventory inventory) {
        Object menu = inventory.handle();
        if (menu == null) {
            return;
        }
        runOwning(() -> {
            try {

                Object opened = SpongeReflection.method(menu.getClass(), "open",
                                SpongeReflection.type("org.spongepowered.api.entity.living.player.server.ServerPlayer"))
                        .invoke(menu, subject);
                java.util.Optional<Object> container = SpongeReflection.unwrap(opened);
                if (container.isPresent()) {
                    SpongeMenuRegistry.MenuInfo info = SpongeMenuRegistry.infoOf(menu);
                    SpongeMenuRegistry.rememberContainer(container.get(), inventory.guiId(),
                            inventory.size(), info != null && info.callbacks);
                }
            } catch (Throwable failure) {

                closeInventory();
            }
        });
    }

    @Override
    public void closeInventory() {
        runOwning(() -> {
            try {
                SpongeReflection.call(subject, "closeInventory");
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public void giveItem(CasinoItem item) {
        final Object stack = itemFactory.apply(item);
        if (stack == null) {
            return;
        }
        runOwning(() -> {
            try {
                Object inventory = SpongeReflection.call(subject, "inventory");
                Object result = SpongeReflection.method(inventory.getClass(), "offer",
                                SpongeReflection.type("org.spongepowered.api.item.inventory.ItemStack"))
                        .invoke(inventory, stack);

                Object rejected = SpongeReflection.call(result, "rejectedItems");
                if (rejected instanceof java.util.Collection
                        && !((java.util.Collection<?>) rejected).isEmpty()) {
                    dropAtFeet(rejected);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public int countItem(String materialId) {
        Object expected = SpongeReflection.registryValue("ITEM_TYPE", materialId).orElse(null);
        if (expected == null) return 0;
        try {
            Object inventory = SpongeReflection.call(subject, "inventory");
            Object slots = SpongeReflection.call(inventory, "slots");
            if (!(slots instanceof Iterable)) return 0;
            int total = 0;
            for (Object slot : (Iterable<?>) slots) {
                Object stack = SpongeReflection.unwrap(SpongeReflection.call(slot, "peek")).orElse(null);
                if (stack == null) continue;
                Object type = SpongeReflection.call(stack, "type");
                if (expected.equals(type)) {
                    Object quantity = SpongeReflection.call(stack, "quantity");
                    if (quantity instanceof Number) total += ((Number) quantity).intValue();
                }
            }
            return total;
        } catch (Throwable failure) {
            return 0;
        }
    }

    @Override
    public boolean takeItem(String materialId, int amount) {
        if (amount <= 0) return true;
        Object expected = SpongeReflection.registryValue("ITEM_TYPE", materialId).orElse(null);
        if (expected == null || countItem(materialId) < amount) return false;
        try {
            Object inventory = SpongeReflection.call(subject, "inventory");
            Object slots = SpongeReflection.call(inventory, "slots");
            int remaining = amount;
            for (Object slot : (Iterable<?>) slots) {
                if (remaining <= 0) break;
                Object stack = SpongeReflection.unwrap(SpongeReflection.call(slot, "peek")).orElse(null);
                if (stack == null || !expected.equals(SpongeReflection.call(stack, "type"))) continue;
                Object quantity = SpongeReflection.call(stack, "quantity");
                int held = quantity instanceof Number ? ((Number) quantity).intValue() : 0;
                if (held <= 0) continue;
                int remove = Math.min(held, remaining);
                for (java.lang.reflect.Method method : slot.getClass().getMethods()) {
                    if (method.getName().equals("poll") && method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == int.class) {
                        method.invoke(slot, remove);
                        remaining -= remove;
                        break;
                    }
                }
            }
            return remaining == 0;
        } catch (Throwable failure) {
            return false;
        }
    }

    private void dropAtFeet(Object rejectedSnapshots) {
        try {
            Object world = SpongeReflection.call(subject, "world");
            Object position = SpongeReflection.call(subject, "position");
            for (Object snapshot : (java.util.Collection<?>) rejectedSnapshots) {
                Object stack = SpongeReflection.call(snapshot, "createStack");
                Class<?> entityTypes = SpongeReflection
                        .type("org.spongepowered.api.entity.EntityTypes");
                Object itemType = entityTypes.getField("ITEM").get(null);
                Object entityType = SpongeReflection.call(itemType, "get");

                Object entity = SpongeReflection.method(world.getClass(), "createEntity",
                                SpongeReflection.type("org.spongepowered.api.entity.EntityType"),
                                SpongeReflection.type("org.spongepowered.math.vector.Vector3d"))
                        .invoke(world, entityType, position);

                Class<?> keys = SpongeReflection.type("org.spongepowered.api.data.Keys");
                Object itemStackSnapshotKey = keys.getField("ITEM_STACK_SNAPSHOT").get(null);
                Object asSnapshot = SpongeReflection.call(stack, "createSnapshot");
                SpongeReflection.method(entity.getClass(), "offer",
                                SpongeReflection.type("org.spongepowered.api.data.Key"),
                                Object.class)
                        .invoke(entity, itemStackSnapshotKey, asSnapshot);

                SpongeReflection.method(world.getClass(), "spawnEntity",
                                SpongeReflection.type("org.spongepowered.api.entity.Entity"))
                        .invoke(world, entity);
            }
        } catch (Throwable ignored) {

        }
    }

    @Override
    public void playSound(String soundId, float volume, float pitch) {
        runOwning(() -> {
            try {
                java.util.Optional<Object> soundType =
                        SpongeReflection.registryValue("SOUND_TYPE", spongeSoundId(soundId));
                if (!soundType.isPresent()) {

                    return;
                }
                Class<?> soundClass = SpongeReflection.type("net.kyori.adventure.sound.Sound");
                Object source = SpongeReflection
                        .type("net.kyori.adventure.sound.Sound$Source")
                        .getField("MASTER").get(null);

                Object key = SpongeReflection.call(soundType.get(), "key");
                Object sound = SpongeReflection.method(soundClass, "sound",
                                SpongeReflection.type("net.kyori.adventure.key.Key"),
                                SpongeReflection.type("net.kyori.adventure.sound.Sound$Source"),
                                float.class, float.class)
                        .invoke(null, key, source, volume, clampPitch(pitch));

                SpongeReflection.method(
                                SpongeReflection.type("net.kyori.adventure.audience.Audience"),
                                "playSound", soundClass)
                        .invoke(subject, sound);
            } catch (Throwable ignored) {
            }
        });
    }

    private static float clampPitch(float pitch) {
        return Math.max(0.5F, Math.min(2.0F, pitch));
    }

    private static String spongeSoundId(String id) {
        if (id == null) return "";
        String value = id.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        switch (value) {
            case "ui_button_click": value = "ui.button.click"; break;
            case "block_chest_open": value = "block.chest.open"; break;
            case "block_note_block_pling": value = "block.note_block.pling"; break;
            case "entity_player_levelup": value = "entity.player.levelup"; break;
            case "entity_villager_no": value = "entity.villager.no"; break;
            case "entity_experience_orb_pickup": value = "entity.experience_orb.pickup"; break;
            case "entity_generic_explode": value = "entity.generic.explode"; break;
            default:
                break;
        }
        return "minecraft:" + value;
    }

    @Override
    public void spawnParticle(String particleId, int count,
                              double offsetX, double offsetY, double offsetZ) {
        runOwning(() -> {
            try {
                java.util.Optional<Object> particleType =
                        SpongeReflection.registryValue("PARTICLE_TYPE", particleId);
                if (!particleType.isPresent()) {
                    return;
                }
                Class<?> effectClass =
                        SpongeReflection.type("org.spongepowered.api.effect.particle.ParticleEffect");
                Object builder = SpongeReflection.method(effectClass, "builder").invoke(null);

                builder = SpongeReflection.method(builder.getClass(), "type",
                                SpongeReflection.type("org.spongepowered.api.effect.particle.ParticleType"))
                        .invoke(builder, particleType.get());
                builder = SpongeReflection.method(builder.getClass(), "quantity", int.class)
                        .invoke(builder, Math.max(1, count));
                Object effect = SpongeReflection.call(builder, "build");

                Object world = SpongeReflection.call(subject, "world");
                Object position = SpongeReflection.call(subject, "position");
                SpongeReflection.method(world.getClass(), "spawnParticles",
                                effectClass,
                                SpongeReflection.type("org.spongepowered.math.vector.Vector3d"))
                        .invoke(world, effect, position);
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public void sendActionBar(String message) {
        try {
            Object text = SpongeReflection.component(message);
            SpongeReflection.method(
                            SpongeReflection.type("net.kyori.adventure.audience.Audience"),
                            "sendActionBar",
                            SpongeReflection.type("net.kyori.adventure.text.ComponentLike"))
                    .invoke(subject, text);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Object mainText = SpongeReflection.component(title == null ? "" : title);
            Object subText = SpongeReflection.component(subtitle == null ? "" : subtitle);

            Class<?> titleClass = SpongeReflection.type("net.kyori.adventure.title.Title");
            Class<?> timesClass = SpongeReflection.type("net.kyori.adventure.title.Title$Times");
            Class<?> componentClass = SpongeReflection.type("net.kyori.adventure.text.Component");

            Object times = SpongeReflection.method(timesClass, "times",
                            java.time.Duration.class, java.time.Duration.class,
                            java.time.Duration.class)
                    .invoke(null, ticks(fadeIn), ticks(stay), ticks(fadeOut));

            Object built = SpongeReflection.method(titleClass, "title",
                            componentClass, componentClass, timesClass)
                    .invoke(null, mainText, subText, times);

            SpongeReflection.method(
                            SpongeReflection.type("net.kyori.adventure.audience.Audience"),
                            "showTitle", titleClass)
                    .invoke(subject, built);
        } catch (Throwable ignored) {
        }
    }

    private static java.time.Duration ticks(int ticks) {
        return java.time.Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }

    @Override
    public void runOwning(Runnable task) {
        if (!CasinoScheduler.isReady()) {
            task.run();
            return;
        }
        if (CasinoScheduler.backend().isOnOwningThread(subject)) {
            task.run();
            return;
        }
        CasinoScheduler.run(task);
    }
}
