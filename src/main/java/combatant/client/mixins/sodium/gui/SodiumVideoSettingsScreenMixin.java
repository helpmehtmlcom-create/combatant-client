/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumGraphicsGuiRenderer;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt;
import net.caffeinemc.mods.sodium.client.gui.widgets.DonationButtonWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.KeyBoundButtonWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.PageListWidget;
import net.caffeinemc.mods.sodium.client.gui.widgets.ScrollableTooltip;
import net.caffeinemc.mods.sodium.client.gui.widgets.SearchWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Keeps Sodium's complete widget/input/state graph and replaces only presentation extraction.
 * The hook becomes a no-op in RuntimeGate panic or when the VisualConfig switch is disabled.
 */
@Mixin(value = VideoSettingsScreen.class, remap = false)
public abstract class SodiumVideoSettingsScreenMixin {
    @Unique private boolean combatant$donationSuppressed;

    @Shadow private PageListWidget pageList;
    @Shadow private SearchWidget searchWidget;
    @Shadow private OptionListWidget optionList;
    @Shadow private KeyBoundButtonWidget applyButton;
    @Shadow private KeyBoundButtonWidget closeButton;
    @Shadow private KeyBoundButtonWidget undoButton;
    @Shadow private List<KeyBoundButtonWidget> shortcutButtons;
    @Shadow private DonationButtonWidget donateButton;
    @Shadow private boolean hasPendingChanges;
    @Shadow @Final private ScrollableTooltip tooltip;
    @Shadow private ScreenPrompt prompt;

    @Shadow
    private void updateControls(int mouseX, int mouseY) {
        throw new AssertionError();
    }

    @Shadow
    private void updateSearchWidgetWidth() {
        throw new AssertionError();
    }

    @Inject(method = "rebuild", at = @At("TAIL"), remap = false)
    private void combatant$removeDonationUi(CallbackInfo ci) {
        if (!SodiumGraphicsGuiRenderer.shouldUseModernUi() || donateButton == null) return;
        donateButton.updateDisplay((VideoSettingsScreen) (Object) this, false);
        combatant$donationSuppressed = true;
        updateSearchWidgetWidth();
    }

    @Inject(method = "openDonationPrompt", at = @At("HEAD"), cancellable = true, remap = false)
    private void combatant$removeDonationPrompt(SodiumOptions options, CallbackInfo ci) {
        if (SodiumGraphicsGuiRenderer.shouldUseModernUi()) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, remap = false)
    private void combatant$renderModernSodiumGui(GuiGraphicsExtractor ctx,
                                                  int mouseX,
                                                  int mouseY,
                                                  float delta,
                                                  CallbackInfo ci) {
        if (!SodiumGraphicsGuiRenderer.shouldUseModernUi()) {
            combatant$restoreNativeDonationState();
            return;
        }
        combatant$suppressDonationUi();

        // This side effect is part of Sodium's normal extractRenderState path: it owns pending
        // state, action button enablement and tooltip hover selection. Keep it before presentation.
        updateControls(mouseX, mouseY);

        if (SodiumGraphicsGuiRenderer.render(
                (VideoSettingsScreen) (Object) this,
                ctx,
                mouseX,
                mouseY,
                delta,
                pageList,
                searchWidget,
                optionList,
                applyButton,
                closeButton,
                undoButton,
                shortcutButtons,
                tooltip,
                prompt,
                hasPendingChanges
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void combatant$modernMouseClicked(MouseButtonEvent event,
                                               boolean doubleClick,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!SodiumGraphicsGuiRenderer.shouldUseModernUi()) return;
        if (SodiumGraphicsGuiRenderer.mouseClicked(
                (VideoSettingsScreen) (Object) this,
                event,
                doubleClick,
                pageList,
                searchWidget,
                optionList,
                applyButton,
                closeButton,
                undoButton,
                prompt
        )) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void combatant$modernMouseScrolled(double mouseX,
                                                double mouseY,
                                                double horizontal,
                                                double vertical,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (!SodiumGraphicsGuiRenderer.shouldUseModernUi()) return;

        // Sodium intentionally binds Ctrl+wheel to its own GUI-scale setting. Preserve that API
        // behavior; the Combatant presentation remains fixed-size regardless of the resulting
        // vanilla scale value.
        if (Minecraft.getInstance().hasControlDown()) return;

        if (SodiumGraphicsGuiRenderer.mouseScrolled(
                (VideoSettingsScreen) (Object) this,
                mouseX,
                mouseY,
                horizontal,
                vertical,
                pageList,
                optionList
        )) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void combatant$suppressDonationUi() {
        if (donateButton == null || combatant$donationSuppressed) return;
        donateButton.updateDisplay((VideoSettingsScreen) (Object) this, false);
        combatant$donationSuppressed = true;
        updateSearchWidgetWidth();
    }

    @Unique
    private void combatant$restoreNativeDonationState() {
        if (donateButton == null || !combatant$donationSuppressed) return;
        boolean visible = !SodiumClientMod.options().notifications.hasClearedDonationButton;
        donateButton.updateDisplay((VideoSettingsScreen) (Object) this, visible);
        combatant$donationSuppressed = false;
        updateSearchWidgetWidth();
    }

}
