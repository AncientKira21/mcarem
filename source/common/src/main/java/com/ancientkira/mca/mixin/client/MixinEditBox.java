package com.ancientkira.mca.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EditBox.class)
public abstract class MixinEditBox {
    @ModifyVariable(method = "keyPressed", at = @At("HEAD"), argsOnly = true)
    private KeyEvent mca$clipboardShortcuts(KeyEvent event) {
        var screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.getClass().getName().startsWith("com.ancientkira.mca.client.gui.")) return event;
        boolean shortcut = event.key() == GLFW.GLFW_KEY_A || event.key() == GLFW.GLFW_KEY_C
                || event.key() == GLFW.GLFW_KEY_X || event.key() == GLFW.GLFW_KEY_V;
        if (shortcut && (event.hasControlDown() || event.hasControlDownWithQuirk())) {
            return new KeyEvent(event.key(), event.scancode(), event.modifiers() | InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER);
        }
        return event;
    }
}
