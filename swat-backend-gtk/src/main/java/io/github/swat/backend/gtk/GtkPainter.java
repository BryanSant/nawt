package io.github.swat.backend.gtk;

import io.github.swat.Painter;

import java.lang.foreign.MemorySegment;

final class GtkPainter implements Painter {

    private final MemorySegment cr;

    GtkPainter(MemorySegment cr) { this.cr = cr; }

    @Override public Painter color(double r, double g, double b, double a) {
        Cairo.setSourceRGBA(cr, r, g, b, a);
        return this;
    }

    @Override public Painter fillRect(double x, double y, double w, double h) {
        Cairo.rectangle(cr, x, y, w, h);
        Cairo.fill(cr);
        return this;
    }

    @Override public Painter strokeRect(double x, double y, double w, double h) {
        Cairo.rectangle(cr, x, y, w, h);
        Cairo.stroke(cr);
        return this;
    }

    @Override public Painter line(double x1, double y1, double x2, double y2) {
        Cairo.newPath(cr);
        Cairo.moveTo(cr, x1, y1);
        Cairo.lineTo(cr, x2, y2);
        Cairo.stroke(cr);
        return this;
    }
}
