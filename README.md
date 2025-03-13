<!---
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
--->

PDFBox Lined Table Stripper
===========================

This project is designed to make it easy to extract tabular information from
PDF files, where the table cells are delineated by vertical and horizontal
graphic lines. The specific motivation is to extract tables from the
aip.net.nz website, which come in a variety of layouts. Hearing that this
is useful to people also motivates me... please do email me at 
drifter.frank@gmail.com if you find this useful.

A table is extracted from a PDF document to an ArrayList of String arrays. Each
row of the table is contained in a String array. The graphic lines in the PDF 
document are used to allocate text within the table cells to the appropriate 
array elements.

All cells within the PDF table must be rectangular, but may span multiple rows
and/or columns of the table. Where a cell spans multiple rows or columns, 
multiple copies of the entire content of the cell is stored in the array. *All* 
the vertical line segments in the table are used to determine the width of
columns in the table, and *all* the horizontal line segments are used to 
determine the height of rows. Therefore, if the table is irregular (i.e. has 
non-rectangular cells, or rows with differing cell widths), many multiple copies
of a single cell may result.

Within each cell, the text may be distributed across multiple lines. These lines
may be replicated in the output array element, separated by newline characters,
or the text may be coalesced into a single line.

A single table may span several pages, or multiple tables may be extracted from 
a single page. A LinedTable object specifies how a table is to be recognised
and extracted.

A MultipleTables object contains one or more LinedTable objects, and allows
a single call to MultipleTable.extractTables() to efficiently extract several
tables with a single pass through each PDF page. This generates an ArrayList of
table representations, each being an ArrayList of String arrays, as above.

A table is located on a PDF page by finding filled rectangles of a specified
colour. An array of heading colours allows for different heading colours on 
different pages of the table. If there are less heading colours specified than 
pages containing the table, the last heading colour is used for all subsequent 
pages. So heading colours {Color.BLACK, Color.BLUE} says that the first page has 
a black background heading colour, and all subsequent pages have blue. The row 
of heading rectangles is sued to set the horizontal limits of the table. The 
bottom of these rectangles sets the top-most limit of the table.

The end of the table can be specified by a Regex Pattern. If not null, this is 
searched for in lines of *unformatted* text in the document, which may or may
not include spaces. Therefore any spaces in the Pattern should be represented
by "\s*". If the endTable Pattern is null or is not found in the page, filled 
rectangles, indicating the heading of the next table, are searched for, based on
the *first* heading colour value. If found, the bottom-most horizontal line
above these rectangles marks the bottom of the table. If no endTable Pattern or
next heading is found, the bottom-most horizontal line on the page is used as 
the table bottom limit. This mechanism allows for convenient handling of both 
multi-page tables and multiple tables on a single page.

Other LinedTable options:
* suppressDuplicateOverlappingText: PDFs sometimes produce boldface by 
displaying the same character, offset slightly. Enabling this will remove these
duplicate characters from the output.

* extraQuadrantRotation: Some pages are rotated by 90 degrees, apparently 
outside of the PDF document. This rotation is not included in the PDF page 
properties, and not handled by Acrobat Reader, which displays the page at
90 degrees. Setting this flag will rotate these pages to normal.

* tolerance: Tolerance in deciding whether 2 lines intersect. Setting too low a
value results in cells not being recognised, and narrow rectangles being treated
as rectangles rather than lines. A value of 2-3 is about right.

* leadingSpaces: Whether leading spaces should be added to the output to
approximate the formatting in the PDF.
 
* reduceSpaces: Whether to generate multiple spaces in the output where 2 
characters are widely spaced, or to reduce it to a single space.

* removeEmptyRows: Whether to remove empty rows (all cells contain isBlank()
string) from the output table.

* startOnNewPage: For multiple tables, indicates that this table starts on the
next page rather than below the previous table on the same page.

* lineEnding: The line ending to generate when text in a cell is on several 
lines. Using a space will unwrap the text into a single long line. Using a
newline character will allow splitting the text into multiple strings.

* mergeWrappedRows: A flag to merge the last row of cells on a page with the
first row of cells on the next page, to handle the case where a single row of
cells spans 2 pages. This is identified when the first row on the second page
has a blank string as its first value.

See the test sources for examples of LinedTable and MultipleTables definitions. 

This project is based on the [Apache PDFBox](https://pdfbox.apache.org/) library,
for which I am most grateful. It uses v3.0.3 of the PDFBox library to perform the 
low-level PDF document access, so PDFBOX must be included in the project's 
dependencies. PDFBox and the PDFBOX Lined Table Stripper are published under the 
Apache License, Version 2.0.

PDFBox is a project of the [Apache Software Foundation](https://www.apache.org/).

Test Data
---------

Because this project is designed to extract data from files downloaded from
aip.net.nz, testing uses the files listed below. However, these files are
copyright, and explicitly not allowed to be redistributed, so cannot be
included in this project. However, you can download these files for free by 
visiting https://aip.net.nz . They should be copied to the /src/test/resources/AIP
directory of this project.

    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_04_NZANR_Part_71_Parachute_Landing_Areas_PLA.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_05_NZANR_Part_71_Restricted_Areas_R.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_06_NZANR_Part_71_Visual_Reporting_Points_VRP.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_07_NZANR_Part_71_Volcanic_Hazard_Zones_VHZ.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_10_NZANR_Part_71_Danger_Areas_D.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_11_NZANR_Part_71_General_Aviation_Areas_GAA.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_12_NZANR_Part_71_Mandatory_Broadcast_Zones_MBZ.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_14_NZANR_Part_71_QNH_Zones.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/1-Permanent-Airspace/1_15_NZANR_Part_71_Transit-Lanes-T.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/3-Instrument-Flight-Procedures/3_06_NZANR_Part_95_Navaids.pdf
    https://www.aip.net.nz/assets/AIP/General-GEN/0-GEN/GEN_0.4.pdf
    https://www.aip.net.nz/assets/AIP/General-GEN/3-SERVICES/GEN_3.7.pdf
    https://www.aip.net.nz/assets/AIP/Air-Navigation-Register/5-Aerodromes/NZANR-Aerodrome_Coordinates.pdf
    https://www.aip.net.nz/assets/AIP/Aerodrome-Charts/Kaitaia-NZKT/NZKT_51.1_52.1.pdf
    https://www.aip.net.nz/assets/AIP/Aerodrome-Charts/Milford-Sound-NZMF/NZMF_51.1_52.1.pdf
    https://www.aip.net.nz/assets/AIP/Aerodrome-Charts/Martins-Bay-NZMJ/NZMJ_51.1_52.1.pdf
    https://www.aip.net.nz/assets/AIP/Aerodrome-Charts/Pudding-Hill-NZPH/NZPH_51.1_52.1.pdf

Please note that some of these files change monthly. The known good working
versions of the files are dated 28 NOV 24. It is possible that a file 
subsequently uploaded to the AIP website may no longer be readable by the
PDF Lined Table Stripper. I will endeavour to update this project to always be
able to read these (and many other) files from the AIP site. If you find any 
tests failing, please email me at drifter.frank@gmail.com and I'll work on an
update.

Binary Downloads
----------------

You can download binary versions of PDFBOX for releases currently under 
development or older releases from their [Download Page](https://pdfbox.apache.org/download.cgi).

Build
-----

You need JDK 21 (or higher) and [Maven 3](https://maven.apache.org/) to
build this project. This is a later version than the PDFBOX project requires.

Support
-------

This project is not supported by PDFBOX. For support, send an email to
drifter.frank@gmail.com.

Known Limitations and Problems
------------------------------

I have no mechanism currently for tracking issues and requests relating to this
project.

See the PDFBOX [Issue Tracker](https://issues.apache.org/jira/browse/PDFBOX) for
known issues and requested features for the PDFBOX project itself. 

License (see also [LICENSE.txt](https://github.com/apache/pdfbox/blob/trunk/LICENSE.txt))
------------------------------

Collective work: Copyright 2015 The Apache Software Foundation.

Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Export control
--------------

The PDFBOX distribution includes cryptographic software. See the PDFBOX project
for more details.
