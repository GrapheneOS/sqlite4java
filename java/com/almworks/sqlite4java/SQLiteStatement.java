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

import javolution.util.FastTable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import static com.almworks.sqlite4java.SQLiteConstants.*;

/**
 * This class encapsulates sqlite statement. It is linked to the opening connection through controller, and confined to
 * the same thread.
 * <p/>
 * Typical usage:
 * <pre>
 * SQLiteStatement statement = connection.prepare(".....");
 * try {
 *   statement.bind(....);
 *   while (statement.step()) {
 *      statement.columnXXX(...);
 *   }
 * } finally {
 *   statement.dispose();
 * }
 * </pre>
 */
public final class SQLiteStatement {
  public static final SQLiteStatement DISPOSED = new SQLiteStatement();

  private static final int COLUMN_COUNT_UNKNOWN = -1;

  /**
   * The SQL of this statement.
   */
  private final SQLParts mySqlParts;

  /**
   * The controller that handles connection-level operations. Initially it is set
   */
  private SQLiteController myController;

  /**
   * Statement handle wrapper. Becomes null when disposed.
   */
  private SWIGTYPE_p_sqlite3_stmt myHandle;

  /**
   * When true, the last step() returned SQLITE_ROW, which means data can be read.
   */
  private boolean myHasRow;

  /**
   * When true, values have been bound to the statement. (and they take up memory)
   */
  private boolean myHasBindings;

  /**
   * When true, the statement has performed step() and needs to be reset to be reused.
   */
  private boolean myStepped;

  /**
   * The number of columns in current result set. When set to COLUMN_COUNT_UNKNOWN, the number of columns should
   * be requested at first need.
   */
  private int myColumnCount;

  /**
   * All currently active bind streams.
   */
  private FastTable<BindStream> myBindStreams;
  private FastTable<ColumnStream> myColumnStreams;

  /**
   * Contains progress handler instance - only when step() is in progress. Used to cancel the execution.
   * Protected for MT access with this.
   */
  private ProgressHandler myProgressHandler;

  /**
   * True if statement has been cancelled. Cleared at statement reset.
   */
  private boolean myCancelled;

  /**
   * Instances are constructed only by SQLiteConnection.
   *
   * @see SQLiteConnection#prepare(String, boolean)
   */
  SQLiteStatement(SQLiteController controller, SWIGTYPE_p_sqlite3_stmt handle, SQLParts sqlParts) {
    assert handle != null;
    assert sqlParts.isFixed() : sqlParts;
    myController = controller;
    myHandle = handle;
    mySqlParts = sqlParts;
    Internal.logFine(this, "instantiated");
  }

  /**
   * Constructs an empty disposed statement
   */
  private SQLiteStatement() {
    myController = SQLiteController.getDisposed(null);
    myHandle = null;
    mySqlParts = new SQLParts().fix();
  }

  /**
   * @return true if the statement is disposed and cannot be used.
   */
  public boolean isDisposed() {
    return myHandle == null;
  }

  /**
   * @return sql parts for this statement (immutable)
   */
  public SQLParts getSqlParts() {
    return mySqlParts;
  }

  /**
   * Disposes this statement and frees allocated resources. If statement handle is cached,
   * it is returned to connection's cache.
   * <p/>
   * After statement is disposed, it is no longer usable and holds no references to connection
   * or sqlite db.
   */
  public void dispose() {
    if (myHandle == null)
      return;
    try {
      myController.validate();
    } catch (SQLiteException e) {
      Internal.recoverableError(this, "invalid dispose: " + e, true);
      return;
    }
    Internal.logFine(this, "disposing");
    myController.dispose(this);
    // clear may be called from dispose() too
    clear();
  }

  /**
   * Resets statement and clears bindings.
   *
   * @see #reset(boolean)
   */
  public SQLiteStatement reset() throws SQLiteException {
    return reset(true);
  }

  /**
   * Resets statement (see sqlite3_reset API docs), if the statement has been stepped.
   * <p/>
   * Optionally, clears bindings if they have been called.
   *
   * @see <a href="http://www.sqlite.org/c3ref/reset.html">sqlite3_reset</a>
   */
  public SQLiteStatement reset(boolean clearBindings) throws SQLiteException {
    myController.validate();
    boolean fineLogging = Internal.isFineLogging();
    if (fineLogging)
      Internal.logFine(this, "reset(" + clearBindings + ")");
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    clearColumnStreams();
    if (myStepped) {
      if (fineLogging)
        Internal.logFine(this, "resetting");
      int rc = _SQLiteSwigged.sqlite3_reset(handle);
      myController.throwResult(rc, "reset()", this);
    }
    myHasRow = false;
    myColumnCount = 0;
    myStepped = false;
    if (clearBindings && myHasBindings) {
      if (fineLogging)
        Internal.logFine(this, "clearing bindings");
      int rc = _SQLiteSwigged.sqlite3_clear_bindings(handle);
      myController.throwResult(rc, "reset.clearBindings()", this);
      clearBindStreams(false);
      myHasBindings = false;
    }
    synchronized (this) {
      myCancelled = false;
    }
    return this;
  }

  /**
   * Clears bindings if there are any.
   *
   * @see <a href="http://www.sqlite.org/c3ref/clear_bindings.html">sqlite3_clear_bindings</a>
   */
  public SQLiteStatement clearBindings() throws SQLiteException {
    myController.validate();
    Internal.logFine(this, "clearBindings");
    if (myHasBindings) {
      Internal.logFine(this, "clearing bindings");
      int rc = _SQLiteSwigged.sqlite3_clear_bindings(handle());
      myController.throwResult(rc, "clearBindings()", this);
      clearBindStreams(false);
    }
    myHasBindings = false;
    return this;
  }

  /**
   * Steps through statement.
   *
   * @return true if there is data (SQLITE_ROW) was returned, false if statement has been completed (SQLITE_DONE)
   * @throws SQLiteException if result code from sqlite3_step was neither SQLITE_ROW nor SQLITE_DONE
   * @see <a href="http://www.sqlite.org/c3ref/step.html">sqlite3_step</a>
   */
  public boolean step() throws SQLiteException {
    myController.validate();
    if (Internal.isFineLogging())
      Internal.logFine(this, "step");
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    clearBindStreams(true);
    clearColumnStreams();
    int rc;
    ProgressHandler ph = myController.getProgressHandler();
    ph.reset();
    synchronized (this) {
      if (myCancelled)
        throw new SQLiteCancelledException();
      myProgressHandler = ph;
    }
    try {
      rc = _SQLiteSwigged.sqlite3_step(handle);
    } finally {
      synchronized (this) {
        myProgressHandler = null;
      }
      if (Internal.isFineLogging())
        Internal.logFine(this, "step " + ph.getSteps() + " steps");
      ph.reset();
    }
    myStepped = true;
    if (rc == Result.SQLITE_ROW) {
      Internal.logFine(this, "step ROW");
      if (!myHasRow) {
        // at first row, set column count to COLUMN_COUNT_UNKNOWN so it will be requested at first need
        myColumnCount = COLUMN_COUNT_UNKNOWN;
      }
      myHasRow = true;
    } else if (rc == Result.SQLITE_DONE) {
      Internal.logFine(this, "step DONE");
      myColumnCount = 0;
      myHasRow = false;
    } else {
      myController.throwResult(rc, "step()", this);
    }
    return myHasRow;
  }

  public SQLiteStatement stepThrough() throws SQLiteException {
    while (step()) ;
    return this;
  }

  public int loadInts(int column, int[] buffer, int offset, int count) throws SQLiteException {
    myController.validate();
    if (buffer == null || count <= 0 || offset < 0 || offset + count > buffer.length) {
      assert false;
      return 0;
    }
    if (Internal.isFineLogging())
      Internal.logFine(this, "loadInts(" + column + "," + offset + "," + count + ")");
    if (myStepped && !myHasRow)
      return 0;
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    clearBindStreams(true);
    clearColumnStreams();
    int r;
    int rc;
    ProgressHandler ph = myController.getProgressHandler();
    ph.reset();
    synchronized (this) {
      if (myCancelled)
        throw new SQLiteCancelledException();
      myProgressHandler = ph;
    }
    try {
      _SQLiteManual manual = myController.getSQLiteManual();
      r = manual.wrapper_load_ints(handle, column, buffer, offset, count);
      rc = manual.getLastReturnCode();
    } finally {
      synchronized (this) {
        myProgressHandler = null;
      }
      ph.reset();
    }
    myStepped = true;
    if (rc == Result.SQLITE_ROW) {
      if (!myHasRow) {
        myColumnCount = COLUMN_COUNT_UNKNOWN;
      }
      myHasRow = true;
    } else if (rc == Result.SQLITE_DONE) {
      myColumnCount = 0;
      myHasRow = false;
    } else {
      myController.throwResult(rc, "loadInts()", this);
    }
    return r;
  }

  public void cancel() {
    ProgressHandler handler;
    synchronized (this) {
      myCancelled = true;
      handler = myProgressHandler;
    }
    if (handler != null) {
      handler.cancel();
    }
  }

  /**
   * @return true if last call to {@link #step} has returned "true"
   */
  public boolean hasRow() {
    return myHasRow;
  }

  /**
   * @return true if there were bindings to statement's variables
   */
  public boolean hasBindings() {
    return myHasBindings;
  }

  /**
   * @return true if statement has been stepped at least once, so reset is needed
   */
  public boolean hasStepped() {
    return myStepped;
  }

  public int getBindParameterCount() throws SQLiteException {
    myController.validate();
    return _SQLiteSwigged.sqlite3_bind_parameter_count(handle());
  }

  public String getBindParameterName(int index) throws SQLiteException {
    myController.validate();
    return _SQLiteSwigged.sqlite3_bind_parameter_name(handle(), index);
  }

  public int getBindParameterIndex(String name) throws SQLiteException {
    myController.validate();
    return _SQLiteSwigged.sqlite3_bind_parameter_index(handle(), name);
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/bind_blob.html">sqlite3_bind_double</a>
   */
  public SQLiteStatement bind(int index, double value) throws SQLiteException {
    myController.validate();
    if (Internal.isFineLogging())
      Internal.logFine(this, "bind(" + index + "," + value + ")");
    int rc = _SQLiteSwigged.sqlite3_bind_double(handle(), index, value);
    myController.throwResult(rc, "bind(double)", this);
    myHasBindings = true;
    return this;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/bind_blob.html">sqlite3_bind_int</a>
   */
  public SQLiteStatement bind(int index, int value) throws SQLiteException {
    myController.validate();
    if (Internal.isFineLogging())
      Internal.logFine(this, "bind(" + index + "," + value + ")");
    int rc = _SQLiteSwigged.sqlite3_bind_int(handle(), index, value);
    myController.throwResult(rc, "bind(int)", this);
    myHasBindings = true;
    return this;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/bind_blob.html">sqlite3_bind_int64</a>
   */
  public SQLiteStatement bind(int index, long value) throws SQLiteException {
    myController.validate();
    if (Internal.isFineLogging())
      Internal.logFine(this, "bind(" + index + "," + value + ")");
    int rc = _SQLiteSwigged.sqlite3_bind_int64(handle(), index, value);
    myController.throwResult(rc, "bind(long)", this);
    myHasBindings = true;
    return this;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/bind_blob.html">sqlite3_bind_blob</a>
   */

  public SQLiteStatement bind(int index, byte[] value) throws SQLiteException {
    return value == null ? bindNull(index) : bind(index, value, 0, value.length);
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/bind_blob.html">sqlite3_bind_text16</a>
   */
  public SQLiteStatement bind(int index, String value) throws SQLiteException {
    if (value == null) {
      Internal.logFine(this, "bind(null string)");
      return bindNull(index);
    }
    myController.validate();
    if (Internal.isFineLogging()) {
      if (value.length() <= 20)
        Internal.logFine(this, "bind(" + index + "," + value + ")");
      else
        Internal.logFine(this, "bind(" + index + "," + value.substring(0, 20) + "....)");
    }
    int rc = _SQLiteManual.sqlite3_bind_text(handle(), index, value);
    myController.throwResult(rc, "bind(String)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bind(int index, byte[] value, int offset, int length) throws SQLiteException {
    if (value == null) {
      Internal.logFine(this, "bind(null blob)");
      return bindNull(index);
    }
    myController.validate();
    if (Internal.isFineLogging()) {
      Internal.logFine(this, "bind(" + index + ",[" + length + "])");
    }
    int rc = _SQLiteManual.sqlite3_bind_blob(handle(), index, value, offset, length);
    myController.throwResult(rc, "bind(blob)", this);
    myHasBindings = true;
    return this;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/bind_blob.html">sqlite3_bind_blob</a>
   */
  public SQLiteStatement bindZeroBlob(int index, int length) throws SQLiteException {
    if (length < 0) {
      Internal.logFine(this, "bind(null zeroblob)");
      return bindNull(index);
    }
    myController.validate();
    if (Internal.isFineLogging()) {
      Internal.logFine(this, "bindZeroBlob(" + index + "," + length + ")");
    }
    int rc = _SQLiteSwigged.sqlite3_bind_zeroblob(handle(), index, length);
    myController.throwResult(rc, "bindZeroBlob()", this);
    myHasBindings = true;
    return this;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/bind_blob.html">sqlite3_bind_null</a>
   */
  public SQLiteStatement bindNull(int index) throws SQLiteException {
    myController.validate();
    if (Internal.isFineLogging())
      Internal.logFine(this, "bind_null(" + index + ")");
    int rc = _SQLiteSwigged.sqlite3_bind_null(handle(), index);
    myController.throwResult(rc, "bind(null)", this);
    // specifically does not set myHasBindings to true
    return this;
  }

  public OutputStream bindStream(int index) throws SQLiteException {
    return bindStream(index, 0);
  }

  public OutputStream bindStream(int index, int minimumSize) throws SQLiteException {
    myController.validate();
    if (Internal.isFineLogging())
      Internal.logFine(this, "bindStream(" + index + "," + minimumSize + ")");
    try {
      DirectBuffer buffer = myController.allocateBuffer(minimumSize);
      BindStream out = new BindStream(index, buffer);
      FastTable<BindStream> list = myBindStreams;
      if (list == null) {
        myBindStreams = list = new FastTable<BindStream>(1);
      }
      myBindStreams.add(out);
      myHasBindings = true;
      return out;
    } catch (IOException e) {
      throw new SQLiteException(SQLiteConstants.Wrapper.WRAPPER_WEIRD, "cannot allocate buffer", e);
    }
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/column_blob.html">sqlite3_column_text16</a>
   */
  public String columnString(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnString(" + column + ")");
    _SQLiteManual sqlite = myController.getSQLiteManual();
    String result = sqlite.sqlite3_column_text(handle, column);
    myController.throwResult(sqlite.getLastReturnCode(), "columnString()", this);
    if (Internal.isFineLogging()) {
      if (result == null) {
        Internal.logFine(this, "columnString(" + column + ") is null");
      } else if (result.length() <= 20) {
        Internal.logFine(this, "columnString(" + column + ")=" + result);
      } else {
        Internal.logFine(this, "columnString(" + column + ")=" + result.substring(0, 20) + "....");
      }
    }
    return result;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/column_blob.html">sqlite3_column_int</a>
   */
  public int columnInt(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnInt(" + column + ")");
    int r = _SQLiteSwigged.sqlite3_column_int(handle, column);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnInt(" + column + ")=" + r);
    return r;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/column_blob.html">sqlite3_column_double</a>
   */
  public double columnDouble(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnDouble(" + column + ")");
    double r = _SQLiteSwigged.sqlite3_column_double(handle, column);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnDouble(" + column + ")=" + r);
    return r;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/column_blob.html">sqlite3_column_int64</a>
   */
  public long columnLong(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnLong(" + column + ")");
    long r = _SQLiteSwigged.sqlite3_column_int64(handle, column);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnLong(" + column + ")=" + r);
    return r;
  }

  public byte[] columnBlob(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnBytes(" + column + ")");
    _SQLiteManual sqlite = myController.getSQLiteManual();
    byte[] r = sqlite.sqlite3_column_blob(handle, column);
    myController.throwResult(sqlite.getLastReturnCode(), "columnBytes", this);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnBytes(" + column + ")=[" + (r == null ? "null" : r.length) + "]");
    return r;
  }

  public InputStream columnStream(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnStream(" + column + ")");
    _SQLiteManual sqlite = myController.getSQLiteManual();
    ByteBuffer buffer = sqlite.wrapper_column_buffer(handle, column);
    myController.throwResult(sqlite.getLastReturnCode(), "columnStream", this);
    if (buffer == null)
      return null;
    ColumnStream in = new ColumnStream(buffer);
    FastTable<ColumnStream> table = myColumnStreams;
    if (table == null)
      myColumnStreams = table = new FastTable<ColumnStream>(1);
    table.add(in);
    return in;
  }

  /**
   * @return if the result for column was null
   * @see <a href="http://www.sqlite.org/c3ref/column_blob.html">sqlite3_column_type</a>
   */
  public boolean columnNull(int column) throws SQLiteException {
    myController.validate();
    int valueType = getColumnType(column, handle());
    return valueType == ValueType.SQLITE_NULL;
  }

  public int columnCount() throws SQLiteException {
    myController.validate();
    ensureCorrectColumnCount(handle());
    return myColumnCount;
  }


  public Object columnValue(int column) throws SQLiteException {
    myController.validate();
    int valueType = getColumnType(column, handle());
    switch (valueType) {
      case ValueType.SQLITE_NULL:
        return null;
      case ValueType.SQLITE_FLOAT:
        return columnDouble(column);
      case ValueType.SQLITE_INTEGER:
        long value = columnLong(column);
        return value == ((long) ((int) value)) ? Integer.valueOf((int) value) : Long.valueOf(value);
      case ValueType.SQLITE_TEXT:
        return columnString(column);
      case ValueType.SQLITE_BLOB:
        return columnBlob(column);
      default:
        Internal.recoverableError(this, "value type " + valueType + " not yet supported", true);
        return null;
    }
  }


  public String columnName(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnName(" + column + ")");
    String r = _SQLiteSwigged.sqlite3_column_name(handle, column);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnName(" + column + ")=" + r);
    return r;
  }

  public String columnTableName(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnTableName(" + column + ")");
    String r = _SQLiteSwigged.sqlite3_column_table_name(handle, column);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnTableName(" + column + ")=" + r);
    return r;
  }

  public String columnDatabaseName(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnDatabaseName(" + column + ")");
    String r = _SQLiteSwigged.sqlite3_column_database_name(handle, column);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnDatabaseName(" + column + ")=" + r);
    return r;
  }

  public String columnOriginName(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnOriginName(" + column + ")");
    String r = _SQLiteSwigged.sqlite3_column_origin_name(handle, column);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnOriginName(" + column + ")=" + r);
    return r;
  }

  /**
   * Clear all data, disposing the statement. May be called by SQLiteConnection on close.
   */
  void clear() {
    clearBindStreams(false);
    clearColumnStreams();
    myHandle = null;
    myHasRow = false;
    myColumnCount = 0;
    myHasBindings = false;
    myStepped = false;
    myController = SQLiteController.getDisposed(myController);
    Internal.logFine(this, "cleared");
  }

  private void clearColumnStreams() {
    FastTable<ColumnStream> table = myColumnStreams;
    if (table != null) {
      myColumnStreams = null;
      for (int i = 0; i < table.size(); i++) {
        try {
          table.get(i).close();
        } catch (IOException e) {
          Internal.logFine(this, e.toString());
        }
      }
    }
  }

  private void clearBindStreams(boolean bind) {
    FastTable<BindStream> table = myBindStreams;
    if (table != null) {
      myBindStreams = null;
      for (int i = 0; i < table.size(); i++) {
        BindStream stream = table.get(i);
        if (bind && !stream.isDisposed()) {
          try {
            stream.close();
          } catch (IOException e) {
            Internal.logFine(this, e.toString());
          }
        } else {
          stream.dispose();
        }
      }
      table.clear();
    }
  }

  private SWIGTYPE_p_sqlite3_stmt handle() throws SQLiteException {
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle == null) {
      throw new SQLiteException(Wrapper.WRAPPER_STATEMENT_DISPOSED, null);
    }
    return handle;
  }

  private int getColumnType(int column, SWIGTYPE_p_sqlite3_stmt handle) throws SQLiteException {
    checkColumn(column, handle);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnType(" + column + ")");
    int valueType = _SQLiteSwigged.sqlite3_column_type(handle, column);
    if (Internal.isFineLogging())
      Internal.logFine(this, "columnType(" + column + ")=" + valueType);
    return valueType;
  }

  private void checkColumn(int column, SWIGTYPE_p_sqlite3_stmt handle) throws SQLiteException {
    // assert right thread
    if (!myHasRow)
      throw new SQLiteException(Wrapper.WRAPPER_NO_ROW, null);
    if (column < 0)
      throw new SQLiteException(Wrapper.WRAPPER_COLUMN_OUT_OF_RANGE, String.valueOf(column));
    ensureCorrectColumnCount(handle);
    if (column >= myColumnCount)
      throw new SQLiteException(Wrapper.WRAPPER_COLUMN_OUT_OF_RANGE, column + "(" + myColumnCount + ")");
  }

  private void ensureCorrectColumnCount(SWIGTYPE_p_sqlite3_stmt handle) {
    if (myColumnCount == COLUMN_COUNT_UNKNOWN) {
      // data_count seems more safe than column_count
      Internal.logFine(this, "asking column count");
      myColumnCount = _SQLiteSwigged.sqlite3_data_count(handle);
      if (Internal.isFineLogging())
        Internal.logFine(this, "data_count=" + myColumnCount);
    }
  }

  public String toString() {
    return "[" + mySqlParts + "]" + myController;
  }

/*
  protected void finalize() throws Throwable {
    super.finalize();
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle != null) {
      Internal.recoverableError(this, "wasn't disposed", true);
    }
  }
*/

  SWIGTYPE_p_sqlite3_stmt statementHandle() {
    return myHandle;
  }

  private final class BindStream extends OutputStream {
    private final int myIndex;
    private DirectBuffer myBuffer;

    public BindStream(int index, DirectBuffer buffer) throws IOException {
      myIndex = index;
      myBuffer = buffer;
      myBuffer.data().clear();
    }

    public void write(int b) throws IOException {
      try {
        myController.validate();
        ByteBuffer data = buffer(1);
        data.put((byte) b);
      } catch (SQLiteException e) {
        dispose();
        throw new IOException("cannot write: " + e);
      }
    }

    public void write(byte b[], int off, int len) throws IOException {
      try {
        myController.validate();
        ByteBuffer data = buffer(len);
        data.put(b, off, len);
      } catch (SQLiteException e) {
        dispose();
        throw new IOException("cannot write: " + e);
      }
    }

    private ByteBuffer buffer(int len) throws IOException, SQLiteException {
      DirectBuffer buffer = getBuffer();
      ByteBuffer data = buffer.data();
      if (data.remaining() < len) {
        DirectBuffer newBuffer = null;
        try {
          newBuffer = myController.allocateBuffer(buffer.getCapacity() + len);
        } catch (IOException e) {
          dispose();
          throw e;
        }
        ByteBuffer newData = newBuffer.data();
        data.flip();
        newData.put(data);
        myController.freeBuffer(buffer);
        data = newData;
        myBuffer = newBuffer;
        assert data.remaining() >= len : data.capacity();
      }
      return data;
    }

    public void close() throws IOException {
      try {
        myController.validate();
        DirectBuffer buffer = myBuffer;
        if (buffer == null)
          return;
        if (Internal.isFineLogging())
          Internal.logFine(SQLiteStatement.this, "BindStream.close:bind([" + buffer.data().capacity() + "])");
        int rc = _SQLiteManual.wrapper_bind_buffer(handle(), myIndex, buffer);
        dispose();
        myController.throwResult(rc, "bind(buffer)", SQLiteStatement.this);
      } catch (SQLiteException e) {
        throw new IOException("cannot write: " + e);
      }
    }

    public boolean isDisposed() {
      return myBuffer == null;
    }

    private DirectBuffer getBuffer() throws IOException {
      DirectBuffer buffer = myBuffer;
      if (buffer == null)
        throw new IOException("stream discarded");
      if (!buffer.isValid())
        throw new IOException("buffer discarded");
      if (!buffer.isUsed())
        throw new IOException("buffer not used");
      return buffer;
    }

    public void dispose() {
      DirectBuffer buffer = myBuffer;
      if (buffer != null) {
        myBuffer = null;
        myController.freeBuffer(buffer);
      }
      FastTable<BindStream> list = myBindStreams;
      if (list != null) {
        list.remove(this);
      }
    }
  }

  private class ColumnStream extends InputStream {
    private ByteBuffer myBuffer;

    public ColumnStream(ByteBuffer buffer) {
      assert buffer != null;
      myBuffer = buffer;
    }

    public int read() throws IOException {
      ByteBuffer buffer = getBuffer();
      if (buffer.remaining() <= 0)
        return -1;
      byte b = 0;
      try {
        b = buffer.get();
      } catch (BufferUnderflowException e) {
        Internal.logWarn(this, "weird: " + e);
        return -1;
      }
      return ((int) b) & 0xFF;
    }

    public int read(byte b[], int off, int len) throws IOException {
      ByteBuffer buffer = getBuffer();
      int rem = buffer.remaining();
      if (rem <= 0)
        return -1;
      try {
        if (rem < len)
          len = rem;
        buffer.get(b, off, len);
        return len;
      } catch (BufferUnderflowException e) {
        Internal.logWarn(this, "weird: " + e);
        return -1;
      }
    }

    public void close() throws IOException {
      myBuffer = null;
      FastTable<ColumnStream> table = myColumnStreams;
      if (table != null)
        table.remove(this);
    }

    public ByteBuffer getBuffer() throws IOException {
      ByteBuffer buffer = myBuffer;
      if (buffer == null)
        throw new IOException("stream closed");
      return buffer;
    }
  }
}
