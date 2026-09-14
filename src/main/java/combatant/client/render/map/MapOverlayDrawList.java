/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.List;

/** Immutable, already projected overlay batches. */
public record MapOverlayDrawList(List<Line> lines,
                                 List<Ellipse> ellipses,
                                 List<Circle> circles,
                                 List<Player> players,
                                 List<Text> labels,
                                 MapHitIndex hitIndex) {
    public MapOverlayDrawList {
        lines = List.copyOf(lines);
        ellipses = List.copyOf(ellipses);
        circles = List.copyOf(circles);
        players = List.copyOf(players);
        labels = List.copyOf(labels);
        if (hitIndex == null) hitIndex = MapHitIndex.EMPTY;
    }

    public MapOverlayDrawList(List<Line> lines,
                              List<Ellipse> ellipses,
                              List<Circle> circles,
                              List<Text> labels,
                              MapHitIndex hitIndex) {
        this(lines, ellipses, circles, List.of(), labels, hitIndex);
    }

    public record Line(double x1, double y1, double x2, double y2, int argb, double thickness) {
    }

    public record Ellipse(String id,
                          double centerX,
                          double centerY,
                          double radiusX,
                          double radiusY,
                          double angleRadians,
                          int fillArgb,
                          int strokeArgb,
                          int priority) {
    }

    public record Circle(String id,
                         double centerX,
                         double centerY,
                         double radius,
                         int fillArgb,
                         int strokeArgb,
                         int priority) {
    }

    public record Player(String id, double x, double y, java.util.UUID playerUuid, String playerName,
                         int accentArgb, String sourceGlyph, float sizePixels, float alpha, int priority) {
    }

    public record Text(String id, double x, double y, String text, int argb, int priority) {
    }
}
