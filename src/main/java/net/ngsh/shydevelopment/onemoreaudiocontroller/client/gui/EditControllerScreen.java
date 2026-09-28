package net.ngsh.shydevelopment.onemoreaudiocontroller.client.gui;

import net.ngsh.shydevelopment.onemoreaudiocontroller.AudioControllerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Small popup screen for creating a new JSON-backed controller, or renaming an existing one.
 * Both flows only ever touch {@code controllers.json}/{@code orders.json}; vanilla and
 * API-registered controllers can't be created or renamed this way (see
 * {@link ControllerManagerScreen}).
 */
public final class EditControllerScreen extends Screen {

    private final Screen parent;
    private final boolean isAdd;
    private final String existingId;
    private final String initialName;
    private final Runnable onSaved;

    private EditBox idBox;
    private EditBox nameBox;
    private Component errorMessage;
    private int errorY;

    public static EditControllerScreen forAdd(Screen parent, Runnable onSaved) {
        return new EditControllerScreen(parent, true, null, "", onSaved);
    }

    public static EditControllerScreen forRename(Screen parent, String id, String currentName, Runnable onSaved) {
        return new EditControllerScreen(parent, false, id, currentName, onSaved);
    }

    private EditControllerScreen(Screen parent, boolean isAdd, String existingId, String initialName, Runnable onSaved) {
        super(Component.literal(isAdd ? "Add Controller" : "Rename Controller"));
        this.parent = parent;
        this.isAdd = isAdd;
        this.existingId = existingId;
        this.initialName = initialName;
        this.onSaved = onSaved;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 50;

        if (isAdd) {
            idBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Id"));
            idBox.setMaxLength(48);
            idBox.setHint(Component.literal("id, es. tavern_music").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(idBox);
            y += 24;
        } else {
            idBox = null;
        }

        nameBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Name"));
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("Nome visualizzato").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(initialName == null ? "" : initialName);
        addRenderableWidget(nameBox);
        y += 28;

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onConfirm())
                .bounds(centerX - 100, y, 95, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 5, y, 95, 20).build());

        this.errorY = y + 26;
        setInitialFocus(idBox != null ? idBox : nameBox);
    }

    private void onConfirm() {
        String name = nameBox.getValue().trim();
        if (isAdd) {
            String id = idBox.getValue().trim().toLowerCase(Locale.ROOT);
            AudioControllerManager.AddControllerResult result = AudioControllerManager.addJsonController(id, name);
            switch (result) {
                case OK -> {
                    onSaved.run();
                    onClose();
                }
                case BLANK_ID -> errorMessage = Component.literal("Inserisci un id.").withStyle(ChatFormatting.RED);
                case INVALID_ID -> errorMessage = Component.literal("Id non valido: usa solo lettere minuscole, numeri e underscore.").withStyle(ChatFormatting.RED);
                case RESERVED_ID -> errorMessage = Component.literal("Questo id è riservato a una categoria vanilla.").withStyle(ChatFormatting.RED);
                case DUPLICATE_ID -> errorMessage = Component.literal("Esiste già un controller con questo id.").withStyle(ChatFormatting.RED);
            }
        } else {
            AudioControllerManager.renameJsonController(existingId, name);
            onSaved.run();
            onClose();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // super.render() already calls renderBackground(...) once - it must run first and only
        // once. Calling renderBackground() again afterwards (as this used to) re-runs the
        // blur/panorama pass on top of whatever this method already drew, blurring it out.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 70, 0xFFFFFF);
        if (errorMessage != null) {
            guiGraphics.drawCenteredString(this.font, errorMessage, this.width / 2, errorY, 0xFFFFFF);
        }
    }
}
