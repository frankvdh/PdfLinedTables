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

/**
 * Wrapper class to associate a string of text with an area on the page.
 *
 * @author @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 */
public class TableCell extends FRectangle {

    private String text;

    /**
     * Basic constructor
     *
     * @param x0
     * @param y0
     * @param x1
     * @param y1
     * @param text
     */
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
    
    @Override
    public String toString() {
        return String.format("[(%.2f, %.2f), (%.2f, %.2f)]: %s", getMinX(), getMinY(), getMaxX(), getMaxY(), text);
    }

}
