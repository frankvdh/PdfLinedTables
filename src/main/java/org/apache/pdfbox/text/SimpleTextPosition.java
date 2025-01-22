
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
 *
 * @author frank

 */
package org.apache.pdfbox.text;
import java.awt.geom.Point2D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This represents a string and a position on the screen of those characters.
 *
 * @author Ben Litchfield
 */
public final class SimpleTextPosition
{
    public static final Logger LOG = LogManager.getLogger(SimpleTextPosition.class);
    private final Point2D.Float p;
    private final float width; 
    private final float spaceWidth; // width of a space, in display units
    private final char unicode;

    /**
     * Constructor.
     *
     * @param p x,y display coordinate of the position
     * @param width The width of the given character. (in display units)
     * @param spaceWidth The width of the space character. (in display units)
     * @param unicode The Unicode character to be displayed.
      */
    public SimpleTextPosition(Point2D.Float p, float width, float spaceWidth, char unicode)
    {
        this.p = p;
        this.width = width;
        this.spaceWidth = spaceWidth *0.3f;
        this.unicode = unicode;
        LOG.trace("'{}', {}, width {}, spaceWidth {}", unicode, p, width, spaceWidth);
    }

    /**
     * Return the character stored in this object.
     *
     * @return The char on the screen.
     */
    public char getUnicode()
    {
        return unicode;
    }

    /**
     * Get the position of the char.
     *
     * @return The width of the text in display units.
     */
    public Point2D.Float getP()
    {
        return p;
    }

    /**
     * Get the X coordinate width of the position of the char.
     *
     * @return The X coordinate in display units.
     */
    public float getX()
    {
        return p.x;
    }

    /**
     * Get the Y coordinate width of the position of the char.
     *
     * @return The Y coordinate in display units.
     */
    public float getY()
    {
        return p.y;
    }

    /**
     * This will get the width of the string when page rotation adjusted coordinates are used.
     *
     * @return The width of the text in display units.
     */
    public float getWidth()
    {
        return width;
    }

    /**
     * Get the width of a space character. This is needed by the
     * text stripper, that need to know the width of a space character.
     *
     * @return The width of a space character.
     */
    public float getSpaceWidth()
    {
        return spaceWidth;
    }

    /**
     * Show the character data for this text position.
     *
     * @return A human readable form of this object.
     */
    @Override
    public String toString()
    {
        return String.format("'%c', %s, width %.2f, spaceWidth %.2f", unicode, p.toString(), width, spaceWidth);
    }
}
