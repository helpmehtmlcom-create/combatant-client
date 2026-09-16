/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.RenderPhase;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A named frame-graph node with an explicit resource contract and enable predicate. */
public final class RenderPassNode {
    private final FrameGraphPassContract contract;
    private final BooleanSupplier enabled;
    private final Consumer<RenderFrameContext> renderer;

    public RenderPassNode(RenderPhase phase, String label, Consumer<RenderFrameContext> renderer) {
        this(FrameGraphPassContract.sideEffect(
                phase,
                label == null || label.isBlank()
                        ? (phase == null ? RenderPhase.NONE : phase).name().toLowerCase()
                        : label), () -> true, renderer);
    }

    public RenderPassNode(FrameGraphPassContract contract, Consumer<RenderFrameContext> renderer) {
        this(contract, () -> true, renderer);
    }

    public RenderPassNode(FrameGraphPassContract contract, BooleanSupplier enabled, Consumer<RenderFrameContext> renderer) {
        this.contract = Objects.requireNonNull(contract, "contract");
        this.enabled = enabled == null ? () -> true : enabled;
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public RenderPhase phase() {
        return contract.phase();
    }

    public String label() {
        return contract.label();
    }

    public FrameGraphPassContract contract() {
        return contract;
    }

    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    public void execute(RenderFrameContext context) {
        renderer.accept(context);
    }
}
