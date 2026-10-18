package dev.worldtrace;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;

public final class WorldTraceKeys {

    public static final String CATEGORY = "key.categories.worldtrace";

    private static KeyBinding openJournal;
    private static KeyBinding createBookmark;
    private static KeyBinding toggleDestination;

    private WorldTraceKeys() {
    }

    public static void register() {
        openJournal = new KeyBinding(
                "key.worldtrace.journal",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY);
        createBookmark = new KeyBinding(
                "key.worldtrace.bookmark",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CATEGORY);
        toggleDestination = new KeyBinding(
                "key.worldtrace.destination",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                CATEGORY);

        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(openJournal);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(createBookmark);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(toggleDestination);
    }

    public static boolean journalPressed() {
        return openJournal != null && openJournal.wasPressed();
    }

    public static boolean bookmarkPressed() {
        return createBookmark != null && createBookmark.wasPressed();
    }

    public static boolean destinationPressed() {
        return toggleDestination != null && toggleDestination.wasPressed();
    }
}
