package com.example.examplemod;

import com.example.examplemod.server.DeathCapture;
import com.example.examplemod.storage.DeathRecord;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mod.EventBusSubscriber(
        modid = ExampleMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class DeathLootHandler {

    private static final ItemStack EMPTY = ItemStack.EMPTY;

    private static Boolean curiosLoaded;

    private static boolean curiosLoaded() {
        if (curiosLoaded == null) {
            curiosLoaded = ModList.get().isLoaded("curios");
        }
        return curiosLoaded;
    }

    private static final class Queued {
        private final ItemStack stack;
        private final DeathRecord.Backpack record;

        private Queued(ItemStack stack, DeathRecord.Backpack record) {
            this.stack = stack;
            this.record = record;
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (AegisData.hasProtection(player)) {
            return;
        }

        final int chance = Config.deathLootChance;
        if (chance <= 0) {
            return;
        }

        final RandomSource random = player.getRandom();
        final boolean certain = chance >= 100;
        final Inventory inventory = player.getInventory();
        final Deque<Queued> backpacks = new ArrayDeque<>();

        final DeathRecord record = DeathCapture.prepare(player, event.getSource());

        // Inventario principal (0-35).
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof BackpackItem) {
                backpacks.push(queueBackpack(stack, record, slot, false));
                continue;
            }

            if (certain || random.nextInt(100) < chance) {
                record.inventory.set(slot, stack);
                inventory.setItem(slot, EMPTY);
            }
        }

        clearOrQueue(inventory.armor, record.armor, backpacks, record, random, certain, chance, false);
        clearOrQueue(inventory.offhand, null, backpacks, record, random, certain, chance, true);

        if (curiosLoaded()) {
            CuriosApi.getCuriosInventory(player).ifPresent(curiosHandler -> {
                IItemHandlerModifiable curios = curiosHandler.getEquippedCurios();

                for (int slot = 0, size = curios.getSlots(); slot < size; slot++) {
                    ItemStack stack = curios.getStackInSlot(slot);

                    if (!stack.isEmpty() && stack.getItem() instanceof BackpackItem) {
                        backpacks.push(queueBackpack(stack, record, slot, true));
                    }
                }
            });
        }

        Queued queued;
        while ((queued = backpacks.poll()) != null) {
            processBackpack(queued, backpacks, record, random, certain, chance);
        }

        DeathCapture.commit(player, record);
    }

    private static Queued queueBackpack(ItemStack stack, DeathRecord record, int slot, boolean curio) {
        DeathRecord.Backpack entry = new DeathRecord.Backpack();
        entry.slot = slot;
        entry.curiosSlot = curio;
        entry.icon = stack.copyWithCount(1);
        record.backpacks.add(entry);
        return new Queued(stack, entry);
    }

    private static void clearOrQueue(NonNullList<ItemStack> slots,
                                     List<ItemStack> target,
                                     Deque<Queued> backpacks,
                                     DeathRecord record,
                                     RandomSource random,
                                     boolean certain,
                                     int chance,
                                     boolean offhand) {
        for (int slot = 0, size = slots.size(); slot < size; slot++) {
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof BackpackItem) {
                backpacks.push(queueBackpack(stack, record, slot, false));
                continue;
            }

            if (certain || random.nextInt(100) < chance) {
                if (offhand) {
                    record.offhand = stack;
                } else if (slot < target.size()) {
                    target.set(slot, stack);
                } else {
                    while (target.size() <= slot) {
                        target.add(ItemStack.EMPTY);
                    }
                    target.set(slot, stack);
                }
                slots.set(slot, EMPTY);
            }
        }
    }

    private static void processBackpack(Queued queued,
                                        Deque<Queued> backpacks,
                                        DeathRecord record,
                                        RandomSource random,
                                        boolean certain,
                                        int chance) {
        ItemStack backpack = queued.stack;

        if (backpack.isEmpty() || !(backpack.getItem() instanceof BackpackItem)) {
            return;
        }

        var contents = new BackpackWrapper(backpack).getInventoryHandler();

        for (int slot = 0, size = contents.getSlots(); slot < size; slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof BackpackItem) {
                backpacks.push(queueBackpack(stack, record, slot, false));
                continue;
            }

            if (certain || random.nextInt(100) < chance) {
                while (queued.record.contents.size() <= slot) {
                    queued.record.contents.add(ItemStack.EMPTY);
                }
                queued.record.contents.set(slot, stack);
                contents.setStackInSlot(slot, EMPTY);
            }
        }
    }
}