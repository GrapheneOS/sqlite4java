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
public final class SQLiteConnection {
  /**
   * The database file, or null if it is memory database
   */
  private final File myFile;
  private final int myNumber = Internal.nextConnectionNumber();
  private final Object myLock = new Object();

  /**
   * Confinement thread, set on open().
   */
  private volatile Thread myConfinement;

  /**
   * Handle to the db. Almost confined: usually not changed outside the confining thread, except for close() method.
   */
  private SWIGTYPE_p_sqlite3 myHandle;
  private int myOpenCounter;

  /**
   * Prepared statements. Almost confined.
   */
  private final Map<String, SQLiteStatement> myStatementCache = new HashMap<String, SQLiteStatement>();
  private final List<SQLiteStatement> myStatements = new ArrayList<SQLiteStatement>();

  /**
   * Create connection to database located in the specified file.
   * Database is not opened by this method, but the whole object is being confined to
   * the calling thread. So call the constructor only in the thread which will be used
   * to work with the connection.
   *
   * @param dbfile database file, or null for memory database
   */
  public SQLiteConnection(File dbfile) {
    myFile = dbfile;
    Internal.logger.info(this + " created(" + myFile + ")");
  }

  /**
   * Create connection to in-memory temporary database.
   *
   * @see #SQLiteConnection(java.io.File)
   */
  public SQLiteConnection() {
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
  public SQLiteConnection open() throws SQLiteException {
    return open(true);
  }

  /**
   * Opens database. If database is already open, fails gracefully, allowing process
   * to continue in production mode.
   *
   * @param allowCreate if true, database file may be created. For in-memory database, must
   *                    be true
   */
  public SQLiteConnection open(boolean allowCreate) throws SQLiteException {
    int flags = Open.SQLITE_OPEN_READWRITE;
    if (!allowCreate) {
      if (isMemoryDatabase()) {
        throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "cannot open memory database without creation");
      }
    } else {
      flags |= Open.SQLITE_OPEN_CREATE;
    }
    openX(flags);
    return this;
  }

  /**
   * Opens database is read-only mode. Not applicable for in-memory database.
   */
  public SQLiteConnection openReadonly() throws SQLiteException {
    if (isMemoryDatabase()) {
      throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "cannot open memory database in read-only mode");
    }
    openX(Open.SQLITE_OPEN_READONLY);
    return this;
  }

  /**
   * Tells whether database is open. May be called from another thread.
   */
  public boolean isOpen() {
    synchronized (myLock) {
      return myHandle != null;
    }
  }

  boolean isOpen(int openCounter) {
    synchronized (myLock) {
      return myHandle != null && myOpenCounter == openCounter;
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
    SQLiteStatement[] statements = null;
    synchronized (myLock) {
      handle = myHandle;
      if (handle == null)
        return;
      myHandle = null;
      myConfinement = null;
      statements = getStatementsForDisposeOnClose();
    }
    disposeStatements(statements);
    int rc = _SQLiteSwigged.sqlite3_close(handle);
    // rc may be SQLiteConstants.Result.SQLITE_BUSY if statements are open
    if (rc != SQLiteConstants.Result.SQLITE_OK) {
      String errmsg = null;
      try {
        errmsg = _SQLiteSwigged.sqlite3_errmsg(handle);
      } catch (Exception e) {
        Internal.logger.log(Level.WARNING, "cannot get sqlite3_errmsg", e);
      }
      Internal.logger.warning(this + " close error " + rc + (errmsg == null ? "" : ": " + errmsg));
    }
    Internal.logger.info(this + " closed");
  }

  public SQLiteConnection exec(String sql) throws SQLiteException {
    checkThread();
    String[] error = {null};
    int rc = _SQLiteManual.sqlite3_exec(handle(), sql, error);
    throwResult(rc, "exec()", error[0]);
    return this;
  }

  public SQLiteStatement prepare(String sql) throws SQLiteException {
    return prepare(sql, true);
  }

  public SQLiteStatement prepare(String sql, boolean managed) throws SQLiteException {
    checkThread();
    SWIGTYPE_p_sqlite3 handle;
    int openCounter;
    SQLiteStatement statement = null;
    synchronized (myLock) {
      if (managed) {
        statement = myStatementCache.get(sql);
      }
      handle = handle();
      openCounter = myOpenCounter;
    }
    if (statement != null) {
      statement = validateCachedStatement(statement);
      if (statement != null) {
        return statement;
      } else {
        // cannot use statement from cache
        managed = false;
      }
    }
    int[] rc = {Integer.MIN_VALUE};
    SWIGTYPE_p_sqlite3_stmt stmt = _SQLiteManual.sqlite3_prepare_v2(handle, sql, rc);
    throwResult(rc[0], "prepare()", sql);
    if (stmt == null)
      throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "sqlite did not return stmt");
    synchronized (myLock) {
      // the connection may close while prepare in progress
      // most probably that would throw SQLiteException earlier, but we'll check anyway
      if (myHandle != null && myOpenCounter == openCounter) {
        statement = new SQLiteStatement(this, stmt, sql, openCounter, managed);
        myStatements.add(statement);
        if (managed) {
          myStatementCache.put(sql, statement);
        }
      }
    }
    if (statement == null) {
      // connection closed
      try {
        throwResult(_SQLiteSwigged.sqlite3_finalize(stmt), "finalize() in prepare()");
      } catch (Exception e) {
        // ignore
      }
      throw new SQLiteException(Wrapper.WRAPPER_NOT_OPENED, "connection closed while prepare() was in progress");
    }
    return statement;
  }

  private SQLiteStatement validateCachedStatement(SQLiteStatement statement) throws SQLiteException {
    boolean hasRow = statement.hasRow();
    boolean hasBindings = statement.hasBindings();
    if (hasRow || hasBindings) {
      String msg = hasRow ? (hasBindings ? "rows and bindings" : "rows") : "bindings";
      msg = statement + ": retrieved from cache with " + msg + ", clearing";

      // todo not sure if we need stack trace in log files here
//            IllegalStateException thrown = new IllegalStateException(msg);
      IllegalStateException thrown = null;
      Internal.logger.log(Level.WARNING, msg, thrown);
      statement.clear();
    }
    return statement;
  }

  private void disposeStatements(SQLiteStatement[] statements) {
    if (statements != null) {
      for (SQLiteStatement statement : statements) {
        try {
          statement.finish();
        } catch (SQLiteException e) {
          Internal.logger.log(Level.WARNING, "dispose(" + statement + ") during close()", e);
        }
      }
    }
    synchronized (myLock) {
      if (!myStatements.isEmpty()) {
        Internal.recoverableError(this, "not all statements disposed (" + myStatements + ")", false);
        myStatements.clear();
      }
      myStatementCache.clear();
    }
  }

  private SQLiteStatement[] getStatementsForDisposeOnClose() {
    SQLiteStatement[] statements = null;
    if (!myStatements.isEmpty()) {
      if (myConfinement == Thread.currentThread()) {
        statements = myStatements.toArray(new SQLiteStatement[myStatements.size()]);
      } else {
        Internal.logger.warning(this + " cannot clear " + myStatements.size() + " statements when closing from alien thread");
      }
    }
    return statements;
  }

  void statementDisposed(SQLiteStatement statement, String sql) {
    synchronized (myLock) {
      if (!myStatements.remove(statement)) {
        Internal.recoverableError(statement, "unknown statement disposed", true);
      }
      SQLiteStatement removed = myStatementCache.remove(sql);
      if (removed != null && removed != statement) {
        // statement wasn't cached, but another statement with the same sql was cached
        myStatementCache.put(sql, removed);
      }
    }
  }

  private SWIGTYPE_p_sqlite3 handle() throws SQLiteException {
    synchronized (myLock) {
      SWIGTYPE_p_sqlite3 handle = myHandle;
      if (handle == null)
        throw new SQLiteException(Wrapper.WRAPPER_NOT_OPENED, null);
      return handle;
    }
  }

  void throwResult(int resultCode, String operation) throws SQLiteException {
    throwResult(resultCode, operation, null);
  }

  void throwResult(int resultCode, String operation, Object additional) throws SQLiteException {
    if (resultCode != SQLiteConstants.Result.SQLITE_OK) {
      // ignore sync
      SWIGTYPE_p_sqlite3 handle = myHandle;
      String message = this + " " + operation;
      String additionalMessage = additional == null ? null : String.valueOf(additional);
      if (additionalMessage != null)
        message += " " + additionalMessage;
      if (handle != null) {
        try {
          String errmsg = _SQLiteSwigged.sqlite3_errmsg(handle);
          if (additionalMessage == null || !additionalMessage.equals(errmsg)) {
            message += " [" + errmsg + "]";
          }
        } catch (Exception e) {
          Internal.logger.log(Level.WARNING, "cannot get sqlite3_errmsg", e);
        }
      }
      throw new SQLiteException(resultCode, message);
    }
  }

  private void openX(int flags) throws SQLiteException {
    SQLite.loadLibrary();
    SWIGTYPE_p_sqlite3 handle;
    synchronized (myLock) {
      if (myConfinement == null) {
        myConfinement = Thread.currentThread();
        Internal.logger.fine(this + " confined to " + myConfinement);
      } else {
        checkThread();
      }
      handle = myHandle;
    }
    if (handle != null) {
      Internal.recoverableError(this, "already opened", true);
      return;
    }
    String dbname = getSqliteDbName();
    int[] rc = {Integer.MIN_VALUE};
    handle = _SQLiteManual.sqlite3_open_v2(dbname, flags, rc);
    if (rc[0] != Result.SQLITE_OK) {
      if (handle != null) {
        try {
          _SQLiteSwigged.sqlite3_close(handle);
        } catch (Exception e) {
          // ignore
        }
      }
      String errorMessage = _SQLiteSwigged.sqlite3_errmsg(null);
      throw new SQLiteException(rc[0], errorMessage);
    }
    if (handle == null) {
      throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "sqlite didn't return db handle");
    }
    synchronized (myLock) {
      myOpenCounter++;
      myHandle = handle;
    }
    Internal.logger.info(this + " opened(" + flags + ")");
  }

  private String getSqliteDbName() {
    return myFile == null ? ":memory:" : myFile.getAbsolutePath();
  }

  int getStatementCount() {
    synchronized (myLock) {
      return myStatements.size();
    }
  }

  void checkThread() throws SQLiteException {
    Thread confinement = myConfinement;
    if (confinement == null)
      return;
    Thread thread = Thread.currentThread();
    if (thread != confinement) {
      String message = this + " confined(" + confinement + ") used(" + thread + ")";
      throw new SQLiteException(Wrapper.WRAPPER_CONFINEMENT_VIOLATED, message);
    }
  }

  public String toString() {
    return "sqlite[" + myNumber + "]";
  }

  protected void finalize() throws Throwable {
    super.finalize();
    SWIGTYPE_p_sqlite3 handle = myHandle;
    if (handle != null) {
      Internal.recoverableError(this, "wasn't closed before disposal", true);
      try {
        close();
      } catch (Throwable e) {
        // ignore
      }
    }
  }
}
