package net.ngsh.shydevelopment.onemoreaudiocontroller.client.gui;

import net.ngsh.shydevelopment.onemoreaudiocontroller.AudioControllerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game replacement for opening the vanilla Sound Options screen directly from Catalogue/YACL's
 * "Config" button (or the Mods menu). Lets the player add, rename, delete and drag-reorder
 * JSON-backed controllers without touching {@code controllers.json}/{@code orders.json} by hand,
 * and reorder vanilla/API controllers alongside them (those two can only be reordered here, not
 * created/renamed/deleted - see the class doc on {@link AudioControllerManager}).
 */
public final class ControllerManagerScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final Component DELETE_LABEL = Component.literal("Elimina").withStyle(ChatFormatting.RED);
    private static final Component RENAME_LABEL = Component.literal("Rinomina").withStyle(ChatFormatting.YELLOW);

    private final Screen returnScreen;
    private List<String> workingOrder;

    private int listX;
    private int listWidth;
    private int listTop;
    private int listBottom;
    private int scrollOffset;
    private int deleteWidth;
    private int renameWidth;

    private String draggingId;
    private double dragGrabOffsetY;

    public ControllerManagerScreen(Screen returnScreen) {
        super(Component.literal("Gestione Controller Audio"));
        this.returnScreen = returnScreen;
        AudioControllerManager.reload();
        this.workingOrder = new ArrayList<>(AudioControllerManager.order());
    }

    @Override
    protected void init() {
        this.listWidth = Math.min(400, this.width - 40);
        this.listX = (this.width - listWidth) / 2;
        this.listTop = 30;

        int barY = this.height - 50;
        this.listBottom = barY - 8;

        int btnWidth = 150;
        addRenderableWidget(Button.builder(Component.literal("Aggiungi controller"), b -> openAdd())
                .bounds(this.width / 2 - btnWidth - 5, barY, btnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apri Music & Sounds"), b -> openSoundOptions())
                .bounds(this.width / 2 + 5, barY, btnWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 100, barY + 24, 200, 20).build());

        this.deleteWidth = this.font.width(DELETE_LABEL);
        this.renameWidth = this.font.width(RENAME_LABEL);

        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
    }

    private void refresh() {
        this.workingOrder = new ArrayList<>(AudioControllerManager.order());
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());
    }

    private void openAdd() {
        this.minecraft.setScreen(EditControllerScreen.forAdd(this, this::refresh));
    }

    private void openRename(String id) {
        AudioControllerManager.ControllerDefinition definition = AudioControllerManager.controllerById(id);
        String currentName = definition != null ? definition.defaultName : id;
        this.minecraft.setScreen(EditControllerScreen.forRename(this, id, currentName, this::refresh));
    }

    private void openSoundOptions() {
        this.minecraft.setScreen(new SoundOptionsScreen(this, this.minecraft.options));
    }

    private void confirmDelete(String id) {
        Component name = labelFor(id);
        Component message = Component.literal("Vuoi eliminare \"" + name.getString() + "\"? Puoi ricrearlo in seguito con lo stesso id.");
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                AudioControllerManager.removeJsonController(id);
                refresh();
            }
            this.minecraft.setScreen(this);
        }, Component.literal("Elimina controller"), message));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(returnScreen);
    }

    private static Component labelFor(String id) {
        AudioControllerManager.ControllerDefinition definition = AudioControllerManager.controllerById(id);
        if (definition != null) {
            return Component.translatableWithFallback(definition.translationKey, definition.defaultName);
        }
        return Component.translatable("soundCategory." + id);
    }

    private static boolean isEditable(String id) {
        return AudioControllerManager.isJsonControlled(id) && !AudioControllerManager.isApiControlled(id);
    }

    private int maxScroll() {
        int total = workingOrder.size() * ROW_HEIGHT;
        return Math.max(0, total - (listBottom - listTop));
    }

    private int rowTop(int index) {
        return listTop + index * ROW_HEIGHT - scrollOffset;
    }

    private int rowRight() {
        return listX + listWidth;
    }

    private int indexForY(double y) {
        int index = (int) Math.floor((y - listTop + scrollOffset) / (double) ROW_HEIGHT);
        return Mth.clamp(index, 0, Math.max(0, workingOrder.size() - 1));
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // super.render() already calls renderBackground(...) once - it must run first and only
        // once. Calling renderBackground() again afterwards (as this used to) re-runs the
        // blur/panorama pass on top of whatever this method already drew, blurring it out.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        guiGraphics.enableScissor(listX, listTop, listX + listWidth, listBottom);
        int slot = 0;
        for (String id : workingOrder) {
            if (id.equals(draggingId)) {
                continue;
            }
            int rowTop = rowTop(slot);
            if (rowTop + ROW_HEIGHT >= listTop && rowTop <= listBottom) {
                drawRow(guiGraphics, id, rowTop, mouseX, mouseY, false);
            }
            slot++;
        }
        if (draggingId != null) {
            int floatingTop = (int) (mouseY - dragGrabOffsetY);
            drawRow(guiGraphics, draggingId, floatingTop, mouseX, mouseY, true);
        }
        guiGraphics.disableScissor();
    }

    private void drawRow(GuiGraphics g, String id, int rowTop, int mouseX, int mouseY, boolean floating) {
        int left = listX;
        int right = rowRight();
        boolean hovered = !floating && mouseX >= left && mouseX <= right && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT - 2;
        int background = floating ? 0xAA355A8C : (hovered ? 0x55FFFFFF : 0x33000000);
        g.fill(left, rowTop, right, rowTop + ROW_HEIGHT - 2, background);

        // Drag handle: three short lines.
        for (int i = 0; i < 3; i++) {
            g.fill(left + 6, rowTop + 5 + i * 3, left + 14, rowTop + 6 + i * 3, 0xFFAAAAAA);
        }

        g.drawString(this.font, labelFor(id), left + 24, rowTop + 6, 0xFFFFFF, false);

        if (isEditable(id)) {
            int deleteX0 = right - 8 - deleteWidth;
            int renameX0 = deleteX0 - 10 - renameWidth;
            g.drawString(this.font, RENAME_LABEL, renameX0, rowTop + 6, 0xFFFFFF, false);
            g.drawString(this.font, DELETE_LABEL, deleteX0, rowTop + 6, 0xFFFFFF, false);
        } else {
            String tag = AudioControllerManager.isApiControlled(id) ? "(api)" : "(vanilla)";
            int tagWidth = this.font.width(tag);
            g.drawString(this.font, tag, right - 8 - tagWidth, rowTop + 6, 0x999999, false);
        }
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int slot = 0;
            for (String id : workingOrder) {
                int rowTop = rowTop(slot);
                if (mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT - 2 && mouseX >= listX && mouseX <= rowRight()
                        && rowTop >= listTop - ROW_HEIGHT && rowTop <= listBottom) {
                    if (isEditable(id)) {
                        int deleteX0 = rowRight() - 8 - deleteWidth;
                        int renameX0 = deleteX0 - 10 - renameWidth;
                        if (mouseX >= deleteX0 - 4 && mouseX <= rowRight()) {
                            confirmDelete(id);
                            return true;
                        }
                        if (mouseX >= renameX0 - 4 && mouseX < deleteX0 - 4) {
                            openRename(id);
                            return true;
                        }
                    }
                    draggingId = id;
                    dragGrabOffsetY = mouseY - rowTop;
                    return true;
                }
                slot++;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingId != null) {
            int currentIndex = workingOrder.indexOf(draggingId);
            int targetIndex = indexForY(mouseY - dragGrabOffsetY + ROW_HEIGHT / 2.0);
            if (targetIndex != currentIndex && currentIndex >= 0) {
                workingOrder.remove(currentIndex);
                workingOrder.add(targetIndex, draggingId);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingId != null) {
            draggingId = null;
            AudioControllerManager.setOrder(workingOrder);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX <= rowRight() && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = Mth.clamp((int) (scrollOffset - scrollY * ROW_HEIGHT), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
