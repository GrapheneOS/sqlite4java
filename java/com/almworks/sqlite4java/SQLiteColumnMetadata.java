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
 *
 * <ul><li>Declared datatype ({@code String})</li>
 * <li>Collation sequence name ({@code String})</li>
 * <li>Flag that is true if NOT NULL constraint exists ({@code int})</li>
 * <li>Flag that is true if column is a part of primary key ({@code int})</li>
 * <li>Flag that is true if column is auto-increment ({@code int})</li></ul>
 *
 * You get instances of SQLiteColumnMetadata via {@link SQLiteConnection#getTableColumnMetadata} method.
 *
 * @author Alexander Smirnov
 * @see <a href="http://www.sqlite.org/c3ref/table_column_metadata.html">sqlite3_table_column_metadata</a>
 */
public final class SQLiteColumnMetadata {

  private final String myDataType; // Declared data type
  private final String myCollSeq; // Collation sequence name
  private final boolean myNotNull; // True if NOT NULL constraint exists
  private final boolean myPrimaryKey; // True if column part of PK
  private final boolean myAutoinc; // True if column is auto-increment

  SQLiteColumnMetadata(String dataType, String collSeq, boolean notNull, boolean primaryKey, boolean autoinc) {
    myDataType = dataType;
    myCollSeq = collSeq;
    myNotNull = notNull;
    myPrimaryKey = primaryKey;
    myAutoinc = autoinc;
  }

  /**
   * @return declared data type
   */
  public String getDataType() {
    return myDataType;
  }

  /**
   * @return collation sequence name
   */
  public String getCollSeq() {
    return myCollSeq;
  }

  /**
   * @return True if NOT NULL constraint exists
   */
  public boolean getNotNull() {
    return myNotNull;
  }

  /**
   * @return True if column part of primary key
   */
  public boolean getPrimaryKey() {
    return myPrimaryKey;
  }

  /**
   * @return True if column is auto-increment
   */
  public boolean getAutoinc() {
    return myAutoinc;
  }
}
