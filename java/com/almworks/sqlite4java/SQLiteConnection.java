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

import javolution.util.stripped.FastMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import static com.almworks.sqlite4java.SQLiteConstants.*;

/**
 * SQLiteConnection is a single connection to sqlite database. It wraps the <strong><code>sqlite3*</code></strong>
 * database handle from SQLite C Interface.
 * <p/>
 * Unless otherwise specified, methods are confined to the thread that was used to open the connection.
 * This means that an exception will be thrown if you try to access the method from a different thread.
 * <p/>
 * SQLiteConnection should be expicitly closed before the object is disposed. Failing to do so
 * may result in unpredictable behavior from SQLite.
 * <p/>
 * Once closed with {@link #dispose()}, the connection cannot be reused and the instance
 * should be forgotten.
 * <p/>
 * Several connections to the same database can be opened by creating several instances of SQLiteConnection.
 * <p/>
 * SQLiteConnection tracks all statements it had prepared. When connection is disposed,
 * it first tries to dispose all prepared statements. If there's an active transaction, it is rolled
 * back.
 *
 * @see SQLiteStatement
 * @see <a href="http://www.sqlite.org/c3ref/sqlite3.html">sqlite3*</a>
 */
public final class SQLiteConnection {
  private static final int MAX_POOLED_DIRECT_BUFFER_SIZE = 1 << 20;
  private static final int DEFAULT_STEPS_PER_CALLBACK = 1;

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
  private final ArrayList<SQLiteStatement> myStatements = new ArrayList<SQLiteStatement>(100);

  /**
   * Statement registry. All statements that are not disposed are listed here.
   */
  private final ArrayList<SQLiteBlob> myBlobs = new ArrayList<SQLiteBlob>(10);

  /**
   * Allocated buffers pool. Sorted by pool size.
   * todo pool size control
   */
  private final ArrayList<DirectBuffer> myBuffers = new ArrayList<DirectBuffer>(10);

  /**
   * Sum of myBuffer sizes
   */
  private int myBuffersTotalSize;

  /**
   * Compiled statement cache. Maps SQL string into a valid SQLite handle.
   * <p/>
   * When cached handle is used, it is removed from the cache and placed into SQLiteStatement. When SQLiteStatement
   * is disposed, the handle is placed back into cache, unless there's another statement already created for the
   * same SQL.
   */
  private final FastMap<SQLParts, SWIGTYPE_p_sqlite3_stmt> myStatementCache = new FastMap<SQLParts, SWIGTYPE_p_sqlite3_stmt>();

  /**
   * This controller provides service for cached statements.
   */
  private final SQLiteController myCachedController = new CachedController();

  /**
   * This controller provides service for statements that aren't cached.
   */
  private final SQLiteController myUncachedController = new UncachedController();

  /**
   * This object contains several variables that assist in calling native methods and allow to avoid
   * unnecessary memory allocation.
   */
  private final _SQLiteManual mySQLiteManual = new _SQLiteManual();

  /**
   * Native byte buffer to communicate between Java and SQLite to report progress and cancel execution.
   */
  private ProgressHandler myProgressHandler;

  /**
   * May be set only before first exec() or step().
   */
  private volatile int myStepsPerCallback = DEFAULT_STEPS_PER_CALLBACK;

  /**
   * If initialized, all subsequent statements are analyzed for speed and stats are
   * collected in the profiler.
   */
  private volatile SQLiteProfiler myProfiler;

  /**
   * Contains inactive (initialized, but not in use) long arrays, mapped by the name.
   */
  private final FastMap<String, SWIGTYPE_p_intarray> myLongArrays = FastMap.newInstance();

  /**
   * Incremental number used in generation of long array names.
   */
  private int myLongArrayCounter;

  /**
   * Creates a connection to the database located in the specified file.
   * Database is not opened by the constructor, and the calling thread is insignificant.
   *
   * @param dbfile database file, or null to create an in-memory database
   */
  public SQLiteConnection(File dbfile) {
    myFile = dbfile;
    Internal.logInfo(this, "instantiated [" + myFile + "]");
  }

  /**
   * Creates a connection to an in-memory temporary database.
   * Database is not opened by the constructor, and the calling thread is insignificant.
   *
   * @see #SQLiteConnection(java.io.File)
   */
  public SQLiteConnection() {
    this(null);
  }

  /**
   * Returns the database file. This method is <strong>thread-safe</strong>.
   *
   * @return the file that hosts the database, or null if database is in memory
   */
  public File getDatabaseFile() {
    return myFile;
  }

  /**
   * Checks whether this connection is to an in-memory database. This method is <strong>thread-safe</strong>.
   *
   * @return true if the connection is to the memory database
   */
  public boolean isMemoryDatabase() {
    return myFile == null;
  }

  /**
   * Sets the frequency of database callbacks during long-running SQL statements. Database callbacks
   * are currently used to check if the statement has been cancelled.
   * <p/>
   * This method is <strong>partially thread-safe</strong>: it may be called from a non-confining thread
   * before connection is opened. After connection is opened, is should be called from the confining thread and
   * before any statement has been executed.
   * <p/>
   *
   * @param stepsPerCallback the number of internal SQLite cycles in between calls to the progress callback (default 1)
   * @see <a href="http://www.sqlite.org/c3ref/progress_handler.html">sqlite3_progress_callback</a>
   */
  public void setStepsPerCallback(int stepsPerCallback) {
    try {
      checkThread();
      if (stepsPerCallback > 0) {
        myStepsPerCallback = stepsPerCallback;
      }
    } catch (SQLiteException e) {
      Internal.logWarn(this, "call to setStepsPerCallback is ignored");
    }
  }

  /**
   * Opens the connection, optionally creating the database.
   * <p/>
   * If connection is already open, fails gracefully, allowing connection can be used further.
   * <p/>
   * This method "confines" the connection to the thread in which it has been called. Most of the following
   * method calls to this connection and to its statements should be made only from that thread, otherwise
   * an exception is thrown.
   * <p/>
   * If allowCreate parameter is false, and database file does not exist, method fails with an exception.
   * <p/>
   *
   * @param allowCreate if true, database file may be created. For an in-memory database, this parameter must
   *                    be true.
   * @return this connection
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/open.html">sqlite3_open_v2</a>
   */
  public SQLiteConnection open(boolean allowCreate) throws SQLiteException {
    int flags = SQLITE_OPEN_READWRITE;
    if (!allowCreate) {
      if (isMemoryDatabase()) {
        throw new SQLiteException(WRAPPER_WEIRD, "cannot open memory database without creation");
      }
    } else {
      flags |= SQLITE_OPEN_CREATE;
    }
    open0(flags);
    return this;
  }

  /**
   * Opens the connection, creating database if needed. See {@link #open(boolean)} for a full description.
   *
   * @return this connection
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   */
  public SQLiteConnection open() throws SQLiteException {
    return open(true);
  }

  /**
   * Opens the connection is read-only mode. Not applicable for an in-memory database.
   * See {@link #open(boolean)} for a full description.
   *
   * @return this connection
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   */
  public SQLiteConnection openReadonly() throws SQLiteException {
    if (isMemoryDatabase()) {
      throw new SQLiteException(WRAPPER_WEIRD, "cannot open memory database in read-only mode");
    }
    open0(SQLITE_OPEN_READONLY);
    return this;
  }

  /**
   * Opens the connection with the specified flags for the sqlite3_open_v2 method. The flags SQLITE_OPEN_xxx are defined
   * in {@link SQLiteConstants} and can be ORed together.
   * <p/>
   * This method is provided for future versions compatibility and for open options not otherwise supported by
   * sqlite4java. Use this method only if other open() methods are not sufficient.
   * <p/>
   * In all other respects, this method works exactly like {@link #open(boolean)}, consult documentation to that method
   * for details.
   *
   * @param flags integer flags as defined by sqlite3_open_v2 function
   * @return this connection
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/open.html">sqlite3_open_v2</a>
   */
  public SQLiteConnection openV2(int flags) throws SQLiteException {
    open0(flags);
    return this;
  }

  /**
   * Tells whether connection is open. This method is <strong>thread-safe</strong>.
   *
   * @return true if this connection was successfully opened and has not been disposed
   */
  public boolean isOpen() {
    synchronized (myLock) {
      return myHandle != null && !myDisposed;
    }
  }

  /**
   * Checks if the connection has been disposed. This method is <strong>thread-safe</strong>.
   *
   * @return true if this connection has been disposed. Disposed connections can't be used for anything.
   */
  public boolean isDisposed() {
    synchronized (myLock) {
      return myDisposed;
    }
  }

  /**
   * Closes this connection and disposes all related resources. After dispose() is called, the connection
   * cannot be used and the instance should be forgotten.
   * <p/>
   * Calling this method on an already disposed connection does nothing.
   * <p/>
   * This method is <strong>partially thread-safe</strong>: it may be called from another thread,
   * but in that case prepared statements will not be disposed and will be "lost" by the wrapper.
   * This will probably be ok, but SQLite may return an error and not close the connection.
   * <p/>
   * It is better to call dispose() from a different thread, than not to call it at all.
   * <p/>
   * This method does not throw exception even if SQLite returns an error.
   * <p/>
   *
   * @see <a href="http://www.sqlite.org/c3ref/close.html">sqlite3_close</a>
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
    finalizeBlobs();
    finalizeBuffers();
    finalizeProgressHandler(handle);
    int rc = _SQLiteSwigged.sqlite3_close(handle);
    // rc may be SQLiteConstants.Result.SQLITE_BUSY if statements are open
    if (rc != SQLiteConstants.SQLITE_OK) {
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
   * Executes SQL. This method is normally used for DDL, transaction control and similar SQL statements.
   * For querying database and for DML statements with parameters, use {@link #prepare}.
   * <p/>
   * Several statements, delimited by a semicolon, can be executed with a single call.
   * <p/>
   * Do not use this method if your SQL contains non-ASCII characters!
   * <p/>
   *
   * @param sql an SQL statement
   * @return this connection
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/exec.html">sqlite3_exec</a>
   */
  public SQLiteConnection exec(String sql) throws SQLiteException {
    checkThread();
    SQLiteProfiler profiler = myProfiler;
    if (Internal.isFineLogging())
      Internal.logFine(this, "exec [" + sql + "]");
    SWIGTYPE_p_sqlite3 handle = handle();
    ProgressHandler ph = getProgressHandler();
    ph.reset();
    try {
      String[] error = {null};
      long from = profiler == null ? 0 : System.nanoTime();
      int rc = _SQLiteManual.sqlite3_exec(handle, sql, error);
      if (profiler != null) profiler.reportExec(sql, from, System.nanoTime(), rc);
      throwResult(rc, "exec()", error[0]);
    } finally {
      if (Internal.isFineLogging())
        Internal.logFine(this, "exec [" + sql + "]: " + ph.getSteps() + " steps");
      ph.reset();
    }
    return this;
  }

  /**
   * Prepares an SQL statement. Prepared SQL statement can be used further for putting data into
   * the database and for querying data.
   * <p/>
   * Prepared statements are normally cached by the connection, unless you set <code>cached</code> parameter
   * to false. Because parsing SQL is costly, caching should be used in cases where a single SQL can be
   * executed more than once.
   * <p/>
   * Cached statements are cleared of state before or after they are used.
   * <p/>
   * SQLParts is used to contains the SQL query, yet there are convenience methods that accept String.
   * <p/>
   * Returned statement must be disposed when the calling code is done with it, whether it was cached or not.
   * <p/>
   *
   * @param sql    the SQL statement, not null
   * @param cached if true, the statement handle will be cached by the connection
   * @return an instance of {@link SQLiteStatement}
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/prepare.html">sqlite3_prepare_v2</a>
   */
  public SQLiteStatement prepare(SQLParts sql, boolean cached) throws SQLiteException {
    checkThread();
    SQLiteProfiler profiler = myProfiler;
    if (Internal.isFineLogging())
      Internal.logFine(this, "prepare [" + sql + "]");
    if (sql == null)
      throw new IllegalArgumentException();
    SWIGTYPE_p_sqlite3 handle;
    SWIGTYPE_p_sqlite3_stmt stmt = null;
    SQLParts fixedKey = null;
    int openCounter;
    synchronized (myLock) {
      if (cached) {
        // while the statement is in work, it is removed from cache. it is put back in cache by SQLiteStatement.dispose().
        FastMap.Entry<SQLParts, SWIGTYPE_p_sqlite3_stmt> e = myStatementCache.getEntry(sql);
        if (e != null) {
          fixedKey = e.getKey();
          assert fixedKey != null;
          assert fixedKey.isFixed() : sql;
          stmt = e.getValue();
          if (stmt != null) {
            e.setValue(null);
          }
        }
      }
      handle = handle();
    }
    if (stmt == null) {
      if (Internal.isFineLogging())
        Internal.logFine(this, "calling sqlite3_prepare_v2 for [" + sql + "]");
      long from = profiler == null ? 0 : System.nanoTime();
      String sqlString = sql.toString();
      if (sqlString.trim().length() == 0)
        throw new SQLiteException(WRAPPER_USER_ERROR, "empty SQL");
      stmt = mySQLiteManual.sqlite3_prepare_v2(handle, sqlString);
      int rc = mySQLiteManual.getLastReturnCode();
      if (profiler != null) profiler.reportPrepare(sqlString, from, System.nanoTime(), rc);
      throwResult(rc, "prepare()", sql);
      if (stmt == null)
        throw new SQLiteException(WRAPPER_WEIRD, "sqlite did not return stmt");
    } else {
      if (Internal.isFineLogging())
        Internal.logFine(this, "using cached stmt for [" + sql + "]");
    }
    SQLiteStatement statement = null;
    synchronized (myLock) {
      // the connection may close while prepare in progress
      // most probably that would throw SQLiteException earlier, but we'll check anyway
      if (myHandle != null) {
        SQLiteController controller = cached ? myCachedController : myUncachedController;
        if (fixedKey == null)
          fixedKey = sql.getFixedParts();
        statement = new SQLiteStatement(controller, stmt, fixedKey, myProfiler);
        myStatements.add(statement);
      } else {
        Internal.logWarn(this, "connection disposed while preparing statement for [" + sql + "]");
      }
    }
    if (statement == null) {
      // connection closed
      try {
        throwResult(_SQLiteSwigged.sqlite3_finalize(stmt), "finalize() in prepare()");
      } catch (Exception e) {
        // ignore
      }
      throw new SQLiteException(WRAPPER_NOT_OPENED, "connection disposed");
    }
    return statement;
  }

  /**
   * Convenience method that prepares a cached statement for the given SQL. See {@link #prepare(SQLParts, boolean)}
   * for details.
   *
   * @param sql an SQL statement, not null
   * @return an instance of {@link SQLiteStatement}
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   */
  public SQLiteStatement prepare(String sql) throws SQLiteException {
    return prepare(sql, true);
  }

  /**
   * Convenience method that prepares a statement for the given String-based SQL. See {@link #prepare(SQLParts, boolean)}
   * for details.
   *
   * @param sql    the SQL statement, not null
   * @param cached if true, the statement handle will be cached by the connection
   * @return an instance of {@link SQLiteStatement}
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   */
  public SQLiteStatement prepare(String sql, boolean cached) throws SQLiteException {
    return prepare(new SQLParts(sql), cached);
  }

  /**
   * Convenience method that prepares a cached statement for the given SQL. See {@link #prepare(SQLParts, boolean)}
   * for details.
   *
   * @param sql the SQL statement, not null
   * @return an instance of {@link SQLiteStatement}
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   */
  public SQLiteStatement prepare(SQLParts sql) throws SQLiteException {
    return prepare(sql, true);
  }

  /**
   * Opens a BLOB for reading or writing. This method returns an instance of {@link SQLiteBlob}, which can
   * be used to read or write a single table cell with a BLOB value. After operations are done, the blob should
   * be disposed.
   * <p/>
   * See SQLite documentation about transactional behavior of the corresponding methods.
   * <p/>
   *
   * @param dbname      database name, or null for the current database
   * @param table       table name, not null
   * @param column      column name, not null
   * @param rowid       row id
   * @param writeAccess if true, write access is requested
   * @return an instance of SQLiteBlob for incremental reading or writing
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/blob_open.html">sqlite3_blob_open</a>
   */
  public SQLiteBlob blob(String dbname, String table, String column, long rowid, boolean writeAccess) throws SQLiteException {
    checkThread();
    if (Internal.isFineLogging())
      Internal.logFine(this, "openBlob [" + dbname + "," + table + "," + column + "," + rowid + "," + writeAccess + "]");
    SWIGTYPE_p_sqlite3 handle = handle();
    SWIGTYPE_p_sqlite3_blob blob = mySQLiteManual.sqlite3_blob_open(handle, dbname, table, column, rowid, writeAccess);
    throwResult(mySQLiteManual.getLastReturnCode(), "openBlob()", null);
    if (blob == null)
      throw new SQLiteException(WRAPPER_WEIRD, "sqlite did not return blob");
    SQLiteBlob result = null;
    synchronized (myLock) {
      // the connection may close while openBlob in progress
      // most probably that would throw SQLiteException earlier, but we'll check anyway
      if (myHandle != null) {
        result = new SQLiteBlob(myUncachedController, blob, dbname, table, column, rowid, writeAccess);
        myBlobs.add(result);
      } else {
        Internal.logWarn(this, "connection disposed while opening blob");
      }
    }
    if (result == null) {
      // connection closed
      try {
        throwResult(_SQLiteSwigged.sqlite3_blob_close(blob), "blob_close() in prepare()");
      } catch (Exception e) {
        // ignore
      }
      throw new SQLiteException(WRAPPER_NOT_OPENED, "connection disposed");
    }
    return result;
  }

  /**
   * Convenience method for calling blob() on the currently selected database.
   * See {@link #blob(String, String, String, long, boolean)} for detailed description.
   *
   * @param table       table name, not null
   * @param column      column name, not null
   * @param rowid       row id
   * @param writeAccess if true, write access is requested
   * @return an instance of SQLiteBlob for incremental reading or writing
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   */
  public SQLiteBlob blob(String table, String column, long rowid, boolean writeAccess) throws SQLiteException {
    return blob(null, table, column, rowid, writeAccess);
  }

  /**
   * Sets "busy timeout" for this connection. If timeout is defined, then SQLite will not wait to lock
   * the database for more than the specified number of milliseconds.
   * <p/>
   * By default, the timeout is not set.
   *
   * @param millis number of milliseconds for the busy timeout, or 0 to disable the timeout
   * @return this connection
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/busy_timeout.html">sqlite3_busy_timeout</a>
   */
  public SQLiteConnection setBusyTimeout(long millis) throws SQLiteException {
    checkThread();
    int rc = _SQLiteSwigged.sqlite3_busy_timeout(handle(), (int) millis);
    throwResult(rc, "setBusyTimeout");
    return this;
  }

  /**
   * Checks if the database is in the auto-commit mode. In auto-commit mode, transaction is ended after execution of
   * every statement.
   * <p/>
   *
   * @return true if the connection is in auto-commit mode
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/get_autocommit.html">sqlite3_get_autocommit</a>
   */
  public boolean getAutoCommit() throws SQLiteException {
    checkThread();
    int r = _SQLiteSwigged.sqlite3_get_autocommit(handle());
    return r != 0;
  }

  /**
   * Returns the ROWID of the row, last inserted in this connection (regardless of which statement was used).
   * If the table has a column of type INTEGER PRIMARY KEY, then that column contains the ROWID. See SQLite docs.
   * <p/>
   * You can also use "last_insert_rowid()" function in SQL statements following the insert.
   *
   * @return the rowid of the last successful insert, or 0 if nothing has been inserted in this connection
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/last_insert_rowid.html">sqlite3_last_insert_rowid</a>
   */
  public long getLastInsertId() throws SQLiteException {
    checkThread();
    long id = _SQLiteSwigged.sqlite3_last_insert_rowid(handle());
    return id;
  }

  /**
   * This method returns the number of database rows that were changed or inserted or deleted by the most
   * recently completed SQL statement in this connection. See SQLite documentation for details.
   *
   * @return the number of affected rows, or 0
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/changes.html">sqlite3_changes</a>
   */
  public int getChanges() throws SQLiteException {
    checkThread();
    return _SQLiteSwigged.sqlite3_changes(handle());
  }

  /**
   * This method returns the total number of database rows that were changed or inserted or deleted since
   * this connection was opened. See SQLite documentation for details.
   *
   * @return the total number of affected rows, or 0
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/total_changes.html">sqlite3_total_changes</a>
   */
  public int getTotalChanges() throws SQLiteException {
    checkThread();
    return _SQLiteSwigged.sqlite3_total_changes(handle());
  }

  /**
   * This method can be called to interrupt a currently long-running SQL statement, causing it to fail
   * with an exception.
   * <p/>
   * This method is <strong>thread-safe</strong>.
   * <p/>
   * There are some important implications from using this method, see SQLite docs.
   *
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/interrupt.html">sqlite3_interrupt</a>
   */
  public void interrupt() throws SQLiteException {
    _SQLiteSwigged.sqlite3_interrupt(handle());
  }

  /**
   * This method returns the error code of the most recently failed operation. However, this method is
   * rarely needed, as the error code can usually be received from {@link SQLiteException#getErrorCode}.
   *
   * @return error code, or 0
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/errcode.html">sqlite3_errcode</a>
   * @see <a href="http://www.sqlite.org/c3ref/errcode.html">sqlite3_extended_errcode</a>
   */
  public int getErrorCode() throws SQLiteException {
    checkThread();
    return _SQLiteSwigged.sqlite3_errcode(handle());
  }

  /**
   * This method returns the English error message that describes the error returned by {@link #getErrorCode}.
   *
   * @return error message, or null
   * @throws SQLiteException if SQLite returns an error, or if the call violates the contract of this class
   * @see <a href="http://www.sqlite.org/c3ref/errcode.html">sqlite3_errmsg</a>
   */
  public String getErrorMessage() throws SQLiteException {
    checkThread();
    return _SQLiteSwigged.sqlite3_errmsg(handle());
  }

  /**
   * Starts SQL profiling and returns the profiler class. If profiling is already started, returns the
   * profiler.
   * <p/>
   * This method is thread-safe, in a sense that it can be called from non-session threads. It's not
   * strongly synchronized, so calling it concurrently may result in duplicate profilers.
   * <p/>
   * Only instances of SQLiteStatement created after this method is called will be profiled (whether
   * SQLite statement is cached or not).
   *
   * @return the profiler, which will collect stats for all subsequent operations until {@link #stopProfiling}
   *         is called.
   */
  public SQLiteProfiler profile() {
    SQLiteProfiler profiler = myProfiler;
    if (profiler == null)
      myProfiler = profiler = new SQLiteProfiler();
    return profiler;
  }

  /**
   * Stops the profiling and returns the profiler instance with data. If the profiling was not running,
   * returns null.
   * <p/>
   * This method is thread-safe, in a sense that it can be called from non-session threads. It's not
   * strongly synchronized, so calling it concurrently may result in race conditions.
   *
   * @return the profiler with collected data, or null
   */
  public SQLiteProfiler stopProfiling() {
    SQLiteProfiler profiler = myProfiler;
    myProfiler = null;
    return profiler;
  }

  /**
   * Creates a virtual table within the current session, to represent an array of long values (functionality provided
   * by test_intarray module from SQLite sources). After SQLiteLongArray
   * is created, it can be bound consequently several times to a long[], and the virtual table can be used in any SQL.
   * This provides means to make queries with array parameters. For example:
   * <pre>
   * long[] itemIds = ...;
   * SQLiteLongArray array = connection.createArray();
   * SQLiteStatement st = connection.prepare("SELECT * FROM items WHERE itemId IN " + array.getName());
   * array.bind(itemIds);
   * while (st.step()) {
   *   // read values
   * }
   * st.dispose();
   * array.dispose();
   * <p/>
   * The array contents is bound using {@link SQLiteLongArray#bind} methods. Binding an array is not a transactional
   * operation; it does not start or stop a transaction, and contents of the array is not affected by ROLLBACK.
   * <p/>
   * You can execute any SQL using array's name ({@link SQLiteLongArray#getName}) as the table name. The actual table
   * is a VIRTUAL TABLE, residing in TEMP database. (Because of that, temp database may be created on disk - you can
   * change that using PRAGMA TEMP_STORE.)
   * <p/>
   * It is possible to execute an SQL that contains several virtual array tables.
   * <p/>
   * Note that the virtual array table does not have a primary key (bound values may have duplicates and come
   * in random order), so be careful about performance.
   * <p/>
   * SQLiteLongArray are cached by the SQLiteConnection, unless <code>cached</code> parameter is set to <code>false</code>.
   * When cached SQLiteLongArray is disposed, it is kept by the connection for further reuse. When a non-cached
   * SQLiteLongArray is disposed, its table is deleted from the TEMP database.
   * <p/>
   * <strong>Caution:</strong> It's possible to use DROP TABLE on the array virtual table; doing that will make
   * SQL statements that use the table invalid.
   *
   * @param name   the name of the table, must be a correct SQL table name, and contains only ASCII characters. If null,
   *               a temporary name will be provided automatically (can be later retrieved via {@link SQLiteLongArray#getName}.
   * @param cached if true, then a cached array will be used, thus reducing the number of virtual tables and schema
   *               changes. If cached is true and a name is given and there's no free array with that name, a new array will be created.
   *               If cached is true and name is null, then any free array will be allocated.
   * @return an instance of SQLiteLongArray, wrapping an empty (unbound) virtual array table
   * @throws SQLiteException if name is already in use, or if other problem happens
   * @see <a href="http://www.sqlite.org/src/artifact/489edb9068bb926583445cb02589344961054207">test_intarray.h</a>
   */
  public SQLiteLongArray createArray(String name, boolean cached) throws SQLiteException {
    checkThread();
    if (Internal.isFineLogging())
      Internal.logFine(this, "createArray [" + name + "," + cached + "]");
    if (!cached && name != null && myLongArrays.containsKey(name)) {
      Internal.logWarn(this, "using cached array in lieu of passed parameter, because name already in use");
      cached = true;
    }
    if (!cached) {
      return createArray0(name, myUncachedController);
    }
    if (name == null && !myLongArrays.isEmpty()) {
      name = myLongArrays.head().getNext().getKey();
    }
    SWIGTYPE_p_intarray array = name == null ? null : myLongArrays.remove(name);
    if (array != null) {
      return new SQLiteLongArray(myCachedController, array, name);
    }
    return createArray0(name, myCachedController);
  }

  /**
   * Creates a virtual table within the current session, to represent an array of long values (functionality provided
   * by test_intarray module from SQLite sources). After SQLiteLongArray
   * is created, it can be bound consequently several times to a long[], and the virtual table can be used in any SQL.
   * <p/>
   * This is a convenience method that creates an array with an arbitrary name and cached by the connection,
   * equal to call to <code>createArray(null, true)</code>. See {@link #createArray(String, boolean)} for details.
   *
   * @return an instance of SQLiteLongArray, wrapping an empty (unbound) virtual array table
   * @throws SQLiteException in case any problem is reported by SQLite, or general contract is broken
   */
  public SQLiteLongArray createArray() throws SQLiteException {
    return createArray(null, true);
  }

  private SQLiteLongArray createArray0(String name, SQLiteController controller) throws SQLiteException {
    SWIGTYPE_p_sqlite3 handle = handle();
    if (name == null)
      name = nextArrayName();
    SWIGTYPE_p_intarray r = mySQLiteManual.sqlite3_intarray_create(handle, name);
    int rc = mySQLiteManual.getLastReturnCode();
    throwResult(rc, "createArray()", name);
    if (r == null) {
      throwResult(SQLiteConstants.WRAPPER_WEIRD, "createArray()", name);
    }
    return new SQLiteLongArray(controller, r, name);
  }

  private String nextArrayName() {
    return String.format("__IA%02X", ++myLongArrayCounter);
  }

  private void finalizeProgressHandler(SWIGTYPE_p_sqlite3 handle) {
    if (Thread.currentThread() == myConfinement) {
      ProgressHandler handler = myProgressHandler;
      if (handler != null) {
        _SQLiteManual.uninstall_progress_handler(handle, handler);
      }
    }
  }

  private void finalizeBuffers() {
    DirectBuffer[] buffers;
    synchronized (myLock) {
      if (myBuffers.isEmpty()) {
        return;
      }
      buffers = myBuffers.toArray(new DirectBuffer[myBuffers.size()]);
      myBuffers.clear();
      myBuffersTotalSize = 0;
    }
    if (Thread.currentThread() == myConfinement) {
      for (DirectBuffer buffer : buffers) {
        _SQLiteManual.wrapper_free(buffer);
      }
    } else {
      Internal.logWarn(this, "cannot free " + buffers.length + " buffers from alien thread (" + Thread.currentThread() + ")");
    }
  }

  private ProgressHandler getProgressHandler() throws SQLiteException {
    ProgressHandler handler = myProgressHandler;
    if (handler == null) {
      handler = mySQLiteManual.install_progress_handler(handle(), myStepsPerCallback);
      if (handler == null) {
        Internal.logWarn(this, "cannot install progress handler [" + mySQLiteManual.getLastReturnCode() + "]");
        handler = ProgressHandler.DISPOSED;
      }
      myProgressHandler = handler;
    }
    return handler;
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
        SQLParts sql = null;
        synchronized (myLock) {
          if (myStatementCache.isEmpty())
            break;
          Map.Entry<SQLParts, SWIGTYPE_p_sqlite3_stmt> e = myStatementCache.entrySet().iterator().next();
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

  private void finalizeBlobs() {
    boolean alienThread = myConfinement != Thread.currentThread();
    if (!alienThread) {
      Internal.logFine(this, "finalizing blobs");
      while (true) {
        SQLiteBlob[] blobs = null;
        synchronized (myLock) {
          if (myBlobs.isEmpty())
            break;
          blobs = myBlobs.toArray(new SQLiteBlob[myBlobs.size()]);
        }
        for (SQLiteBlob blob : blobs) {
          finalizeBlob(blob);
        }
      }
    }
    synchronized (myLock) {
      if (!myBlobs.isEmpty()) {
        int count = myBlobs.size();
        if (alienThread) {
          Internal.logWarn(this, "cannot finalize " + count + " blobs from alien thread");
        } else {
          Internal.recoverableError(this, count + " blobs are not finalized", false);
        }
      }
      myBlobs.clear();
    }
  }

  private void finalizeStatement(SWIGTYPE_p_sqlite3_stmt handle, SQLParts sql) {
    if (Internal.isFineLogging())
      Internal.logFine(this, "finalizing cached stmt for " + sql);
    softFinalize(handle, sql);
    synchronized (myLock) {
      forgetCachedHandle(handle, sql);
    }
  }

  private void finalizeStatement(SQLiteStatement statement) {
    Internal.logFine(statement, "finalizing");
    SWIGTYPE_p_sqlite3_stmt handle = statement.statementHandle();
    SQLParts sql = statement.getSqlParts();
    statement.clear();
    softFinalize(handle, statement);
    synchronized (myLock) {
      forgetStatement(statement);
      forgetCachedHandle(handle, sql);
    }
  }

  private void finalizeArray(SQLiteLongArray array) {
    Internal.logFine(array, "finalizing");
    String tableName = array.getName();
    try {
      exec("DROP TABLE IF EXISTS " + tableName);
    } catch (SQLiteException e) {
      Internal.log(Level.WARNING, array, "cannot drop " + tableName, e);
    }
  }

  private void finalizeBlob(SQLiteBlob blob) {
    Internal.logFine(blob, "finalizing");
    SWIGTYPE_p_sqlite3_blob handle = blob.blobHandle();
    blob.clear();
    softClose(handle, blob);
    synchronized (myLock) {
      forgetBlob(blob);
    }
  }

  private void softFinalize(SWIGTYPE_p_sqlite3_stmt handle, Object source) {
    int rc = _SQLiteSwigged.sqlite3_finalize(handle);
    if (rc != SQLITE_OK) {
      Internal.logWarn(this, "error [" + rc + "] finishing " + source);
    }
  }

  private void softClose(SWIGTYPE_p_sqlite3_blob handle, Object source) {
    int rc = _SQLiteSwigged.sqlite3_blob_close(handle);
    if (rc != SQLITE_OK) {
      Internal.logWarn(this, "error [" + rc + "] finishing " + source);
    }
  }

  private void cacheArrayHandle(SQLiteLongArray array) {
    if (Internal.isFineLogging())
      Internal.logFine(array, "returning handle to cache");
    boolean finalize = false;
    SWIGTYPE_p_intarray handle = array.arrayHandle();
    if (handle == null) {
      Internal.logWarn(array, "no handle");
      return;
    }
    try {
      int rc = _SQLiteManual.sqlite3_intarray_unbind(handle);
      throwResult(rc, "intarray_unbind");
    } catch (SQLiteException e) {
      Internal.log(Level.WARNING, array, "exception when clearing", e);
      finalize = true;
    }
    if (finalize) {
      finalizeArray(array);
    } else {
      SWIGTYPE_p_intarray expunged = myLongArrays.put(array.getName(), handle);
      if (expunged != null) {
        Internal.logWarn(array, handle + " expunged " + expunged);
      }
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
    SQLParts sql = statement.getSqlParts();
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

  private void forgetCachedHandle(SWIGTYPE_p_sqlite3_stmt handle, SQLParts sql) {
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

  private void forgetBlob(SQLiteBlob blob) {
    assert Thread.holdsLock(myLock);
    boolean removed = myBlobs.remove(blob);
    if (!removed) {
      Internal.recoverableError(blob, "alien blob", true);
    }
  }

  private SWIGTYPE_p_sqlite3 handle() throws SQLiteException {
    synchronized (myLock) {
      if (myDisposed)
        throw new SQLiteException(WRAPPER_MISUSE, "connection is disposed");
      SWIGTYPE_p_sqlite3 handle = myHandle;
      if (handle == null)
        throw new SQLiteException(WRAPPER_NOT_OPENED, null);
      return handle;
    }
  }

  void throwResult(int resultCode, String operation) throws SQLiteException {
    throwResult(resultCode, operation, null);
  }

  void throwResult(int resultCode, String operation, Object additional) throws SQLiteException {
    if (resultCode != SQLiteConstants.SQLITE_OK) {
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
      if (resultCode == SQLITE_BUSY || resultCode == SQLITE_IOERR_BLOCKED) {
        throw new SQLiteBusyException(resultCode, message);
      } else if (resultCode == SQLITE_INTERRUPT) {
        throw new SQLiteInterruptedException(resultCode, message);
      } else {
        throw new SQLiteException(resultCode, message);
      }
    }
  }

  private void open0(int flags) throws SQLiteException {
    SQLite.loadLibrary();
    if (Internal.isFineLogging())
      Internal.logFine(this, "opening (0x" + Integer.toHexString(flags).toUpperCase(Locale.US) + ")");
    SWIGTYPE_p_sqlite3 handle;
    synchronized (myLock) {
      if (myDisposed) {
        throw new SQLiteException(WRAPPER_MISUSE, "cannot reopen closed connection");
      }
      if (myConfinement == null) {
        myConfinement = Thread.currentThread();
        if (Internal.isFineLogging())
          Internal.logFine(this, "confined to " + myConfinement);
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
    handle = mySQLiteManual.sqlite3_open_v2(dbname, flags);
    int rc = mySQLiteManual.getLastReturnCode();
    if (rc != SQLITE_OK) {
      if (handle != null) {
        if (Internal.isFineLogging())
          Internal.logFine(this, "error on open (" + rc + "), closing handle");
        try {
          _SQLiteSwigged.sqlite3_close(handle);
        } catch (Exception e) {
          Internal.log(Level.FINE, this, "error on closing after failed open", e);
        }
      }
      String errorMessage = mySQLiteManual.drainLastOpenError();
      if (errorMessage == null)
        errorMessage = "open database error code " + rc;
      throw new SQLiteException(rc, errorMessage);
    }
    if (handle == null) {
      throw new SQLiteException(WRAPPER_WEIRD, "sqlite didn't return db handle");
    }
    synchronized (myLock) {
      myHandle = handle;
    }
    Internal.logInfo(this, "opened");
    configureConnection(handle);
  }

  private void configureConnection(SWIGTYPE_p_sqlite3 handle) {
    int rc = _SQLiteSwigged.sqlite3_extended_result_codes(handle, 1);
    if (rc != SQLITE_OK) {
      Internal.logWarn(this, "cannot enable extended result codes [" + rc + "]");
    }
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
      throw new SQLiteException(WRAPPER_CONFINEMENT_VIOLATED, message);
    }
  }

  public String toString() {
    return "DB[" + myNumber + "]";
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

  private void freeBuffer(DirectBuffer buffer) throws SQLiteException {
    checkThread();
    boolean cached;
    synchronized (myLock) {
      cached = myBuffers.indexOf(buffer) >= 0;
    }
    buffer.decUsed();
    if (!cached) {
      int rc = _SQLiteManual.wrapper_free(buffer);
      if (rc != 0) {
        Internal.recoverableError(this, "error deallocating buffer", true);
      }
    }
  }

  private DirectBuffer allocateBuffer(int minimumSize) throws SQLiteException, IOException {
    checkThread();
    handle();
    int size = 1024;
    while (size < minimumSize + DirectBuffer.CONTROL_BYTES)
      size <<= 1;
    int payloadSize = size - DirectBuffer.CONTROL_BYTES;
    int allocated;
    DirectBuffer buffer = null;
    synchronized (myLock) {
      for (int i = myBuffers.size() - 1; i >= 0; i--) {
        DirectBuffer b = myBuffers.get(i);
        if (!b.isValid()) {
          myBuffers.remove(i);
          myBuffersTotalSize -= b.getCapacity();
          continue;
        }
        if (b.getCapacity() < payloadSize) {
          break;
        }
        if (!b.isUsed()) {
          buffer = b;
        }
      }
      if (buffer != null) {
        buffer.incUsed();
        buffer.data().clear();
        return buffer;
      }
      allocated = myBuffersTotalSize;
    }
    assert buffer == null;
    buffer = mySQLiteManual.wrapper_alloc(size);
    throwResult(mySQLiteManual.getLastReturnCode(), "allocateBuffer", minimumSize);
    if (buffer == null) {
      throw new SQLiteException(WRAPPER_WEIRD, "cannot allocate buffer [" + minimumSize + "]");
    }
    buffer.incUsed();
    buffer.data().clear();
    if (allocated + size < MAX_POOLED_DIRECT_BUFFER_SIZE) {
      synchronized (myLock) {
        int i;
        for (i = 0; i < myBuffers.size(); i++) {
          DirectBuffer b = myBuffers.get(i);
          if (b.getCapacity() > payloadSize)
            break;
        }
        myBuffers.add(i, buffer);
        myBuffersTotalSize += buffer.getCapacity();
      }
    }
    return buffer;
  }

  /**
   * Runs SQL and returns formatted result. This method is added for running an SQL from debugger.
   *
   * @param sql SQL to execute
   * @return a string containing multiline formatted table with the result
   */
  public String debug(String sql) {
    SQLiteStatement st = null;
    try {
      st = prepare(sql);
      boolean r = st.step();
      if (!r) {
        return "";
      }
      int columns = st.columnCount();
      if (columns == 0) {
        return "";
      }
      int[] widths = new int[columns];
      String[] columnNames = new String[columns];
      for (int i = 0; i < columns; i++) {
        columnNames[i] = String.valueOf(st.getColumnName(i));
        widths[i] = columnNames[i].length();
      }
      List<String> cells = new ArrayList<String>();
      do {
        for (int i = 0; i < columns; i++) {
          String v = st.columnNull(i) ? "<null>" : String.valueOf(st.columnValue(i));
          cells.add(v);
          widths[i] = Math.max(widths[i], v.length());
        }
      } while (st.step());

      StringBuilder buf = new StringBuilder();
      buf.append('|');
      for (int i = 0; i < columns; i++) {
        appendW(buf, columnNames[i], widths[i], ' ');
        buf.append('|');
      }
      buf.append("\n|");
      for (int i = 0; i < columns; i++) {
        appendW(buf, "", widths[i], '-');
        buf.append('|');
      }
      for (int i = 0; i < cells.size(); i++) {
        if (i % columns == 0) {
          buf.append("\n|");
        }
        appendW(buf, cells.get(i), widths[i % columns], ' ');
        buf.append('|');
      }
      return buf.toString();
    } catch (SQLiteException e) {
      return e.getMessage();
    } finally {
      if (st != null) st.dispose();
    }
  }

  private static void appendW(StringBuilder buf, String what, int width, char filler) {
    buf.append(what);
    for (int i = what.length(); i < width; i++)
      buf.append(filler);
  }


  private abstract class BaseController extends SQLiteController {
    public void validate() throws SQLiteException {
      assert validateImpl();
    }

    private boolean validateImpl() throws SQLiteException {
      SQLiteConnection.this.checkThread();
      SQLiteConnection.this.handle();
      return true;
    }

    public void throwResult(int resultCode, String message, Object additionalMessage) throws SQLiteException {
      SQLiteConnection.this.throwResult(resultCode, message, additionalMessage);
    }

    public void dispose(SQLiteBlob blob) {
      if (checkDispose(blob)) {
        SQLiteConnection.this.finalizeBlob(blob);
      }
    }

    protected boolean checkDispose(Object object) {
      try {
        SQLiteConnection.this.checkThread();
      } catch (SQLiteException e) {
        Internal.recoverableError(this, "disposing " + object + " from alien thread", true);
        return false;
      }
      return true;
    }

    public _SQLiteManual getSQLiteManual() {
      return mySQLiteManual;
    }

    public DirectBuffer allocateBuffer(int sizeEstimate) throws IOException, SQLiteException {
      return SQLiteConnection.this.allocateBuffer(sizeEstimate);
    }

    public void freeBuffer(DirectBuffer buffer) {
      try {
        SQLiteConnection.this.freeBuffer(buffer);
      } catch (SQLiteException e) {
        Internal.logWarn(SQLiteConnection.this, e.toString());
      }
    }

    public ProgressHandler getProgressHandler() throws SQLiteException {
      return SQLiteConnection.this.getProgressHandler();
    }
  }


  private class CachedController extends BaseController {
    public void dispose(SQLiteStatement statement) {
      if (checkDispose(statement)) {
        SQLiteConnection.this.cacheStatementHandle(statement);
      }
    }

    @Override
    public void dispose(SQLiteLongArray array) {
      if (checkDispose(array)) {
        SQLiteConnection.this.cacheArrayHandle(array);
      }
    }

    public String toString() {
      return SQLiteConnection.this.toString() + "[C]";
    }
  }

  private class UncachedController extends BaseController {
    public void dispose(SQLiteStatement statement) {
      if (checkDispose(statement)) {
        SQLiteConnection.this.finalizeStatement(statement);
      }
    }

    @Override
    public void dispose(SQLiteLongArray array) {
      if (checkDispose(array)) {
        SQLiteConnection.this.finalizeArray(array);
      }
    }

    public String toString() {
      return SQLiteConnection.this.toString() + "[U]";
    }
  }
}
