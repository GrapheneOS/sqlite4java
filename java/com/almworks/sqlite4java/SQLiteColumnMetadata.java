/*
 * Copyright 2010 ALM Works Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.almworks.sqlite4java;

/**
 * SQLiteColumnMetadata wraps table column metadata represented by: 
 * * Declared datatype (<strong><code>String</code></strong>)
 * * Collation sequence name (<strong><code>String</code></strong>)
 * * Flag that is true if NOT NULL constraint exists (<strong><code>int</code></strong>)
 * * Flag that is true if column is a part of primary key (<strong><code>int</code></strong>)
 * * Flag that is true if column is auto-increment (<strong><code>int</code></strong>)
 * <p/>
 * You get instances of SQLiteColumnMetadata via {@link SQLiteConnection#getTableColumnMetadata} method.
 *
 * @author Alexander Smirnov
 * @see <a href="http://www.sqlite.org/c3ref/table_column_metadata.html">sqlite3_table_column_metadata</a>
 */
public final class SQLiteColumnMetadata {
  private String dataType; // Declared data type
  private String collSeq; // Collation sequence name
  private int notNull; // True if NOT NULL constraint exists
  private int primaryKey; // True if column part of PK
  private int autoinc; // True if column is auto-increment
  public SQLiteColumnMetadata(String dataType, String collSeq, int notNull, int primaryKey, int autoinc) {
    this.dataType = dataType;
    this.collSeq = collSeq;
    this.notNull = notNull;
    this.primaryKey = primaryKey;
    this.autoinc = autoinc;
  }
  public String getDataType() { return dataType; }
  public String getCollSeq() { return collSeq; }
  public int getNotNull() { return notNull; }
  public int getPrimaryKey() { return primaryKey; }
  public int getAutoinc() { return autoinc; }
}
