package com.example.examplemod.server;

import com.example.examplemod.Config;
import com.example.examplemod.storage.DeathHistory;
import com.example.examplemod.storage.DeathRecord;
import com.example.examplemod.storage.DeathStorageBlockEntity;
import com.example.examplemod.storage.DeathStorageManager;
import com.example.examplemod.storage.ModBlocks;
import com.example.examplemod.storage.SafePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Crea el registro de muerte y decide almacén nuevo o fusión. */
public final class DeathCapture {

    private DeathCapture() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("deathlootforge");

    public static DeathRecord prepare(ServerPlayer player, DamageSource source) {
        DeathRecord record = new DeathRecord();

        record.ownerId = player.getUUID();
        record.ownerName = player.getGameProfile().getName();
        record.dimension = player.level().dimension().location().toString();
        record.pos = player.blockPosition();
        record.cause = source.getLocalizedDeathMessage(player).getString();
        record.timestamp = System.currentTimeMillis();
        record.selectedSlot = player.getInventory().selected;

        for (int i = 0; i < 36; i++) {
            record.inventory.add(ItemStack.EMPTY);
        }
        for (int i = 0; i < 4; i++) {
            record.armor.add(ItemStack.EMPTY);
        }

        return record;
    }

    public static void commit(ServerPlayer player, DeathRecord record) {
    List<ItemStack> items = flatten(record);
    if (items.isEmpty()) {
        LOGGER.info("[deathloot] muerte sin objetos, no se crea nada");
        return;
    }

    ServerLevel level = player.serverLevel();
    DeathStorageBlockEntity near = StorageRouting.nearestOwned(player, level);

    LOGGER.info("[deathloot] muerte con {} objetos, radio {} -> cercano: {}",
            items.size(), StorageRouting.radius(), near == null ? "no" : near.getBlockPos());

    if (near != null) {
        DeathHistory history = DeathHistory.get(player.server);
        DeathRecord target = history.byStorage(player.getUUID(), near.getStorageId());

        if (target == null) {
            target = adopt(player, near, record);
            LOGGER.info("[deathloot] registro ausente para el almacen, se reconstruye");
        }

        StorageRouting.merge(player, target, near, record);
        return;
    }

    create(player, record, level, items);
}

/** Reconstruye el registro de un almacén que se quedó sin él. */
private static DeathRecord adopt(ServerPlayer player, DeathStorageBlockEntity storage, DeathRecord source) {
    DeathRecord target = new DeathRecord();

    target.ownerId = player.getUUID();
    target.ownerName = player.getGameProfile().getName();
    target.dimension = source.dimension;
    target.pos = storage.getBlockPos();
    target.cause = source.cause;
    target.timestamp = source.timestamp;
    target.selectedSlot = source.selectedSlot;
    target.storageId = storage.getStorageId();
    target.storageNameIndex = storage.getNameIndex();

    DeathHistory.get(player.server).add(target);
    return target;
}

    private static void create(ServerPlayer player, DeathRecord record, ServerLevel level, List<ItemStack> items) {
        BlockPos spot = SafePlacement.find(level, record.pos);
        if (spot == null) {
            spot = record.pos;
        }

        level.setBlockAndUpdate(spot, ModBlocks.DEATH_STORAGE.get().defaultBlockState());

        if (!(level.getBlockEntity(spot) instanceof DeathStorageBlockEntity storage)) {
            for (ItemStack stack : items) {
                player.drop(stack, true);
            }
            return;
        }

        DeathStorageManager manager = DeathStorageManager.get(player.server);
        int index = manager.nextNameIndex(player.getUUID());
        UUID storageId = UUID.randomUUID();

        storage.setStorageId(storageId);
        storage.bindOwner(player, index);
        storage.setCheckpoint(true);
        storage.setSkullColor(DeathStorageBlockEntity.DEFAULT_COLOR);
        storage.setExpiresAt(System.currentTimeMillis() + Config.storageMinutes * 60_000L);
        storage.reserve(items.size());
        storage.storeAll(items);
        storage.sync();

        record.storageId = storageId;
        record.storageNameIndex = index;
        record.pos = spot;

        manager.register(storageId, player.getUUID(), level.dimension(), spot, storage.getExpiresAt());
        manager.enforceLimit(player.server, player.getUUID());

        DeathHistory.get(player.server).add(record);

        player.displayClientMessage(Component.translatable(
                "deathlootforge.result.stored", storage.getStorageName()), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);

        HistoryService.sendHistory(player, false);
    }

    /** Todo lo que hay que guardar, en orden estable. */
    public static List<ItemStack> flatten(DeathRecord record) {
        List<ItemStack> out = new ArrayList<>();

        for (ItemStack stack : record.inventory) {
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        for (ItemStack stack : record.armor) {
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        if (!record.offhand.isEmpty()) {
            out.add(record.offhand);
        }
        for (ItemStack stack : record.curios) {
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        for (DeathRecord.Backpack backpack : record.backpacks) {
            for (ItemStack stack : backpack.contents) {
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }

        return out;
    }
}