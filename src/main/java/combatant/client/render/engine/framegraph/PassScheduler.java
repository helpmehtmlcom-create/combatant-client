/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import combatant.client.render.engine.core.RenderPhase;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PassScheduler {
    private final EnumMap<RenderPhase, List<RenderPassNode>> nodes = new EnumMap<>(RenderPhase.class);

    public void add(RenderPassNode node) {
        if (node == null) return;
        nodes.computeIfAbsent(node.phase(), ignored -> new ArrayList<>()).add(node);
    }

    public List<RenderPassNode> nodes(RenderPhase phase) {
        List<RenderPassNode> list = nodes.get(phase == null ? RenderPhase.NONE : phase);
        if (list == null || list.isEmpty()) return List.of();
        return List.copyOf(list);
    }


    public List<RenderPassNode> allNodes() {
        ArrayList<RenderPassNode> result = new ArrayList<>();
        for (RenderPhase phase : RenderPhase.values()) {
            List<RenderPassNode> phaseNodes = nodes.get(phase);
            if (phaseNodes != null) result.addAll(phaseNodes);
        }
        return List.copyOf(result);
    }

    public List<RenderPassNode> enabledFrameNodes() {
        List<RenderPassNode> all = allNodes();
        if (all.isEmpty()) return List.of();
        ArrayList<RenderPassNode> enabled = new ArrayList<>(all.size());
        for (RenderPassNode node : all) if (node.enabled()) enabled.add(node);
        return List.copyOf(enabled);
    }

    /** Enable predicates are sampled once so compile and execution see the same pass set. */
    public List<RenderPassNode> enabledNodes(RenderPhase phase) {
        List<RenderPassNode> all = nodes(phase);
        if (all.isEmpty()) return List.of();
        ArrayList<RenderPassNode> enabled = new ArrayList<>(all.size());
        for (RenderPassNode node : all) if (node.enabled()) enabled.add(node);
        return List.copyOf(enabled);
    }

    public CompiledFrameGraph compile(RenderPhase phase) {
        return compile(enabledNodes(phase), null);
    }

    public CompiledFrameGraph compile(List<RenderPassNode> phaseNodes,
                                      Map<FrameGraphResourceKey, FrameGraphResourceDeclaration> declarations) {
        if (phaseNodes == null || phaseNodes.isEmpty()) return new CompiledFrameGraph(List.of(), List.of());
        ArrayList<FrameGraphPassContract> contracts = new ArrayList<>(phaseNodes.size());
        for (RenderPassNode node : phaseNodes) contracts.add(node.contract());
        return declarations == null
                ? FrameGraphContractCompiler.compile(contracts)
                : FrameGraphContractCompiler.compile(contracts, declarations);
    }

    public void clear() {
        nodes.clear();
    }
}
