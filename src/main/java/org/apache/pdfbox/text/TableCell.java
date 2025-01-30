/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.pdfbox.text;

import java.awt.geom.Point2D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author frank
 */
public class TableCell extends FRectangle {

    private static final Logger LOG = LogManager.getLogger(TableCell.class);

    /**
     * Wrapper to sort Rectangles according to their location on the page. They
     * are sorted according to their top edge, from top to bottom. Where two
     * rectangles have the same top edge, they are sorted according to their
     * left edge.
     *
     */
    private String text;

    public TableCell(float x0, float y0, float x1, float y1, String text) {
        super(null, null, x0, y0, x1, y1);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void clear() {
        text = "";
    }

    final public void addPoint(Point2D p1) {
        add((int) Math.round(p1.getX()), (int) Math.round(p1.getY()));
    }
}
