/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;

/**
 *
 * @author frank
 */
public class FRectangle implements Comparable<FRectangle> {

    protected float minX, minY, maxX, maxY;
    private Color fillRgb, strokeRgb;
    private Stroke stroke;

    public FRectangle() {
        minX = -1;
        minY = -1;
        maxX = -1;
        maxY = -1;
    }

    public FRectangle(Color fillColour, Color strokeColour, Stroke stroke) {
        this();
        this.fillRgb = fillColour;
        this.strokeRgb = strokeColour;
        this.stroke = stroke;
    }

    final public void setColours(Color fillColour, Color strokeColour, Stroke stroke) {
        this.fillRgb = fillColour;
        this.strokeRgb = strokeColour;
        this.stroke = stroke;
    }

    public FRectangle(float x, float y) {
        minX = x;
        minY = y;
        maxX = x;
        maxY = y;
    }

    public FRectangle(float x0, float y0, float x1, float y1) {
        setMinMax(x0, y0, x1, y1);
    }

    public FRectangle(Rectangle2D r) {
        setMinMax((float) r.getMinX(), (float) r.getMinY(), (float) r.getMaxX(), (float) r.getMaxY());
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

    final public void setMinMax(float x0, float y0, float x1, float y1) {
        minX = x0;
        minY = y0;
        maxX = x1;
        maxY = y1;
        assert x0 <= x1 && y0 <= y1;
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

    public void setY(float min, float max) {
        assert min <= max;
        minY = min;
        maxY = max;
    }

    public void setX(float min, float max) {
        assert min <= max;
        minX = min;
        maxX = max;
    }

    public void setMin(float x, float y) {
        minX = x;
        minY = y;
        maxX = minX;
        maxY = minY;
        assert minX <= maxX && minY <= maxY;
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

    public boolean intersects(FRectangle other) {
        return overlapsX(other.minX, other.maxX) && overlapsY(other.minY, other.maxY);
    }

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

    public void rotate90() {
        float temp = maxX;
        maxX = maxY;
        maxY = temp;
    }

    public void add(float x, float y) {
        if (x < minX || minX < 0) {
            minX = x;
        }
        if (x > maxX || maxX < 0) {
            maxX = x;
        }
        if (y < minY || minY < 0) {
            minY = y;
        }
        if (y > maxY || maxY < 0) {
            maxY = y;
        }
        assert minX <= maxX && minY <= maxY;
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
        // First compare the tops of the cells... sort ascending Y values
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
        return String.format("[(%.2f, %.2f), (%.2f, %.2f)]: %08x %08x %s", minX, minY, maxX, maxY, fillRgb == null ? 0xaaaaaaaa : fillRgb.getRGB(),
                strokeRgb == null ? 0xaaaaaaaa : strokeRgb.getRGB(), stroke == null ? "null" : stroke.toString());
    }

}
