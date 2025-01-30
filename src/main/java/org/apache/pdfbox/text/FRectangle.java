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
 * minX must less than or equal to maxX
 * minY must less than or equal to maxY
 *
 * @author @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 */
 public class FRectangle {

    private float minX, minY, maxX, maxY;
    private Color fillRgb, strokeRgb;

    /** Basic constructor
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     */
    public FRectangle(float x0, float y0, float x1, float y1) {
        minX = x0;
        minY = y0;
        maxX = x1;
        maxY = y1;
    }

    /** Full constructor
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     */
    public FRectangle(Color fillColour, Color strokeColour, float x0, float y0, float x1, float y1) {
        this(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1));
        this.fillRgb = fillColour;
        this.strokeRgb = strokeColour;
    }

    /** Construct a copy
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     */
    public FRectangle(FRectangle src) {
        this(src.fillRgb, src.strokeRgb, src.minX, src.minY, src.maxX, src.maxY);
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
        return (x < minX) ? minX : (x > maxX) ? maxX : x;
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
