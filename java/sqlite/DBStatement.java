package sqlite;

import sqlite.internal.SWIGTYPE_p_sqlite3_stmt;
import sqlite.internal.SQLiteSwigged;
import sqlite.internal.SWIGTYPE_p_sqlite3;
import sqlite.internal.SQLiteConstants;

/**
 * This class encapsulates sqlite statement.
 */
public final class DBStatement {
  private final DBConnection myConnection;
  private final String mySql;

  /**
   * Becomes null when closed.
   */
  private SWIGTYPE_p_sqlite3_stmt myHandle;

  public DBStatement(DBConnection connection, SWIGTYPE_p_sqlite3_stmt handle, String sql) {
    assert handle != null;
    myConnection = connection;
    myHandle = handle;
    mySql = sql;
    DBGlobal.logger.info(this + " created");
  }

  public boolean isDisposed() {
    try {
      myConnection.checkThread();
    } catch (DBException e) {
      DBGlobal.recoverableError(this, "isDisposed() " + e.getMessage(), true);
    }
    return myHandle == null;
  }

  public void dispose() throws DBException {
    myConnection.checkThread();
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle == null)
      return;
    myConnection.statementDisposed(this, mySql);
    myHandle = null;
    int rc = SQLiteSwigged.sqlite3_finalize(handle);
    myConnection.throwResult(rc, "statement.dispose()", toString());
    DBGlobal.logger.info(this + " disposed");
  }

  public String toString() {
    return myConnection + "[" + mySql + "]";
  }

  protected void finalize() throws Throwable {
    super.finalize();
    SWIGTYPE_p_sqlite3_stmt handle = myHandle;
    if (handle != null) {
      DBGlobal.recoverableError(this, "wasn't disposed", true);
    }
  }
}
