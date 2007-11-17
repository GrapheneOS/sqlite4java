package sqlite;

import sqlite.internal.*;
import static sqlite.internal.SQLiteConstants.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * DBConnection is a single connection to sqlite database. Most methods are thread-confined,
 * and will throw errors if called from alien thread. Confinement thread is defined at the
 * construction time.
 * <p/>
 * DBConnection should be expicitly closed before the object is disposed. Failing to do so
 * may result in unpredictable behavior from sqlite.
 */
public final class DBConnection {
  /**
   * The database file, or null if it is memory database
   */
  private final File myFile;
  private final Thread myConfinement;
  private final int myNumber = ++DBGlobal.lastConnectionNumber;
  private final Object myLock = new Object();

  /**
   * Handle to the db. Almost confined: usually not changed outside the confining thread, except for close() method.
   */
  private SWIGTYPE_p_sqlite3 myHandle;

  /**
   * Prepared statements. Almost confined.
   */
  private final Map<String, DBStatement> myStatementCache = new HashMap<String, DBStatement>();
  private final List<DBStatement> myStatements = new ArrayList<DBStatement>();

  /**
   * Create connection to database located in the specified file.
   * Database is not opened by this method, but the whole object is being confined to
   * the calling thread. So call the constructor only in the thread which will be used
   * to work with the connection.
   *
   * @param dbfile database file, or null for memory database
   */
  public DBConnection(File dbfile) {
    myFile = dbfile;
    myConfinement = Thread.currentThread();
    DBGlobal.logger.info(this + " created(" + myFile + "," + myConfinement + ")");
  }

  /**
   * Create connection to in-memory temporary database.
   *
   * @see #DBConnection(java.io.File)
   */
  public DBConnection() {
    this(null);
  }

  /**
   * @return the file hosting the database, or null if database is in memory
   */
  public File getDatabaseFile() {
    return myFile;
  }

  public boolean isMemoryDatabase() {
    return myFile == null;
  }

  /**
   * Opens database, creating it if needed.
   *
   * @see #open(boolean)
   */
  public void open() throws DBException {
    open(true);
  }

  /**
   * Opens database. If database is already open, fails gracefully, allowing process
   * to continue in production mode.
   *
   * @param allowCreate if true, database file may be created. For in-memory database, must
   *                    be true
   */
  public void open(boolean allowCreate) throws DBException {
    int flags = Open.SQLITE_OPEN_READWRITE;
    if (!allowCreate) {
      if (isMemoryDatabase()) {
        throw new DBException(Wrapper.WRAPPER_WEIRD, "cannot open memory database without creation");
      }
    } else {
      flags |= Open.SQLITE_OPEN_CREATE;
    }
    openX(flags);
  }

  /**
   * Opens database is read-only mode. Not applicable for in-memory database.
   */
  public void openReadonly() throws DBException {
    if (isMemoryDatabase()) {
      throw new DBException(Wrapper.WRAPPER_WEIRD, "cannot open memory database in read-only mode");
    }
    openX(Open.SQLITE_OPEN_READONLY);
  }

  /**
   * Tells whether database is open. May be called from another thread.
   */
  public boolean isOpen() {
    synchronized (myLock) {
      return myHandle != null;
    }
  }

  /**
   * Closes database. After database is closed, it may be reopened again. In case of in-memory
   * database, the reopened database will be empty.
   * <p/>
   * This method may be called from another thread.
   */
  public void close() {
    SWIGTYPE_p_sqlite3 handle;
    DBStatement[] statements = null;
    synchronized (myLock) {
      handle = myHandle;
      if (handle == null)
        return;
      myHandle = null;
      statements = getStatementsForDisposeOnClose(statements);
    }
    disposeStatements(statements);
    int rc = SQLiteSwigged.sqlite3_close(handle);
    // rc may be SQLiteConstants.Result.SQLITE_BUSY if statements are open
    if (rc != SQLiteConstants.Result.SQLITE_OK) {
      String errmsg = null;
      try {
        errmsg = SQLiteSwigged.sqlite3_errmsg(handle);
      } catch (Exception e) {
        DBGlobal.logger.log(Level.WARNING, "cannot get sqlite3_errmsg", e);
      }
      DBGlobal.logger.warning(this + " close error " + rc + (errmsg == null ? "" : ": " + errmsg));
    }
    DBGlobal.logger.info(this + " closed");
  }

  private void disposeStatements(DBStatement[] statements) {
    if (statements != null) {
      for (DBStatement statement : statements) {
        try {
          statement.dispose();
        } catch (DBException e) {
          DBGlobal.logger.log(Level.WARNING, "dispose(" + statement + ") during close()", e);
        }
      }
    }
    synchronized (myLock) {
      if (!myStatements.isEmpty()) {
        DBGlobal.recoverableError(this, "not all statements disposed (" + myStatements + ")", true);
        myStatements.clear();
      }
      if (!myStatementCache.isEmpty()) {
        DBGlobal.recoverableError(this, "statement cache not empty (" + myStatementCache + ")", true);
        myStatementCache.clear();
      }
    }
  }

  private DBStatement[] getStatementsForDisposeOnClose(DBStatement[] statements) {
    if (!myStatements.isEmpty()) {
      if (myConfinement == Thread.currentThread()) {
        statements = myStatements.toArray(new DBStatement[myStatements.size()]);
      } else {
        DBGlobal.logger.warning(this + " cannot clear " + myStatements.size() + " statements when closing from alien threads");
      }
    }
    return statements;
  }

  public void exec(String sql) throws DBException {
    checkThread();
    String[] error = {null};
    int rc = SQLiteManual.sqlite3_exec(handle(), sql, error);
    throwResult(rc, "exec()", error[0]);
  }

  public DBStatement prepare(String sql) throws DBException {
    return prepare(sql, true);
  }

  public DBStatement prepare(String sql, boolean useCache) throws DBException {
    checkThread();
    SWIGTYPE_p_sqlite3 handle;
    synchronized (myLock) {
      if (useCache) {
        DBStatement statement = myStatementCache.get(sql);
        if (statement != null) {
          return statement;
        }
      }
      handle = handle();
    }
    int[] rc = {Integer.MIN_VALUE};
    SWIGTYPE_p_sqlite3_stmt stmt = SQLiteManual.sqlite3_prepare_v2(handle, sql, rc);
    throwResult(rc[0], "prepare()", sql);
    if (stmt == null)
      throw new DBException(Wrapper.WRAPPER_WEIRD, "sqlite did not return stmt");
    DBStatement statement = null;
    synchronized (myLock) {
      // the connection may close while prepare in progress
      // most probably that would throw DBException earlier, but we'll check anyway
      if (myHandle != null) {
        statement = new DBStatement(this, stmt, sql);
        myStatements.add(statement);
        if (useCache) {
          myStatementCache.put(sql, statement);
        }
      }
    }
    if (statement == null) {
      // connection closed
      try {
        throwResult(SQLiteSwigged.sqlite3_finalize(stmt), "finalize() in prepare()");
      } catch (Exception e) {
        // ignore
      }
      throw new DBException(Wrapper.WRAPPER_NOT_OPENED, "connection closed while prepare() was in progress");
    }
    return statement;
  }

  void statementDisposed(DBStatement statement, String sql) {
    synchronized (myLock) {
      if (!myStatements.remove(statement)) {
        DBGlobal.recoverableError(statement, "unknown statement disposed", true);
      }
      DBStatement removed = myStatementCache.remove(sql);
      if (removed != null && removed != statement) {
        // statement wasn't cached, but another statement with the same sql was cached
        myStatementCache.put(sql, removed);
      }
    }
  }

  private SWIGTYPE_p_sqlite3 handle() throws DBException {
    synchronized (myLock) {
      SWIGTYPE_p_sqlite3 handle = myHandle;
      if (handle == null)
        throw new DBException(Wrapper.WRAPPER_NOT_OPENED, null);
      return handle;
    }
  }

  private void throwResult(int resultCode, String operation) throws DBException {
    throwResult(resultCode, operation, null);
  }

  void throwResult(int resultCode, String operation, String additional) throws DBException {
    if (resultCode != SQLiteConstants.Result.SQLITE_OK) {
      // ignore sync
      SWIGTYPE_p_sqlite3 handle = myHandle;
      String message = this + " " + operation;
      if (additional != null)
        message += " " + additional;
      if (handle != null) {
        try {
          String errmsg = SQLiteSwigged.sqlite3_errmsg(handle);
          if (additional == null || !additional.equals(errmsg)) {
            message += " [" + errmsg + "]";
          }
        } catch (Exception e) {
          DBGlobal.logger.log(Level.WARNING, "cannot get sqlite3_errmsg", e);
        }
      }
      throw new DBException(resultCode, message);
    }
  }

  private void openX(int flags) throws DBException {
    checkThread();
    SWIGTYPE_p_sqlite3 handle;
    synchronized (myLock) {
      handle = myHandle;
    }
    if (handle != null) {
      DBGlobal.recoverableError(this, "already opened", true);
      return;
    }
    String dbname = getSqliteDbName();
    int[] rc = {Integer.MIN_VALUE};
    handle = SQLiteManual.sqlite3_open_v2(dbname, flags, rc);
    if (rc[0] != Result.SQLITE_OK) {
      if (handle != null) {
        try {
          SQLiteSwigged.sqlite3_close(handle);
        } catch (Exception e) {
          // ignore
        }
      }
      String errorMessage = SQLiteSwigged.sqlite3_errmsg(null);
      throw new DBException(rc[0], errorMessage);
    }
    if (handle == null) {
      throw new DBException(Wrapper.WRAPPER_WEIRD, "sqlite didn't return db handle");
    }
    synchronized (myLock) {
      myHandle = handle;
    }
    DBGlobal.logger.info(this + " opened(" + flags + ")");
  }

  private String getSqliteDbName() {
    return myFile == null ? ":memory:" : myFile.getAbsolutePath();
  }

  int getStatementCount() {
    synchronized (myLock) {
      return myStatements.size();
    }
  }

  void checkThread() throws DBException {
    Thread thread = Thread.currentThread();
    if (thread != myConfinement) {
      String message = this + " confined(" + myConfinement + ") used(" + thread + ")";
      throw new DBException(Wrapper.WRAPPER_CONFINEMENT_VIOLATED, message);
    }
  }

  public String toString() {
    return "sqlite[" + myNumber + "]";
  }

  protected void finalize() throws Throwable {
    super.finalize();
    SWIGTYPE_p_sqlite3 handle = myHandle;
    if (handle != null) {
      DBGlobal.recoverableError(this, "wasn't closed before disposal", true);
      try {
        close();
      } catch (Throwable e) {
        // ignore
      }
    }
  }
}
