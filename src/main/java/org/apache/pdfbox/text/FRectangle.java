/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.awt.geom.Point2D;

/*
 *
 * @author frank
 */

/**
 * Utility class to represent a rectangle or vertical or horizontal line.
 *
 * A horizontal line is a rectangle where minY == maxY.
 * A vertical line is a rectangle where minX == maxX.
 * minX must less tan or equal to maxX
 * minY must less tan or equal to maxY
 * 
 * Instances are sorted according to their maxY and then minX (i.e. top-left corner)
 *
 */
public class FRectangle {

    private float minX, minY, maxX, maxY;
    private Color fillRgb, strokeRgb;

    public FRectangle() {
        minX = Float.NaN;
        minY = Float.NaN;
        maxX = Float.NaN;
        maxY = Float.NaN;
    }

    public FRectangle(Color fillColour, Color strokeColour, Point2D.Float p0, Point2D.Float p1) {
        this(Math.min(p0.x, p1.x), Math.min(p0.y, p1.y), Math.max(p0.x, p1.x), Math.max(p0.y, p1.y));
        setColours(fillColour, strokeColour);
    }

    public FRectangle(Color fillColour, Color strokeColour, float x0, float y0, float x1, float y1) {
        this(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1));
        setColours(fillColour, strokeColour);
    }

    public FRectangle(FRectangle src) {
        this(src.fillRgb, src.strokeRgb, src.minX, src.minY, src.maxX, src.maxY);
    }

    final public void setColours(Color fillColour, Color strokeColour) {
        this.fillRgb = fillColour;
        this.strokeRgb = strokeColour;
    }

    public FRectangle(float x, float y) {
        this(x, y, x, y);
    }

    public FRectangle(float x0, float y0, float x1, float y1) {
        minX = x0;
        minY = y0;
        maxX = x1;
        maxY = y1;
    }

    public FRectangle(Point2D.Float p0, Point2D.Float p1) {
        minX = p0.x;
        minY = p0.y;
        maxX = p1.x;
        maxY = p1.y;
    }

    public Color getFillColour() {
        return fillRgb;
    }

    public Color getStrokeColour() {
        return strokeRgb;
    }

    public float getWidth() {
        return maxX - minX;
    }

    public float getHeight() {
        return maxY - minY;
    }

    public float getMaxX() {
        return maxX;
    }

    public float getMaxY() {
        return maxY;
    }

    public float getMinX() {
        return minX;
    }

    public float getMinY() {
        return minY;
    }

    public void setY(float y0, float y1) {
        minY = Math.min(y0, y1);
        maxY = Math.max(y0, y1);
    }

    public void setX(float x0, float x1) {
        minX = Math.min(x0, x1);
        maxX = Math.max(x0, x1);
    }

    public void setMaxY(float y) {
        minY = Math.min(y, minY);
        maxY = y;
    }

    public void setMinY(float y) {
        minY = y;
        maxY = Math.max(y, maxY);
    }

    public void setMaxX(float x) {
        maxX = x;
        minX = Math.min(x, minX);
    }

    public void setMinX(float x) {
        minX = x;
        maxX = Math.max(x, maxX);
    }

    public boolean containsX(float x) {
        return x >= minX && x <= maxX;
    }

    public boolean overlapsX(float min, float max) {
        return max >= minX && min <= maxX;
    }

    public boolean overlapsY(float min, float max) {
        return max >= minY && min <= maxY;
    }

    /** Check whether this instance intersects with another
     *      *
     * @param other
     * @return
     */
    public boolean intersects(FRectangle other) {
        return overlapsX(other.minX, other.maxX) && overlapsY(other.minY, other.maxY);
    }

    public void add(float x, float y) {
        if (x < minX || Float.isNaN(minX)) {
            setMinX(x);
        }
        if (x > maxX || Float.isNaN(maxX)) {
            setMaxX(x);
        }
        if (y < minY || Float.isNaN(minY)) {
            setMinY(y);
        }
        if (y > maxY || Float.isNaN(maxY)) {
            setMaxY(y);
        }
    }

    public void add(FRectangle r) {
        if (r.minX < minX || Float.isNaN(minX)) {
            setMinX(r.minX);
        }
        if (r.maxX > maxX || Float.isNaN(maxX)) {
            setMaxX(r.maxX);
        }
        if (r.minY < minY || Float.isNaN(minY)) {
            setMinY(r.minY);
        }
        if (r.maxY > maxY || Float.isNaN(maxY)) {
            setMaxY(r.maxY);
        }
    }
    
    public float trimX(float x) {
        if (x < minX) {
            return minX;
        }
        if (x > maxX) {
            return maxX;
        }
        return x;
    }
    
    public float trimY(float y) {
        return (y < minY) ? minY : (y > maxY) ? maxY : y;
    }

    @Override
    public String toString() {
        return String.format("[(%.2f, %.2f), (%.2f, %.2f)]: %s %s %s", minX, minY, maxX, maxY, 
                fillRgb == null ? "null" : String.format("%08x", fillRgb.getRGB()),
                strokeRgb == null ? "null" : String.format("%08x", strokeRgb.getRGB()));
    }

}
