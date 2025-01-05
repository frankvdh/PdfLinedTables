/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.awt.Stroke;
import java.awt.geom.Point2D;

/**
 *
 * @author frank
 */

public class TableCell extends FRectangle {

    /**
     * Wrapper to sort Rectangles according to their location on the page. They
     * are sorted according to their top edge, from top to bottom. Where two
     * rectangles have the same top edge, they are sorted according to their
     * left edge.
     *
     */
    private String text;

    public TableCell(float x, float y) {
        super(x, y);
    }

    public TableCell(float x0, float y0, float x1, float y1) {
        super(Color.WHITE, Color.BLACK, null, x0, y0, x1, y1);
    }

    public TableCell(Color fillColour, Color strokeColour, Stroke stroke, float x0, float y0, float x1, float y1) {
        super(fillColour, strokeColour, stroke, x0, y0, x1, y1);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void addText(String text) {
        this.text += text;
    }

    public void clear() {
        text = "";
    }

    final public void addPoint(Point2D p1) {
        add((int) Math.round(p1.getX()), (int) Math.round(p1.getY()));
    }
}
