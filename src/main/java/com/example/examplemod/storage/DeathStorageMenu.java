package com.example.examplemod.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DeathStorageMenu extends AbstractContainerMenu {

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int GRID_X = 19;
    public static final int GRID_Y = 28;
    private static final int HIDDEN_Y = -1000;

    private final Container container;
    private final BlockPos pos;
    @Nullable
    private final DeathStorageBlockEntity storage;
    private final int capacity;
    private int scrollRow;

    public DeathStorageMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, resolve(inventory.player, pos), pos);
    }

    private static Container resolve(Player player, BlockPos pos) {
        return player.level().getBlockEntity(pos) instanceof DeathStorageBlockEntity be
                ? be
                : new SimpleContainer(DeathStorageBlockEntity.SIZE);
    }

    public DeathStorageMenu(int id, Inventory inventory, Container container, BlockPos pos) {
        super(ModMenus.DEATH_STORAGE.get(), id);
        this.container = container;
        this.pos = pos;
        this.storage = container instanceof DeathStorageBlockEntity be ? be : null;
        this.capacity = container.getContainerSize();

        container.startOpen(inventory.player);

        for (int i = 0; i < capacity; i++) {
    addSlot(new StorageSlot(container, i, GRID_X, HIDDEN_Y));
}

applyScroll();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, GRID_X + col * 18, 150 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, GRID_X + col * 18, 208));
        }
    }

    public int maxScroll() {
        return Math.max(0, (capacity + COLUMNS - 1) / COLUMNS - ROWS);
    }

    public int getCapacity() {
    return capacity;
}

    public int getScrollRow() {
        return scrollRow;
    }

    public void scroll(int rows) {
        int next = Math.max(0, Math.min(maxScroll(), scrollRow + rows));
        if (next == scrollRow) {
            return;
        }
        scrollRow = next;
        applyScroll();
    }

    private void applyScroll() {
    for (int i = 0; i < capacity; i++) {
        int row = i / COLUMNS;
        int visible = row - scrollRow;
        int x = GRID_X + (i % COLUMNS) * 18;
        int y = (visible < 0 || visible >= ROWS) ? HIDDEN_Y : GRID_Y + visible * 18;

        slots.set(i, new StorageSlot(container, i, x, y));
    }
}

    private static final class StorageSlot extends Slot {
    private StorageSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }
}

    @Nullable
    public DeathStorageBlockEntity getStorage() {
        return storage;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return storage != null ? storage.stillValid(player) : true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (!slot.hasItem()) {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < capacity) {
            if (!moveItemStackTo(stack, capacity, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, capacity, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}