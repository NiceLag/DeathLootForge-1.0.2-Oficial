package com.example.examplemod.api;

import com.example.examplemod.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API publica de cargas gratis de DeathlootForge.
 *
 * Todo se guarda en el persistentData del jugador, asi que sobrevive a la muerte,
 * al respawn y al cambio de dimension. Las cargas son por jugador y de un solo uso:
 * cada carga sirve para UNA muerte cualquiera y se consume al usarla.
 *
 * Uso desde otro mod:
 *   DeathLootCharges.addFreeTeleports(player, 2);
 *   DeathLootCharges.addFreeExtensions(player, 5, 1);
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeathLootCharges {

    /** Tramos de tiempo que el mod acepta ampliar. */
    public static final int[] VALID_MINUTES = {5, 10, 30, 60, 300, 600};

    private static final String TP_KEY = "deathloot_free_tps";
    private static final String EXT_KEY = "deathloot_free_ext";

    private DeathLootCharges() {
    }

    public static boolean isValidMinutes(int minutes) {
        for (int value : VALID_MINUTES) {
            if (value == minutes) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ teleportes

    /** Teleportes gratis que le quedan al jugador. */
    public static int freeTeleports(Player player) {
        return Math.max(0, player.getPersistentData().getInt(TP_KEY));
    }

    public static boolean hasFreeTeleport(Player player) {
        return freeTeleports(player) > 0;
    }

    public static void setFreeTeleports(Player player, int amount) {
        player.getPersistentData().putInt(TP_KEY, Math.max(0, amount));
    }

    /** Suma cargas de teleporte gratis (acepta negativos para restar). */
    public static void addFreeTeleports(Player player, int amount) {
        setFreeTeleports(player, freeTeleports(player) + amount);
    }

    public static void clearFreeTeleports(Player player) {
        player.getPersistentData().remove(TP_KEY);
    }

    /**
     * Gasta una carga de teleporte gratis.
     *
     * @return true si habia una carga y se consumio
     */
    public static boolean consumeFreeTeleport(Player player) {
        int current = freeTeleports(player);
        if (current <= 0) {
            return false;
        }
        setFreeTeleports(player, current - 1);
        return true;
    }

    // ------------------------------------------------------- tiempo ampliado

    private static CompoundTag extensionTag(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(EXT_KEY, 10)) {
            data.put(EXT_KEY, new CompoundTag());
        }
        return data.getCompound(EXT_KEY);
    }

    /** Cargas gratis que le quedan para ese tramo de minutos. */
    public static int freeExtensions(Player player, int minutes) {
        return Math.max(0, extensionTag(player).getInt(Integer.toString(minutes)));
    }

    public static boolean hasFreeExtension(Player player, int minutes) {
        return freeExtensions(player, minutes) > 0;
    }

    public static void setFreeExtensions(Player player, int minutes, int amount) {
        CompoundTag tag = extensionTag(player);
        int clamped = Math.max(0, amount);
        if (clamped <= 0) {
            tag.remove(Integer.toString(minutes));
            return;
        }
        tag.putInt(Integer.toString(minutes), clamped);
    }

    /** Suma cargas gratis para un tramo concreto ("5 minutos", "30 minutos", ...). */
    public static void addFreeExtensions(Player player, int minutes, int amount) {
        if (minutes <= 0) {
            return;
        }
        setFreeExtensions(player, minutes, freeExtensions(player, minutes) + amount);
    }

    public static void clearFreeExtensions(Player player) {
        player.getPersistentData().remove(EXT_KEY);
    }

    /**
     * Gasta una carga gratis del tramo indicado.
     *
     * @return true si habia una carga y se consumio
     */
    public static boolean consumeFreeExtension(Player player, int minutes) {
        int current = freeExtensions(player, minutes);
        if (current <= 0) {
            return false;
        }
        setFreeExtensions(player, minutes, current - 1);
        return true;
    }

    /** Copia de solo lectura: minutos -> cargas, para sincronizar o mostrar. */
    public static Map<Integer, Integer> allFreeExtensions(Player player) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        CompoundTag tag = extensionTag(player);
        for (String key : tag.getAllKeys()) {
            try {
                int minutes = Integer.parseInt(key);
                int charges = tag.getInt(key);
                if (charges > 0) {
                    out.put(minutes, charges);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static CompoundTag freeExtensionsTag(Player player) {
        CompoundTag tag = extensionTag(player);
        CompoundTag out = new CompoundTag();
        for (String key : tag.getAllKeys()) {
            int charges = tag.getInt(key);
            if (charges > 0) {
                out.putInt(key, charges);
            }
        }
        return out;
    }

    public static void clearAll(Player player) {
        clearFreeTeleports(player);
        clearFreeExtensions(player);
    }

    // ------------------------------------------------------------ persistencia

    /** El persistentData ya viaja en el clon; se copia igual por si acaso. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag from = event.getOriginal().getPersistentData();
        CompoundTag to = event.getEntity().getPersistentData();

        if (from.contains(TP_KEY)) {
            to.putInt(TP_KEY, from.getInt(TP_KEY));
        }
        if (from.contains(EXT_KEY, 10)) {
            to.put(EXT_KEY, from.getCompound(EXT_KEY).copy());
        }
    }
}