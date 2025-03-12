/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.text;

import java.awt.Color;

/**
 * Utility class to represent a filled and/or stroked rectangle.
 *
 * minX must less than or equal to maxX minY must less than or equal to maxY
 *
 * @author @author <a href="mailto:drifter.frank@gmail.com">Frank van der
 * Hulst</a>
 */
public class FRectangle {

    private float minX, minY, maxX, maxY;
    private Color fillRgb, strokeRgb;

    /**
     * Basic constructor
     *
     * @param x0 X coordinate of left edge
     * @param y0 Y coordinate of bottom edge
     * @param x1 X coordinate of right edge
     * @param y1 Y coordinate of top edge
     */
    public FRectangle(float x0, float y0, float x1, float y1) {
        minX = x0;
        minY = y0;
        maxX = x1;
        maxY = y1;
    }

    /**
     * Full constructor
     *
     * @param fillColour Colour of background
     * @param strokeColour Colour of outline
     * @param x0 X coordinate of left edge
     * @param y0 Y coordinate of bottom edge
     * @param x1 X coordinate of right edge
     * @param y1 Y coordinate of top edge
     */
    public FRectangle(Color fillColour, Color strokeColour, float x0, float y0, float x1, float y1) {
        this(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1));
        this.fillRgb = fillColour;
        this.strokeRgb = strokeColour;
    }

    /**
     * Construct a copy
     *
     * @param src Object to copy from
     */
    public FRectangle(FRectangle src) {
        this(src.fillRgb, src.strokeRgb, src.minX, src.minY, src.maxX, src.maxY);
    }

    /**
     * Get fill colour
     *
     * @return colour
     */
    public Color getFillColour() {
        return fillRgb;
    }

    /**
     * Get outline colour
     *
     * @return colour
     */
    public Color getStrokeColour() {
        return strokeRgb;
    }

    /**
     * Get width of rectangle
     *
     * @return width
     */
    public float getWidth() {
        return maxX - minX;
    }

    /**
     * Get height of rectangle
     *
     * @return height
     */
    public float getHeight() {
        return maxY - minY;
    }

    /**
     * Get right edge of rectangle
     *
     * @return right edge
     */
    public float getMaxX() {
        return maxX;
    }

    /**
     * Get top edge of rectangle
     *
     * @return top edge
     */
    public float getMaxY() {
        return maxY;
    }

    /**
     * Get left edge
     *
     * @return left edge
     */
    public float getMinX() {
        return minX;
    }

    /**
     * Get bottom edge
     *
     * @return bottom edge
     */
    public float getMinY() {
        return minY;
    }

    /**
     * Set X edges of rectangle
     *
     * @param x0 one edge
     * @param x1 the other edge
     */
    public void setX(float x0, float x1) {
        minX = Math.min(x0, x1);
        maxX = Math.max(x0, x1);
    }

    /**
     * Set top edge of rectangle. If less than current bottom edge, bottom edge
     * is also set to the same as the top edge
     *
     * @param y new value
     */
    public void setMaxY(float y) {
        minY = Math.min(y, minY);
        maxY = y;
    }

    /**
     * Set bottom edge of rectangle. If greater than current top edge, top edge
     * is also set to the same as the bottom edge
     *
     * @param y new value
     */
    public void setMinY(float y) {
        minY = y;
        maxY = Math.max(y, maxY);
    }

    /**
     * Set right edge of rectangle. If less than current left edge, left edge is
     * also set to the same as the right edge
     *
     * @param x new value
     */
    public void setMaxX(float x) {
        maxX = x;
        minX = Math.min(x, minX);
    }

    /**
     * Set left edge of rectangle. If greater than current right edge, right
     * edge is also set to the same as the left edge
     *
     * @param x new value
     */
    public void setMinX(float x) {
        minX = x;
        maxX = Math.max(x, maxX);
    }

    /**
     * Does the X range of the rectangle contain the given value?
     *
     * @param x value to check
     * @return true if contained within the X range
     */
    public boolean containsX(float x) {
        return x >= minX && x <= maxX;
    }

    /**
     * Does the given range of values overlap with the X range of the rectangle
     *
     * @param min Left end of test range
     * @param max Right end of test range
     * @return true if the ranges overlap
     */
    public boolean overlapsX(float min, float max) {
        return max >= minX && min <= maxX;
    }

    /**
     * Does the given range of values overlap with the Y range of the rectangle
     *
     * @param min bottom end of test range
     * @param max top end of test range
     * @return true if the ranges overlap
     */
    public boolean overlapsY(float min, float max) {
        return max >= minY && min <= maxY;
    }

    /**
     * Check whether this instance intersects with another
     *
     * @param other The other rectangle
     * @return true if the two rectangles intersect
     */
    public boolean intersects(FRectangle other) {
        return overlapsX(other.minX, other.maxX) && overlapsY(other.minY, other.maxY);
    }

    /**
     * Add this point to the rectangle, extending the appropriate edges to include it
     *
     * @param x X coordinate of point
     * @param y Y coordinate of point
     */
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

    /**
     * Add the given rectangle to this rectangle, extending the appropriate edges to include it
     *
     * @param r The rectangle to add
     */
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

    /**
     * Trim the x value to the X range of the rectangle
     *
     * @param x The value to trim
     * @return a value between the left and right edges of the rectangle
     */
    public float trimX(float x) {
        return (x < minX) ? minX : (x > maxX) ? maxX : x;
    }

   /**
     * Trim the y value to the Y range of the rectangle
     *
     * @param y The value to trim
     * @return a value between the bottom and top edges of the rectangle
     */
    public float trimY(float y) {
        return (y < minY) ? minY : (y > maxY) ? maxY : y;
    }

    /**
     * String representation of this rectangle
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return String.format("[(%.2f, %.2f), (%.2f, %.2f)]: %s %s", minX, minY, maxX, maxY,
                fillRgb == null ? "null" : String.format("%08x", fillRgb.getRGB()),
                strokeRgb == null ? "null" : String.format("%08x", strokeRgb.getRGB()));
    }
}
