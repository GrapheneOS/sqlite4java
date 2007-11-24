package sqlite;

import sqlite.internal.*;
import static sqlite.internal.SQLiteConstants.*;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * SQLiteConnection is a single connection to sqlite database. Most methods are thread-confined,
 * and will throw errors if called from alien thread. Confinement thread is defined at the
 * time connection is open.
 * <p/>
 * SQLiteConnection should be expicitly closed before the object is disposed. Failing to do so
 * may result in unpredictable behavior from sqlite.
 * <p/>
 * Once closed with {@link #dispose()}, the connection cannot be reused and the instance
 * should be forgotten.
 * <p/>
 * SQLiteConnection tracks all statements it has prepared. When connection is disposed,
 * it first tries to dispose statements. If there's an active transaction, it is rolled
 * back.
 */
public final class SQLiteConnection {
  /**
   * The database file, or null if it is memory database.
   */
  private final File myFile;

  /**
   * An incremental number of the instance, used for debugging purposes.
   */
  private final int myNumber = Internal.nextConnectionNumber();

  /**
   * A lock for protecting statement registry & cache. Locking is needed
   * because dispose() may be called from another thread.
   */
  private final Object myLock = new Object();

  /**
   * Confinement thread, set on open() call, cleared on dispose().
   */
  private volatile Thread myConfinement;

  /**
   * SQLite db handle.
   */
  private SWIGTYPE_p_sqlite3 myHandle;

  /**
   * When connection is disposed (closed), it cannot be used anymore.
   */
  private boolean myDisposed;

  /**
   * Statement registry. All statements that are not disposed are listed here.
   */
  private final List<SQLiteStatement> myStatements = new ArrayList<SQLiteStatement>();

  /**
   * Compiled statement cache. Maps SQL string into a valid SQLite handle.
   * <p>
   * When cached handle is used, it is removed from the cache and placed into SQLiteStatement. When SQLiteStatement
   * is disposed, the handle is placed back into cache, unless there's another statement already created for the
   * same SQL. 
   */
  private final Map<String, SWIGTYPE_p_sqlite3_stmt> myStatementCache = new HashMap<String, SWIGTYPE_p_sqlite3_stmt>();

  /**
   * This controller provides service for cached statements.
   */
  private final StatementController myCachedController = new CachedStatementController();

  /**
   * This controller provides service for statements that aren't cached.
   */
  private final StatementController myUncachedController = new UncachedStatementController();

  /**
   * Create connection to database located in the specified file.
   * Database is not opened by this method.
   *
   * @param dbfile database file, or null for memory database
   */
  public SQLiteConnection(File dbfile) {
    myFile = dbfile;
    Internal.logInfo(this, "instantiated [" + myFile + "]");
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

  /**
   * @return true if connection is to the memory database
   */
  public boolean isMemoryDatabase() {
    return myFile == null;
  }

  /**
   * Opens connection, creating database if needed.
   *
   * @see #open(boolean)
   */
  public SQLiteConnection open() throws SQLiteException {
    return open(true);
  }

  /**
   * Opens connection. If connection is already open, fails gracefully, allowing process
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
   * Opens connection is read-only mode. Not applicable for in-memory database.
   */
  public SQLiteConnection openReadonly() throws SQLiteException {
    if (isMemoryDatabase()) {
      throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "cannot open memory database in read-only mode");
    }
    openX(Open.SQLITE_OPEN_READONLY);
    return this;
  }

  /**
   * Tells whether connection is open. May be called from another thread.
   */
  public boolean isOpen() {
    synchronized (myLock) {
      return myHandle != null && !myDisposed;
    }
  }

  /**
   * Tells whether the connection has been disposed. If it is disposed, nothing can be done with it.
   * @return
   */
  public boolean isDisposed() {
    synchronized (myLock) {
      return myDisposed;
    }
  }

  /**
   * Close connection and dispose all resources. May be called several times.
   * <p/>
   * This method may be called from another thread, but in that case prepared statements will not be
   * automatically disposed and will be lost by the wrapper.
   */
  public void dispose() {
    SWIGTYPE_p_sqlite3 handle;
    synchronized (myLock) {
      if (myDisposed)
        return;
      myDisposed = true;
      handle = myHandle;
      myHandle = null;
    }
    if (handle == null)
      return;
    Internal.logFine(this, "disposing");
    finalizeStatements();
    int rc = _SQLiteSwigged.sqlite3_close(handle);
    // rc may be SQLiteConstants.Result.SQLITE_BUSY if statements are open
    if (rc != SQLiteConstants.Result.SQLITE_OK) {
      String errmsg = null;
      try {
        errmsg = _SQLiteSwigged.sqlite3_errmsg(handle);
      } catch (Exception e) {
        Internal.log(Level.WARNING, this, "cannot get sqlite3_errmsg", e);
      }
      Internal.logWarn(this, "close error " + rc + (errmsg == null ? "" : ": " + errmsg));
    }
    Internal.logInfo(this, "connection closed");
    myConfinement = null;
  }

  /**
   * Execute SQL.
   */
  public SQLiteConnection exec(String sql) throws SQLiteException {
    checkThread();
    if (Internal.isFineLogging())
      Internal.logFine(this, "exec " + sql);
    String[] error = {null};
    int rc = _SQLiteManual.sqlite3_exec(handle(), sql, error);
    throwResult(rc, "exec()", error[0]);
    return this;
  }

  /**
   * Prepares cached SQL statement.
   */
  public SQLiteStatement prepare(String sql) throws SQLiteException {
    return prepare(sql, true);
  }

  /**
   * Prepares SQL statement
   * @param cached if true, the statement handle will be cached by the connection, so after the SQLiteStatement
   * is disposed, the handle may be reused by another prepare() call.
   */
  public SQLiteStatement prepare(String sql, boolean cached) throws SQLiteException {
    checkThread();
    if (Internal.isFineLogging())
      Internal.logFine(this, "prepare " + sql);
    SWIGTYPE_p_sqlite3 handle;
    SWIGTYPE_p_sqlite3_stmt stmt = null;
    int openCounter;
    synchronized (myLock) {
      if (cached) {
        // while the statement is in work, it is removed from cache. it is put back in cache by SQLiteStatement.dispose().
        stmt = myStatementCache.remove(sql);
      }
      handle = handle();
    }
    if (stmt == null) {
      if (Internal.isFineLogging())
        Internal.logFine(this, "calling sqlite3_prepare_v2 for " + sql);
      int[] rc = {Integer.MIN_VALUE};
      stmt = _SQLiteManual.sqlite3_prepare_v2(handle, sql, rc);
      throwResult(rc[0], "prepare()", sql);
      if (stmt == null)
        throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "sqlite did not return stmt");
    } else {
      if (Internal.isFineLogging())
        Internal.logFine(this, "using cached stmt for " + sql);
    }
    SQLiteStatement statement = null;
    synchronized (myLock) {
      // the connection may close while prepare in progress
      // most probably that would throw SQLiteException earlier, but we'll check anyway
      if (myHandle != null) {
        StatementController controller = cached ? myCachedController : myUncachedController;
        statement = new SQLiteStatement(controller, stmt, sql);
        myStatements.add(statement);
      } else {
        Internal.logWarn(this, "connection disposed while preparing statement for " + sql);
      }
    }
    if (statement == null) {
      // connection closed
      try {
        throwResult(_SQLiteSwigged.sqlite3_finalize(stmt), "finalize() in prepare()");
      } catch (Exception e) {
        // ignore
      }
      throw new SQLiteException(Wrapper.WRAPPER_NOT_OPENED, "connection disposed");
    }
    return statement;
  }

  private void finalizeStatements() {
    boolean alienThread = myConfinement != Thread.currentThread();
    if (!alienThread) {
      Internal.logFine(this, "finalizing statements");
      while (true) {
        SQLiteStatement[] statements = null;
        synchronized (myLock) {
          if (myStatements.isEmpty())
            break;
          statements = myStatements.toArray(new SQLiteStatement[myStatements.size()]);
        }
        for (SQLiteStatement statement : statements) {
          finalizeStatement(statement);
        }
      }
      while (true) {
        SWIGTYPE_p_sqlite3_stmt stmt = null;
        String sql = null;
        synchronized (myLock) {
          if (myStatementCache.isEmpty())
            break;
          Map.Entry<String, SWIGTYPE_p_sqlite3_stmt> e = myStatementCache.entrySet().iterator().next();
          sql = e.getKey();
          stmt = e.getValue();
        }
        finalizeStatement(stmt, sql);
      }
    }
    synchronized (myLock) {
      if (!myStatements.isEmpty() || !myStatementCache.isEmpty()) {
        int count = myStatements.size() + myStatementCache.size();
        if (alienThread) {
          Internal.logWarn(this, "cannot finalize " + count + " statements from alien thread");
        } else {
          Internal.recoverableError(this, count + " statements are not finalized", false);
        }
      }
      myStatements.clear();
      myStatementCache.clear();
    }
  }

  private void finalizeStatement(SWIGTYPE_p_sqlite3_stmt handle, String sql) {
    if (Internal.isFineLogging())
      Internal.logFine(this, "finalizing cached stmt for " + sql);
    softFinalize(handle, sql, sql);
    synchronized (myLock) {
      forgetCachedHandle(handle, sql);
    }
  }

  private void finalizeStatement(SQLiteStatement statement) {
    Internal.logFine(statement, "finalizing");
    SWIGTYPE_p_sqlite3_stmt handle = statement.statementHandle();
    String sql = statement.getSql();
    statement.clear();
    softFinalize(handle, sql, statement);
    synchronized (myLock) {
      forgetStatement(statement);
      forgetCachedHandle(handle, sql);
    }
  }

  private void softFinalize(SWIGTYPE_p_sqlite3_stmt handle, String sql, Object source) {
    int rc = _SQLiteSwigged.sqlite3_finalize(handle);
    if (rc != Result.SQLITE_OK) {
      Internal.logWarn(this, "error [" + rc + "] finishing " + source);
    }
  }

  /**
   * Called from {@link SQLiteStatement#dispose()}
   */
  private void cacheStatementHandle(SQLiteStatement statement) {
    if (Internal.isFineLogging())
      Internal.logFine(statement, "returning handle to cache");
    boolean finalize = false;
    SWIGTYPE_p_sqlite3_stmt handle = statement.statementHandle();
    String sql = statement.getSql();
    try {
      if (statement.hasStepped()) {
        int rc = _SQLiteSwigged.sqlite3_reset(handle);
        throwResult(rc, "reset");
      }
      if (statement.hasBindings()) {
        int rc = _SQLiteSwigged.sqlite3_clear_bindings(handle);
        throwResult(rc, "clearBindings");
      }
    } catch (SQLiteException e) {
      Internal.log(Level.WARNING, statement, "exception when clearing", e);
      finalize = true;
    }
    synchronized (myLock) {
      if (!finalize) {
        SWIGTYPE_p_sqlite3_stmt expunged = myStatementCache.put(sql, handle);
        if (expunged != null) {
          if (expunged == handle) {
            Internal.recoverableError(statement, "handle appeared in cache when inserted", true);
          } else {
            // put it back
            if (Internal.isFineLogging()) {
              Internal.logFine(statement, "second cached copy for [" + sql + "] prevails");
            }
            myStatementCache.put(sql, expunged);
            finalize = true;
          }
        }
      }
      forgetStatement(statement);
    }
    if (finalize) {
      Internal.logFine(statement, "cache don't need me, finalizing");
      finalizeStatement(handle, sql);
    }
  }

  private void forgetCachedHandle(SWIGTYPE_p_sqlite3_stmt handle, String sql) {
    assert Thread.holdsLock(myLock);
    SWIGTYPE_p_sqlite3_stmt removedHandle = myStatementCache.remove(sql);
    if (removedHandle != null && removedHandle != handle) {
      // put it back
      myStatementCache.put(sql, removedHandle);
    }
  }

  private void forgetStatement(SQLiteStatement statement) {
    assert Thread.holdsLock(myLock);
    boolean removed = myStatements.remove(statement);
    if (!removed) {
      Internal.recoverableError(statement, "alien statement", true);
    }
  }

  private SWIGTYPE_p_sqlite3 handle() throws SQLiteException {
    synchronized (myLock) {
      if (myDisposed)
        throw new SQLiteException(Wrapper.WRAPPER_MISUSE, "connection is disposed");
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
          Internal.log(Level.WARNING, this, "cannot get sqlite3_errmsg", e);
        }
      }
      throw new SQLiteException(resultCode, message);
    }
  }

  private void openX(int flags) throws SQLiteException {
    SQLite.loadLibrary();
    if (Internal.isFineLogging())
      Internal.logFine(this, "opening (0x" + Integer.toHexString(flags).toUpperCase(Locale.US) + ")");
    SWIGTYPE_p_sqlite3 handle;
    synchronized (myLock) {
      if (myDisposed) {
        throw new SQLiteException(Wrapper.WRAPPER_MISUSE, "cannot reopen closed connection");
      }
      if (myConfinement == null) {
        myConfinement = Thread.currentThread();
        if (Internal.isFineLogging())
          Internal.logFine(this, " confined to " + myConfinement);
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
    if (Internal.isFineLogging())
      Internal.logFine(this, "dbname [" + dbname + "]");
    int[] rc = {Integer.MIN_VALUE};
    handle = _SQLiteManual.sqlite3_open_v2(dbname, flags, rc);
    if (rc[0] != Result.SQLITE_OK) {
      if (handle != null) {
        if (Internal.isFineLogging())
          Internal.logFine(this, "error on open (" + rc[0] + "), closing handle");
        try {
          _SQLiteSwigged.sqlite3_close(handle);
        } catch (Exception e) {
          Internal.log(Level.FINE, this, "error on closing after failed open", e);
        }
      }
      String errorMessage = _SQLiteSwigged.sqlite3_errmsg(null);
      throw new SQLiteException(rc[0], errorMessage);
    }
    if (handle == null) {
      throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "sqlite didn't return db handle");
    }
    synchronized (myLock) {
      myHandle = handle;
    }
    Internal.logInfo(this, "opened");
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
    boolean disposed = myDisposed;
    if (handle != null || !disposed) {
      Internal.recoverableError(this, "wasn't disposed before finalizing", true);
      try {
        dispose();
      } catch (Throwable e) {
        // ignore
      }
    }
  }

  SWIGTYPE_p_sqlite3 connectionHandle() {
    return myHandle;
  }

  private abstract class BaseStatementController implements StatementController {
    public void validate() throws SQLiteException {
      SQLiteConnection.this.checkThread();
      SQLiteConnection.this.handle();
    }

    public void throwResult(int resultCode, String message, Object additionalMessage) throws SQLiteException {
      SQLiteConnection.this.throwResult(resultCode, message, additionalMessage);
    }

    protected boolean checkDispose(SQLiteStatement statement) {
      try {
        SQLiteConnection.this.checkThread();
      } catch (SQLiteException e) {
        Internal.recoverableError(this, "disposing " + statement + " from alien thread", true);
        return false;
      }
      return true;
    }
  }

  private class CachedStatementController extends BaseStatementController {
    public void dispose(SQLiteStatement statement) {
      if (checkDispose(statement)) {
        SQLiteConnection.this.cacheStatementHandle(statement);
      }
    }

    public String toString() {
      return SQLiteConnection.this.toString() + "[C]";
    }
  }

  private class UncachedStatementController extends BaseStatementController {
    public void dispose(SQLiteStatement statement) {
      if (checkDispose(statement)) {
        SQLiteConnection.this.finalizeStatement(statement);
      }
    }

    public String toString() {
      return SQLiteConnection.this.toString() + "[U]";
    }
  }
}
