/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

/** Backend-neutral physical requirements for one logical graph resource. */
public interface FrameGraphPhysicalResourceDescriptor {
    FrameGraphResourceKind kind();

    long approximateByteSize();

    boolean supports(FrameGraphAccess access);

    /** Physical aliasing is allowed only when this contract is exactly compatible. */
    boolean aliasCompatible(FrameGraphPhysicalResourceDescriptor other);

    String debugDescription();
}
