package dev.worldtrace.journal;

import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.util.InputValidation;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;

public final class EntryEditorScreen extends JournalFormScreen {

    private static final int NOTES_LENGTH = InputValidation.MAX_NOTES_LENGTH;
    private static final int TAGS_LENGTH = 320;

    private final TraceEntry entry;

    public EntryEditorScreen(DiscoveryManager manager, TraceEntry entry, Screen parent) {
        super(manager, parent, "Edit entry", List.of(
                FormField.text("Name", entry.name(), InputValidation.MAX_NAME_LENGTH),
                FormField.text("Notes", entry.notes(), NOTES_LENGTH),
                FormField.text("Tags (comma separated)", String.join(", ", entry.tags()), TAGS_LENGTH)));
        this.entry = entry;
    }

    @Override
    protected void defineButtons() {
        addButton("Save", this::onConfirm);
        addButton("Cancel", this::close);
        setButtonEnabled(0, !manager.store().readOnly());
    }

    @Override
    protected void onConfirm() {
        if (manager.store().readOnly()) {
            return;
        }
        manager.updateEntry(
                entry.id(),
                normalize(value(0), InputValidation.MAX_NAME_LENGTH),
                normalize(value(1), NOTES_LENGTH),
                InputValidation.parseTags(value(2)),
                entry.favorite(),
                entry.screenshotRef());
        close();
    }
}
