package com.example.examplemod.server;

import com.example.examplemod.Config;
import com.example.examplemod.storage.DeathHistory;
import com.example.examplemod.storage.DeathRecord;
import com.example.examplemod.storage.DeathStorageBlockEntity;
import com.example.examplemod.storage.DeathStorageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Decide si una nueva muerte cae en un almacen propio cercano. */
public final class StorageRouting {

    /** Se usa si la configuracion quedo en 0 por cualquier motivo. */
    private static final double FALLBACK_RADIUS = 16.0D;

    private StorageRouting() {
    }

    public static double radius() {
        double value = Config.mergeRadius;
        return value > 0.0D ? value : FALLBACK_RADIUS;
    }

    public static DeathStorageBlockEntity nearestOwned(ServerPlayer player, ServerLevel level) {
        double radius = radius();
        double best = radius * radius;
        DeathStorageBlockEntity found = null;

        for (DeathStorageManager.Entry entry : DeathStorageManager.get(player.server).activeOf(player.getUUID())) {
            if (!entry.dimension.location().equals(level.dimension().location())) {
                continue;
            }
            if (!level.isLoaded(entry.pos)) {
                continue;
            }

            double distance = player.distanceToSqr(
                    entry.pos.getX() + 0.5D, entry.pos.getY() + 0.5D, entry.pos.getZ() + 0.5D);

            if (distance > best) {
                continue;
            }
            if (!(level.getBlockEntity(entry.pos) instanceof DeathStorageBlockEntity storage)) {
                continue;
            }
            if (!storage.isActive()) {
                continue;
            }

            best = distance;
            found = storage;
        }

        return found;
    }

    /**
     * Vuelca los ítems en el almacén existente. No toca el tiempo ni el checkpoint:
     * el almacén se queda tal como estaba.
     */
    public static void merge(ServerPlayer player, DeathRecord target, DeathStorageBlockEntity storage,
                             DeathRecord incoming) {
        List<ItemStack> stacks = DeathCapture.flatten(incoming);
        storage.storeAll(stacks);

        target.inventory.addAll(incoming.inventory);
        mergeEquipment(target.armor, incoming.armor);
        mergeEquipment(target.curios, incoming.curios);
        target.backpacks.addAll(incoming.backpacks);

        // Waypoint de vuelta, sin tocar caducidad ni checkpoint.
        target.hidden = false;

        DeathHistory.get(player.server).setDirty();

        player.displayClientMessage(Component.translatable(
                "deathlootforge.result.merged", storage.getStorageName()), false);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);

        HistoryService.sendHistory(player, false);
    }

    private static void mergeEquipment(List<ItemStack> target, List<ItemStack> incoming) {
        for (int i = 0; i < incoming.size(); i++) {
            ItemStack stack = incoming.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            while (target.size() <= i) {
                target.add(ItemStack.EMPTY);
            }
            if (target.get(i).isEmpty()) {
                target.set(i, stack);
            } else {
                target.add(stack);
            }
        }
    }
}