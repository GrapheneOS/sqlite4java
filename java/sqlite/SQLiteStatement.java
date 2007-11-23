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

  private final String mySql;

  private StatementController myController;

  /**
   * Becomes null when disposed.
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
   * When true, the statement has performed step() and needs to be reset.
   */
  private boolean myStepped;

  /**
   * The number of columns in current result set. When set to COLUMN_COUNT_UNKNOWN, the number of columns should be requested
   * at first need.
   */
  private int myColumnCount;

  SQLiteStatement(StatementController controller, SWIGTYPE_p_sqlite3_stmt handle, String sql) {
    assert handle != null;
    myController = controller;
    myHandle = handle;
    mySql = sql;
    Internal.logger.info(this + " created");
  }

  public boolean isDisposed() {
    try {
      myController.validate();
    } catch (SQLiteException e) {
      Internal.recoverableError(this, "isFinished() " + e.getMessage(), true);
    }
    return myHandle == null;
  }

  public String getSql() {
    return mySql;
  }

  public void dispose() {
    try {
      myController.validate();
    } catch (SQLiteException e) {
      Internal.recoverableError(this, "invalid dispose: " + e, true);
      return;
    }
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle == null)
      return;
    boolean hasBindings = myHasBindings;
    boolean stepped = myStepped;
    myHandle = null;
    myHasRow = false;
    myColumnCount = 0;
    myHasBindings = false;
    myStepped = false;
    myController.disposed(handle, mySql, hasBindings, stepped);
    myController = new StatementController.DisposedStatementController(myController.toString());
  }

  public SQLiteStatement reset() throws SQLiteException {
    return reset(true);
  }

  public SQLiteStatement reset(boolean clearBindings) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    if (myStepped) {
      int rc = _SQLiteSwigged.sqlite3_reset(handle);
      myController.throwResult(rc, "reset()", this);
    }
    if (clearBindings && myHasBindings) {
      int rc = _SQLiteSwigged.sqlite3_clear_bindings(handle);
      myController.throwResult(rc, "reset.clearBindings()", this);
    }
    myHasRow = false;
    myColumnCount = 0;
    myStepped = false;
    myHasBindings = false;
    return this;
  }

  public SQLiteStatement clearBindings() throws SQLiteException {
    myController.validate();
    if (myHasBindings) {
      int rc = _SQLiteSwigged.sqlite3_clear_bindings(handle());
      myController.throwResult(rc, "clearBindings()", this);
    }
    myHasBindings = false;
    return this;
  }

  public boolean step() throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    int rc = _SQLiteSwigged.sqlite3_step(handle);
    myStepped = true;
    if (rc == Result.SQLITE_ROW) {
      if (!myHasRow) {
        // at first row, set column count to COLUMN_COUNT_UNKNOWN so it will be requested at first need
        myColumnCount = COLUMN_COUNT_UNKNOWN;
      }
      myHasRow = true;
    } else if (rc == Result.SQLITE_DONE) {
      myColumnCount = 0;
      myHasRow = false;
    } else {
      myController.throwResult(rc, "step()", this);
    }
    return myHasRow;
  }

  public boolean hasRow() {
    return myHasRow;
  }

  public boolean hasBindings() {
    return myHasBindings;
  }

  public boolean hasStepped() {
    return myStepped;
  }

  public SQLiteStatement bind(int index, double value) throws SQLiteException {
    myController.validate();
    int rc = _SQLiteSwigged.sqlite3_bind_double(handle(), index, value);
    myController.throwResult(rc, "bind(double)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bind(int index, int value) throws SQLiteException {
    myController.validate();
    int rc = _SQLiteSwigged.sqlite3_bind_int(handle(), index, value);
    myController.throwResult(rc, "bind(int)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bind(int index, long value) throws SQLiteException {
    myController.validate();
    int rc = _SQLiteSwigged.sqlite3_bind_int64(handle(), index, value);
    myController.throwResult(rc, "bind(long)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bind(int index, String value) throws SQLiteException {
    if (value == null)
      return bindNull(index);
    myController.validate();
    int rc = _SQLiteManual.sqlite3_bind_text(handle(), index, value);
    myController.throwResult(rc, "bind(String)", this);
    myHasBindings = true;
    return this;
  }

  public SQLiteStatement bindNull(int index) throws SQLiteException {
    myController.validate();
    int rc = _SQLiteSwigged.sqlite3_bind_null(handle(), index);
    myController.throwResult(rc, "bind(null)", this);
    // specifically does not set myHasBindings to true
    return this;
  }

  public String columnString(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    int[] rc = {Integer.MIN_VALUE};
    String result = _SQLiteManual.sqlite3_column_text(handle, column, rc);
    myController.throwResult(rc[0], "columnString()", this);
    return result;
  }

  public int columnInt(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    return _SQLiteSwigged.sqlite3_column_int(handle, column);
  }

  public double columnDouble(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    return _SQLiteSwigged.sqlite3_column_double(handle, column);
  }

  public long columnLong(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    return _SQLiteSwigged.sqlite3_column_int64(handle, column);
  }

  public boolean columnNull(int column) throws SQLiteException {
    myController.validate();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    int valueType = _SQLiteSwigged.sqlite3_column_type(handle, column);
    return valueType == ValueType.SQLITE_NULL;
  }

  private SWIGTYPE_p_sqlite3_stmt handle() throws SQLiteException {
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle == null) {
      throw new SQLiteException(Wrapper.WRAPPER_STATEMENT_DISPOSED, null);
    }
    return handle;
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
    return "[" + mySql + "]" + myController;
  }

  protected void finalize() throws Throwable {
    super.finalize();
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle != null) {
      Internal.recoverableError(this, "wasn't disposed", true);
    }
  }

  SWIGTYPE_p_sqlite3_stmt statementHandle() {
    return myHandle;
  }
}
