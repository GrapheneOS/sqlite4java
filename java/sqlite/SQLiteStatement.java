package sqlite;

import static sqlite.internal.SQLiteConstants.*;
import sqlite.internal.SWIGTYPE_p_sqlite3_stmt;
import sqlite.internal._SQLiteManual;
import sqlite.internal._SQLiteSwigged;

/**
 * This class encapsulates sqlite statement. It is tightly linked to the opening connection, and confined to
 * the same thread.
 */
public final class SQLiteStatement {
  private static final int COLUMN_COUNT_UNKNOWN = -1;

  private final SQLiteConnection myConnection;
  private final String mySql;
  private final boolean myCached;

  /**
   * Becomes null when closed.
   */
  private SWIGTYPE_p_sqlite3_stmt myHandle;
  private final int myDbOpenCounter;

  /**
   * When true, the last step() returned SQLITE_ROW, which means data can be read.
   */
  private boolean myHasRow;

  /**
   * When true, values have been bound to the statement. (and they take up memory)
   */
  private boolean myHasBindings;

  /**
   * The number of columns in current result set. When set to COLUMN_COUNT_UNKNOWN, the number of columns should be requested
   * at first need.
   */
  private int myColumnCount;

  SQLiteStatement(SQLiteConnection connection, SWIGTYPE_p_sqlite3_stmt handle, String sql, int openCounter, boolean cached) {
    assert handle != null;
    myConnection = connection;
    myHandle = handle;
    mySql = sql;
    myDbOpenCounter = openCounter;
    myCached = cached;
    Internal.logger.info(this + " created");
  }

  public boolean isFinished() {
    try {
      myConnection.checkThread();
    } catch (SQLiteException e) {
      Internal.recoverableError(this, "isFinished() " + e.getMessage(), true);
    }
    return myHandle == null;
  }

  public boolean isUsable() {
    try {
      myConnection.checkThread();
    } catch (SQLiteException e) {
      return false;
    }
    return myHandle != null && myConnection.isOpen(myDbOpenCounter);
  }

  public boolean isCached() {
    return myCached;
  }

  public SQLiteStatement dispose() throws SQLiteException {
    if (myCached)
      reset(true);
    else
      finish();
    return this;
  }

  public SQLiteStatement reset() throws SQLiteException {
    return reset(true);
  }

  public SQLiteStatement reset(boolean clearBindings) throws SQLiteException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    clearRow();
    int rc = _SQLiteSwigged.sqlite3_reset(handle);
    myConnection.throwResult(rc, "reset()", this);
    if (clearBindings && myHasBindings) {
      rc = _SQLiteSwigged.sqlite3_clear_bindings(handle);
      myConnection.throwResult(rc, "reset.clearBindings()", this);
      myHasBindings = false;
    }
    return this;
  }

  public SQLiteStatement clearBindings() throws SQLiteException {
    myConnection.checkThread();
    int rc = _SQLiteSwigged.sqlite3_clear_bindings(handle());
    myConnection.throwResult(rc, "clearBindings()", this);
    myHasBindings = false;
    return this;
  }

  public boolean step() throws SQLiteException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    int rc = _SQLiteSwigged.sqlite3_step(handle);
    boolean result;
    if (rc == Result.SQLITE_ROW) {
      result = true;
      if (!myHasRow) {
        // at first row, set column count to COLUMN_COUNT_UNKNOWN so it will be requested at first need
        myColumnCount = COLUMN_COUNT_UNKNOWN;
      }
      myHasRow = true;
    } else {
      if (rc != Result.SQLITE_DONE) {
        myConnection.throwResult(rc, "step()", this);
      }
      result = false;
      clearRow();
    }
    return result;
  }

  public boolean isClear() {
    return !hasRow() && !hasBindings();
  }

  public boolean hasRow() {
    return myHasRow;
  }

  public boolean hasBindings() {
    return myHasBindings;
  }

  public SQLiteStatement bind(int index, double value) throws SQLiteException {
    myConnection.checkThread();
    int rc = _SQLiteSwigged.sqlite3_bind_double(handle(), index, value);
    myConnection.throwResult(rc, "bind(double)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bind(int index, int value) throws SQLiteException {
    myConnection.checkThread();
    int rc = _SQLiteSwigged.sqlite3_bind_int(handle(), index, value);
    myConnection.throwResult(rc, "bind(int)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bind(int index, long value) throws SQLiteException {
    myConnection.checkThread();
    int rc = _SQLiteSwigged.sqlite3_bind_int64(handle(), index, value);
    myConnection.throwResult(rc, "bind(long)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bind(int index, String value) throws SQLiteException {
    if (value == null)
      return bindNull(index);
    myConnection.checkThread();
    int rc = _SQLiteManual.sqlite3_bind_text(handle(), index, value);
    myConnection.throwResult(rc, "bind(String)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bindNull(int index) throws SQLiteException {
    myConnection.checkThread();
    int rc = _SQLiteSwigged.sqlite3_bind_null(handle(), index);
    myConnection.throwResult(rc, "bind(null)", this);
    // specifically does not set myHasBindings to true
    return this;
  }

  public String columnString(int column) throws SQLiteException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    int[] rc = {Integer.MIN_VALUE};
    String result = _SQLiteManual.sqlite3_column_text(handle, column, rc);
    myConnection.throwResult(rc[0], "columnString()", this);
    return result;
  }

  public int columnInt(int column) throws SQLiteException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    // todo check retrieval of long
    return _SQLiteSwigged.sqlite3_column_int(handle, column);
  }

  public double columnDouble(int column) throws SQLiteException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    return _SQLiteSwigged.sqlite3_column_double(handle, column);
  }

  public long columnLong(int column) throws SQLiteException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    return _SQLiteSwigged.sqlite3_column_int64(handle, column);
  }

  public boolean columnNull(int column) throws SQLiteException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    int valueType = _SQLiteSwigged.sqlite3_column_type(handle, column);
    return valueType == ValueType.SQLITE_NULL;
  }

  void finish() throws SQLiteException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle == null)
      return;
    myConnection.statementFinished(this, mySql);
    myHandle = null;
    clearRow();
    myHasBindings = false;
    int rc = _SQLiteSwigged.sqlite3_finalize(handle);
    myConnection.throwResult(rc, "finish()", this);
    Internal.logger.info(this + " finished");
  }

  private SWIGTYPE_p_sqlite3_stmt handle() throws SQLiteException {
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle == null) {
      throw new SQLiteException(Wrapper.WRAPPER_STATEMENT_FINISHED, null);
    }
    if (!myConnection.isOpen(myDbOpenCounter)) {
      throw new SQLiteException(Wrapper.WRAPPER_NOT_OPENED, null);
    }
    return handle;
  }

  private void clearRow() {
    myHasRow = false;
    myColumnCount = 0;
  }

  private void checkColumn(int column, SWIGTYPE_p_sqlite3_stmt handle) throws SQLiteException {
    // assert right thread
    if (!myHasRow)
      throw new SQLiteException(Wrapper.WRAPPER_NO_ROW, null);
    if (column < 0)
      throw new SQLiteException(Wrapper.WRAPPER_COLUMN_OUT_OF_RANGE, String.valueOf(column));
    if (myColumnCount == COLUMN_COUNT_UNKNOWN) {
      // data_count seems more safe than column_count
      myColumnCount = _SQLiteSwigged.sqlite3_data_count(handle);
    }
    if (column >= myColumnCount)
      throw new SQLiteException(Wrapper.WRAPPER_COLUMN_OUT_OF_RANGE, column + "(" + myColumnCount + ")");
  }

  public String toString() {
    return myConnection + "[" + mySql + "]" + (myCached ? "[C]" : "");
  }

  protected void finalize() throws Throwable {
    super.finalize();
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle != null) {
      Internal.recoverableError(this, "wasn't finished", true);
    }
  }
}
