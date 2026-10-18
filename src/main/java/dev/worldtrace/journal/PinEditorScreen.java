package dev.worldtrace.journal;

import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.discovery.StructureCatalog;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.util.InputValidation;
import dev.worldtrace.util.NameFormatting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class PinEditorScreen extends JournalFormScreen {

    private static final int LABEL_LENGTH = InputValidation.MAX_LABEL_LENGTH;

    private final MinecraftClient mc;
    private final BlockPos position;
    private final String dimensionId;

    private final List<String> structureOptions = new ArrayList<>();
    private int structureIndex;
    private int radiusIndex;

    public PinEditorScreen(DiscoveryManager manager, MinecraftClient mc, Screen parent) {
        super(manager, parent, "Place watch pin", List.of(
                FormField.text("Label", "", LABEL_LENGTH),
                FormField.choice("Expects", "None (landmark)"),
                FormField.choice("Arrival radius", TracePin.DEFAULT_RADIUS + " m"),
                FormField.text("Note", "", InputValidation.MAX_NOTES_LENGTH)));
        this.mc = mc;
        this.position = mc.player.getBlockPos();
        this.dimensionId = mc.world.getRegistryKey().getValue().toString();
        this.structureOptions.add(null);
        this.structureOptions.addAll(StructureCatalog.allStructureIds());
    }

    @Override
    protected void defineButtons() {
        updateChoiceLabels();
        addButton("Place pin", this::onConfirm);
        addButton("Cancel", this::close);
        setButtonEnabled(0, !manager.store().readOnly());
    }

    private void updateChoiceLabels() {
        String structure = structureOptions.get(structureIndex);
        setChoice(1, structure == null ? "None (landmark)" : NameFormatting.prettifyIdentifier(structure));
        int[] radii = radii();
        setChoice(2, radii[radiusIndex] + " m");
    }

    private static int[] radii() {
        return new int[]{TracePin.MIN_RADIUS, 32, TracePin.DEFAULT_RADIUS, 80, TracePin.MAX_RADIUS};
    }

    @Override
    protected void cycleChoice(int index, int direction) {
        if (index == 1) {
            structureIndex = Math.floorMod(structureIndex + direction, structureOptions.size());
        } else if (index == 2) {
            radiusIndex = Math.floorMod(radiusIndex + direction, radii().length);
        }
        updateChoiceLabels();
    }

    @Override
    protected void onConfirm() {
        if (manager.store().readOnly()) {
            return;
        }
        String structure = structureOptions.get(structureIndex);
        int radius = radii()[radiusIndex];

        String label = normalize(value(0), LABEL_LENGTH);
        if (label.isEmpty()) {
            label = structure == null
                    ? "Landmark at " + position.getX() + ", " + position.getY() + ", " + position.getZ()
                    : NameFormatting.prettifyIdentifier(structure);
        }

        TracePin pin = TracePin.structure(label, structure, dimensionId, position.getX(), position.getY(),
                position.getZ(), radius, normalize(value(3), InputValidation.MAX_NOTES_LENGTH),
                System.currentTimeMillis());
        manager.addPin(pin);
        close();
    }
}
