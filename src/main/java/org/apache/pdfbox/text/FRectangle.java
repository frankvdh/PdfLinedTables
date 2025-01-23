/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.awt.Stroke;
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
public class FRectangle implements Comparable<FRectangle> {

    private float minX, minY, maxX, maxY;
    private Color fillRgb, strokeRgb;
    private Stroke stroke;

    public FRectangle() {
        minX = Float.NaN;
        minY = Float.NaN;
        maxX = Float.NaN;
        maxY = Float.NaN;
    }

    public FRectangle(Color fillColour, Color strokeColour, Stroke stroke) {
        this();
        this.fillRgb = fillColour;
        this.strokeRgb = strokeColour;
        this.stroke = stroke;
    }

    public FRectangle(Color fillColour, Color strokeColour, Stroke stroke, Point2D.Float p0, Point2D.Float p1) {
        this(Math.min(p0.x, p1.x), Math.min(p0.y, p1.y), Math.max(p0.x, p1.x), Math.max(p0.y, p1.y));
        setColours(fillColour, strokeColour, stroke);
    }

    public FRectangle(Color fillColour, Color strokeColour, Stroke stroke, float x0, float y0, float x1, float y1) {
        this(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1));
        setColours(fillColour, strokeColour, stroke);
    }

    public FRectangle(FRectangle src) {
        this(src.fillRgb, src.strokeRgb, src.stroke, src.minX, src.minY, src.maxX, src.maxY);
    }

    final public void setColours(Color fillColour, Color strokeColour, Stroke stroke) {
        this.fillRgb = fillColour;
        this.strokeRgb = strokeColour;
        this.stroke = stroke;
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

    public Stroke getStroke() {
        return stroke;
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

    public void setMin(float x, float y) {
        minX = x;
        minY = y;
        maxX = minX;
        maxY = minY;
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

    public boolean containsY(float y) {
        return y >= minY && y <= maxY;
    }

    public boolean overlapsY(float min, float max) {
        return max >= minY && min <= maxY;
    }

    public boolean contains(float x, float y) {
        return containsX(x) && containsY(y);
    }

    /** Check whether this instance intersects with another
     *      *
     * @param other
     * @return
     */
    public boolean intersects(FRectangle other) {
        return overlapsX(other.minX, other.maxX) && overlapsY(other.minY, other.maxY);
    }

    /** Intersects this instance with another instances
     *
     * @param other
     */
    public void intersect(FRectangle other) {
        if (other.minX > minX) {
            minX = other.minX;
        }
        if (other.minY > minY) {
            minY = other.minY;
        }
        if (other.maxX < maxX) {
            maxX = other.maxX;
        }
        if (other.maxY < maxY) {
            maxY = other.maxY;
        }
        if (minX > maxX) {
            maxX = minX;
        }
        if (minY > maxY) {
            maxY = minY;
        }
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

   final public FRectangle expand(float x) {
        minX -= x;
        minY -= x;
        maxX += x;
        maxY += x;
        return this;
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
   
    public void trimX(float min, float max) {
        if (minX < min) {
            minX = min;
        }
        if (maxX > max) {
            maxX = max;
        }
        if (minX > maxX) {
            maxX = minX;
        }
    }

    public void trimY(float min, float max) {
        if (minY < min) {
            minY = min;
        }
        if (maxY > max) {
            maxY = max;
        }
        if (minY > maxY) {
            maxY = minY;
        }
    }

    @Override
    public int compareTo(FRectangle other) {
        if (this == other) {
            return 0;
        }
        // First compare the tops of the cells... sort ascending
        if (this.minY > other.minY) {
            return 1;
        }
        if (this.minY < other.minY) {
            return -1;
        }
        // Same top, compare left edges, sort ascending
        if (this.minX > other.minX) {
            return 1;
        }
        if (this.minX < other.minX) {
            return -1;
        }
        return 0;
    }

    @Override
    public String toString() {
        return String.format("[(%.2f, %.2f), (%.2f, %.2f)]: %s %s %s", minX, minY, maxX, maxY, 
                fillRgb == null ? "null" : String.format("%08x", fillRgb.getRGB()),
                strokeRgb == null ? "null" : String.format("%08x", strokeRgb.getRGB()), 
                stroke == null ? "null" : stroke.toString());
    }

}
