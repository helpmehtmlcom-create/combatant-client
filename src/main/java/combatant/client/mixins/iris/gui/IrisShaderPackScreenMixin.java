/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris.gui;

import combatant.client.compat.iris.gui.IrisGraphicsGuiRenderer;
import net.irisshaders.iris.gui.OldImageButton;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderPackScreen.class, remap = false)
public abstract class IrisShaderPackScreenMixin {
    @Shadow private ShaderPackSelectionList shaderPackList;
    @Shadow private ShaderPackOptionList shaderOptionList;
    @Shadow private Button screenSwitchButton;
    @Shadow private Button openFolderButton;
    @Shadow private OldImageButton showHideButton;
    @Shadow private boolean optionMenuOpen;
    @Shadow private boolean guiHidden;
    @Shadow private Component notificationDialog;
    @Shadow private int notificationDialogTimer;
    @Shadow private float backgroundInit;
    @Shadow @Final private FrameUpdateNotifier notifier;

    @Shadow
    protected abstract void init();

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void combatant$renderModernIrisGui(GuiGraphicsExtractor ctx,
                                                int mouseX,
                                                int mouseY,
                                                float delta,
                                                CallbackInfo ci) {
        if (!IrisGraphicsGuiRenderer.shouldUseModernUi()) return;
        // Preserve the non-presentation side effects Iris normally performs at the start of
        // extractRenderState even though its native widget drawing is replaced.
        notifier.onNewFrame();
        backgroundInit = 1.0f;
        if (IrisGraphicsGuiRenderer.render(
                (ShaderPackScreen) (Object) this,
                ctx,
                mouseX,
                mouseY,
                delta,
                shaderPackList,
                shaderOptionList,
                screenSwitchButton,
                openFolderButton,
                showHideButton,
                optionMenuOpen,
                guiHidden,
                notificationDialog,
                notificationDialogTimer
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void combatant$modernIrisMouseClicked(MouseButtonEvent event,
                                                   boolean doubleClick,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (IrisGraphicsGuiRenderer.mouseClicked(
                (ShaderPackScreen) (Object) this,
                event,
                doubleClick,
                shaderPackList,
                shaderOptionList,
                screenSwitchButton,
                openFolderButton,
                optionMenuOpen,
                guiHidden,
                () -> {
                    guiHidden = !guiHidden;
                    init();
                }
        )) {
            cir.setReturnValue(true);
        }
    }
}
