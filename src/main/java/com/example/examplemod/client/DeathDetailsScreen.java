package com.example.examplemod.client;

import com.example.examplemod.storage.DeathRecord;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class DeathDetailsScreen extends Screen {

    private static final int W = 470;
    private static final int H = 300;

    private static final int SLOT = 20;
    private static final int STEP = 21;
    private static final int COLS = 9;
    private static final int VISIBLE_ROWS = 4;
    private static final int GRID_X = 14;
    private static final int GRID_Y = 200;

    private static final String[] ARMOR = {
            "deathlootforge.details.helmet", "deathlootforge.details.chest",
            "deathlootforge.details.legs", "deathlootforge.details.boots",
            "deathlootforge.details.offhand"};

    private static final String[] CURIOS = {
            "deathlootforge.details.bag", "deathlootforge.details.amulet",
            "deathlootforge.details.ring", "deathlootforge.details.curio4",
            "deathlootforge.details.curio5"};

    private int left;
    private int top;

    private final DeathRecord record;
    private final Screen parent;
    private ItemStack tooltip = ItemStack.EMPTY;

    private long noticeUntil;
    private String notice = "";

    private int scrollRow;
    private boolean dragging;

    public DeathDetailsScreen(DeathRecord record, Screen parent) {
        super(Component.translatable("deathlootforge.details.title", record.storageNameIndex));
        this.record = record;
        this.parent = parent;
    }

    private String t(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clampScroll();
    }

    private int totalRows() {
        return Math.max(VISIBLE_ROWS, (Math.max(COLS * VISIBLE_ROWS, record.inventory.size()) + COLS - 1) / COLS);
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - VISIBLE_ROWS);
    }

    private void clampScroll() {
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        UiKit.round(graphics, left, top, W, H, UiKit.CARD, UiKit.EDGE);
        graphics.fill(left + 1, top + 1, left + W - 1, top + 26, 0x44000000);
        graphics.fill(left + 1, top + 26, left + W - 1, top + 27, UiKit.EDGE);
        graphics.drawString(font, title, left + 12, top + 9, UiKit.WHITE, false);
        UiKit.close(graphics, left + W - 18, top + 9, UiKit.DIM);

        tooltip = ItemStack.EMPTY;

        renderGear(graphics, mouseX, mouseY);
        renderInventory(graphics, mouseX, mouseY);
        renderInfo(graphics);

        if (!tooltip.isEmpty()) {
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }

        renderNotice(graphics);
    }

    private void renderGear(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, t("deathlootforge.details.gear"), left + 14, top + 36, UiKit.WHITE, false);

        for (int i = 0; i < 5; i++) {
            int y = top + 48 + i * 26;
            ItemStack stack = i < 4 ? record.armorPiece(3 - i) : record.offhand;

            slot(graphics, left + 14, y, stack, mouseX, mouseY, i < 4 ? i : -1);
            graphics.drawString(font, t(ARMOR[i]), left + 14 + SLOT + 5, y + 6, UiKit.DIM, false);
        }

        graphics.drawString(font, t("deathlootforge.details.character"), left + 118, top + 36, UiKit.WHITE, false);
        UiKit.round(graphics, left + 112, top + 48, 108, 128, 0xFF141518, 0);

        Mannequin.render(graphics, record, left + 166, top + 168, 42, mouseX, mouseY);

        graphics.drawString(font, t("deathlootforge.details.curios"), left + W - 88, top + 36, UiKit.WHITE, false);

        for (int i = 0; i < 5; i++) {
            int y = top + 48 + i * 26;
            ItemStack stack = i < record.curios.size() ? record.curios.get(i) : ItemStack.EMPTY;

            slot(graphics, left + W - 34, y, stack, mouseX, mouseY, i);

            String label = t(CURIOS[i]);
            graphics.drawString(font, label, left + W - 34 - 5 - font.width(label), y + 6, UiKit.DIM, false);
        }
    }

    private void renderInventory(GuiGraphics graphics, int mouseX, int mouseY) {
        String header = t("deathlootforge.details.inventory");
        graphics.drawString(font, header, left + 14, top + 188, UiKit.WHITE, false);

        if (maxScroll() > 0) {
            graphics.drawString(font, (scrollRow + VISIBLE_ROWS) + "/" + totalRows(),
                    left + 14 + font.width(header) + 6, top + 188, UiKit.FAINT, false);
        }

        clampScroll();

        int areaX = left + GRID_X - 2;
        int areaY = top + GRID_Y - 2;
        int areaW = COLS * STEP + 2;
        int areaH = VISIBLE_ROWS * STEP;

        graphics.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int absolute = scrollRow + row;

            for (int col = 0; col < COLS; col++) {
                int index = absolute * COLS + col;
                int x = left + GRID_X + col * STEP;
                int y = top + GRID_Y + row * STEP;

                ItemStack stack = index < record.inventory.size() ? record.inventory.get(index) : ItemStack.EMPTY;
                slot(graphics, x, y, stack, mouseX, mouseY, index);
            }
        }

        graphics.disableScissor();

        renderScrollbar(graphics);
    }

    private int barX() {
        return left + GRID_X + COLS * STEP + 1;
    }

    private int barY() {
        return top + GRID_Y;
    }

    private int barH() {
        return VISIBLE_ROWS * STEP - 1;
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll() <= 0) {
            return;
        }

        UiKit.round(graphics, barX(), barY(), 3, barH(), 0xFF141518, UiKit.EDGE);

        int thumb = Math.max(14, barH() * VISIBLE_ROWS / totalRows());
        int travel = barH() - thumb;
        int offset = maxScroll() == 0 ? 0 : travel * scrollRow / maxScroll();

        UiKit.round(graphics, barX(), barY() + offset, 3, thumb, UiKit.GREEN, 0);
    }

    private void scrollBy(double rows) {
        scrollRow = Mth.clamp(scrollRow + (int) Math.round(rows), 0, maxScroll());
    }

    private void scrollToMouse(double mouseY) {
        int thumb = Math.max(14, barH() * VISIBLE_ROWS / totalRows());
        int travel = Math.max(1, barH() - thumb);
        double ratio = (mouseY - barY() - thumb / 2.0D) / travel;
        scrollRow = Mth.clamp((int) Math.round(ratio * maxScroll()), 0, maxScroll());
    }

    private void renderInfo(GuiGraphics graphics) {
        graphics.drawString(font, t("deathlootforge.details.info"), left + 216, top + 188, UiKit.WHITE, false);

        UiKit.round(graphics, left + 216, top + 200, W - 230, 44, 0xFF141518, UiKit.EDGE);

        graphics.drawString(font, t("deathlootforge.details.coords"), left + 222, top + 205, UiKit.DIM, false);
        graphics.drawString(font, UiKit.coordsPretty(record.pos), left + 222, top + 215, UiKit.WHITE, false);
        UiKit.copy(graphics, left + W - 32, top + 211, UiKit.FAINT);

        graphics.drawString(font, UiKit.date(record.timestamp) + "   " + UiKit.hour(record.timestamp),
                left + 222, top + 230, UiKit.DIM, false);

        String hint = t("deathlootforge.details.bag.hint");
        graphics.drawString(font, hint, left + 216, top + 254, UiKit.FAINT, false);
    }

    private void renderNotice(GuiGraphics graphics) {
        if (System.currentTimeMillis() > noticeUntil || notice.isEmpty()) {
            return;
        }

        int width = font.width(notice) + 16;
        int x = left + W - 22 - width;
        int y = top + 194;

        UiKit.round(graphics, x, y, width, 16, 0xF010251A, UiKit.GREEN);
        graphics.drawString(font, notice, x + 8, y + 4, UiKit.GREEN, false);
    }

    private void slot(GuiGraphics graphics, int x, int y, ItemStack stack, int mouseX, int mouseY, int index) {
        UiKit.round(graphics, x, y, SLOT, SLOT, 0xFF141518, UiKit.EDGE);

        if (stack.isEmpty()) {
            return;
        }

        graphics.renderItem(stack, x + 2, y + 2);

        if (inside(mouseX, mouseY, x, y, SLOT, SLOT)) {
            tooltip = stack;

            if (backpackIndex(stack, index) >= 0) {
                UiKit.outline(graphics, x - 1, y - 1, SLOT + 2, SLOT + 2, UiKit.GREEN);
            }
        }
    }

    private int backpackIndex(ItemStack stack, int slotIndex) {
        if (stack.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < record.backpacks.size(); i++) {
            DeathRecord.Backpack backpack = record.backpacks.get(i);

            if (backpack.slot == slotIndex && ItemStack.isSameItemSameTags(backpack.icon, stack)) {
                return i;
            }
            if (ItemStack.isSameItemSameTags(backpack.icon, stack)) {
                return i;
            }
        }

        return -1;
    }

    private void openBackpack(int index) {
        DeathRecord.Backpack backpack = record.backpacks.get(index);
        minecraft.setScreen(BackpackViewScreen.open(backpack.icon, this));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, left + W - 20, top + 4, 14, 14)) {
            onClose();
            return true;
        }

        if (inside(mouseX, mouseY, left + W - 36, top + 206, 16, 18)) {
            minecraft.keyboardHandler.setClipboard(
                    record.pos.getX() + " " + record.pos.getY() + " " + record.pos.getZ());
            notice = t("deathlootforge.detail.copied");
            noticeUntil = System.currentTimeMillis() + 3000L;
            return true;
        }

        if (maxScroll() > 0 && inside(mouseX, mouseY, barX() - 2, barY(), 7, barH())) {
            dragging = true;
            scrollToMouse(mouseY);
            return true;
        }

        for (int i = 0; i < 5; i++) {
            int y = top + 48 + i * 26;

            if (inside(mouseX, mouseY, left + 14, y, SLOT, SLOT)) {
                ItemStack stack = i < 4 ? record.armorPiece(3 - i) : record.offhand;
                int index = backpackIndex(stack, i < 4 ? i : -1);
                if (index >= 0) {
                    openBackpack(index);
                    return true;
                }
            }

            if (inside(mouseX, mouseY, left + W - 34, y, SLOT, SLOT)) {
                ItemStack stack = i < record.curios.size() ? record.curios.get(i) : ItemStack.EMPTY;
                int index = backpackIndex(stack, i);
                if (index >= 0) {
                    openBackpack(index);
                    return true;
                }
            }
        }

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int absolute = scrollRow + row;

            for (int col = 0; col < COLS; col++) {
                int index = absolute * COLS + col;
                int x = left + GRID_X + col * STEP;
                int y = top + GRID_Y + row * STEP;

                if (!inside(mouseX, mouseY, x, y, SLOT, SLOT)) {
                    continue;
                }

                ItemStack stack = index < record.inventory.size() ? record.inventory.get(index) : ItemStack.EMPTY;
                int index2 = backpackIndex(stack, index);
                if (index2 >= 0) {
                    openBackpack(index2);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside(mouseX, mouseY, left + GRID_X - 2, top + GRID_Y - 2, COLS * STEP + 2, VISIBLE_ROWS * STEP)) {
            scrollBy(-delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}