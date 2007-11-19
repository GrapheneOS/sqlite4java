package sqlite;

import static sqlite.internal.SQLiteConstants.*;
import sqlite.internal.SQLiteSwigged;
import sqlite.internal.SWIGTYPE_p_sqlite3_stmt;
import sqlite.internal.SQLiteManual;

/**
 * This class encapsulates sqlite statement. It is tightly linked to the opening connection, and confined to
 * the same thread.
 */
public final class DBStatement {
  private static final int COLUMN_COUNT_UNKNOWN = -1;

  private final DBConnection myConnection;
  private final String mySql;

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

  DBStatement(DBConnection connection, SWIGTYPE_p_sqlite3_stmt handle, String sql, int openCounter) {
    assert handle != null;
    myConnection = connection;
    myHandle = handle;
    mySql = sql;
    myDbOpenCounter = openCounter;
    DBInternal.logger.info(this + " created");
  }

  public boolean isDisposed() {
    try {
      myConnection.checkThread();
    } catch (DBException e) {
      DBInternal.recoverableError(this, "isDisposed() " + e.getMessage(), true);
    }
    return myHandle == null;
  }

  public boolean isUsable() {
    try {
      myConnection.checkThread();
    } catch (DBException e) {
      return false;
    }
    return myHandle != null && myConnection.isOpen(myDbOpenCounter);
  }

  public void dispose() throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle == null)
      return;
    myConnection.statementDisposed(this, mySql);
    myHandle = null;
    clearRow();
    myHasBindings = false;
    int rc = SQLiteSwigged.sqlite3_finalize(handle);
    myConnection.throwResult(rc, "dispose()", this);
    DBInternal.logger.info(this + " disposed");
  }

  public boolean step() throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    int rc = SQLiteSwigged.sqlite3_step(handle);
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

  public boolean hasRow() {
    return myHasRow;
  }

  public boolean hasBindings() {
    return myHasBindings;
  }

  public DBStatement reset() throws DBException {
    myConnection.checkThread();
    clearRow();
    int rc = SQLiteSwigged.sqlite3_reset(handle());
    myConnection.throwResult(rc, "reset()", this);
    return this;
  }

  public DBStatement clearBindings() throws DBException {
    myConnection.checkThread();
    int rc = SQLiteSwigged.sqlite3_clear_bindings(handle());
    myConnection.throwResult(rc, "clearBindings()", this);
    myHasBindings = false;
    return this;
  }

  /**
   * A shortcut for reset() and clearBindings()
   */
  public DBStatement clear() throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    clearRow();
    int rc = SQLiteSwigged.sqlite3_reset(handle);
    myConnection.throwResult(rc, "clear.reset()", this);
    if (myHasBindings) {
      rc = SQLiteSwigged.sqlite3_clear_bindings(handle);
      myConnection.throwResult(rc, "clear.clearBindings()", this);
      myHasBindings = false;
    }
    return this;
  }

  public DBStatement bind(int index, double value) throws DBException {
    myConnection.checkThread();
    int rc = SQLiteSwigged.sqlite3_bind_double(handle(), index, value);
    myConnection.throwResult(rc, "bind(double)", this);
    myHasBindings = true;
    return this;
  }

  public DBStatement bind(int index, int value) throws DBException {
    myConnection.checkThread();
    int rc = SQLiteSwigged.sqlite3_bind_int(handle(), index, value);
    myConnection.throwResult(rc, "bind(int)", this);
    myHasBindings = true;
    return this;
  }

  public DBStatement bind(int index, long value) throws DBException {
    myConnection.checkThread();
    int rc = SQLiteSwigged.sqlite3_bind_int64(handle(), index, value);
    myConnection.throwResult(rc, "bind(long)", this);
    myHasBindings = true;
    return this;
  }

  public DBStatement bind(int index, String value) throws DBException {
    if (value == null)
      return bindNull(index);
    myConnection.checkThread();
    int rc = SQLiteManual.sqlite3_bind_text(handle(), index, value);
    myConnection.throwResult(rc, "bind(String)", this);
    myHasBindings = true;
    return this;
  }

  public DBStatement bindNull(int index) throws DBException {
    myConnection.checkThread();
    int rc = SQLiteSwigged.sqlite3_bind_null(handle(), index);
    myConnection.throwResult(rc, "bind(null)", this);
    // specifically does not set myHasBindings to true
    return this;
  }

  public String columnString(int column) throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    int[] rc = {Integer.MIN_VALUE};
    String result = SQLiteManual.sqlite3_column_text(handle, column, rc);
    myConnection.throwResult(rc[0], "columnString()", this);
    return result;
  }

  public int columnInt(int column) throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    // todo check retrieval of long
    return SQLiteSwigged.sqlite3_column_int(handle, column);
  }

  public double columnDouble(int column) throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    return SQLiteSwigged.sqlite3_column_double(handle, column);
  }

  public long columnLong(int column) throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    return SQLiteSwigged.sqlite3_column_int64(handle, column);
  }

  public boolean columnNull(int column) throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = handle();
    checkColumn(column, handle);
    int valueType = SQLiteSwigged.sqlite3_column_type(handle, column);
    return valueType == ValueType.SQLITE_NULL;
  }

  private SWIGTYPE_p_sqlite3_stmt handle() throws DBException {
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle == null) {
      throw new DBException(Wrapper.WRAPPER_STATEMENT_DISPOSED, null);
    }
    if (!myConnection.isOpen(myDbOpenCounter)) {
      throw new DBException(Wrapper.WRAPPER_NOT_OPENED, null);
    }
    return handle;
  }

  private void clearRow() {
    myHasRow = false;
    myColumnCount = 0;
  }

  private void checkColumn(int column, SWIGTYPE_p_sqlite3_stmt handle) throws DBException {
    // assert right thread
    if (!myHasRow)
      throw new DBException(Wrapper.WRAPPER_NO_ROW, null);
    if (column < 0)
      throw new DBException(Wrapper.WRAPPER_COLUMN_OUT_OF_RANGE, String.valueOf(column));
    if (myColumnCount == COLUMN_COUNT_UNKNOWN) {
      // data_count seems more safe than column_count
      myColumnCount = SQLiteSwigged.sqlite3_data_count(handle);
    }
    if (column >= myColumnCount)
      throw new DBException(Wrapper.WRAPPER_COLUMN_OUT_OF_RANGE, column + "(" + myColumnCount + ")");
  }

  public String toString() {
    return myConnection + "[" + mySql + "]";
  }

  protected void finalize() throws Throwable {
    super.finalize();
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle != null) {
      DBInternal.recoverableError(this, "wasn't disposed", true);
    }
  }
}
