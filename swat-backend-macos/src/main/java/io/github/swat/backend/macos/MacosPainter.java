package io.github.swat.backend.macos;

import io.github.swat.Painter;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** {@link Painter} backed by a CGContextRef. Valid only for the duration of one drawRect: call. */
final class MacosPainter implements Painter {

    private final MemorySegment ctx;
    private double r, g, b, a = 1.0;

    MacosPainter(MemorySegment ctx) {
        this.ctx = ctx;
    }

    @Override public Painter color(double r, double g, double b, double a) {
        this.r = r; this.g = g; this.b = b; this.a = a;
        CG.setFillRGBA(ctx, r, g, b, a);
        CG.setStrokeRGBA(ctx, r, g, b, a);
        return this;
    }

    @Override public Painter fillRect(double x, double y, double w, double h) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment rect = arena.allocate(CG.CGRECT);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, x);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, y);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, w);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, h);
            CG.fillRect(ctx, rect);
        }
        return this;
    }

    @Override public Painter strokeRect(double x, double y, double w, double h) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment rect = arena.allocate(CG.CGRECT);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, x);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, y);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, w);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, h);
            CG.strokeRect(ctx, rect);
        }
        return this;
    }

    @Override public Painter line(double x1, double y1, double x2, double y2) {
        CG.beginPath(ctx);
        CG.moveTo(ctx, x1, y1);
        CG.addLineTo(ctx, x2, y2);
        CG.strokePath(ctx);
        return this;
    }
}
