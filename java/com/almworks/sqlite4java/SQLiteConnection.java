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

import javolution.util.FastMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import static com.almworks.sqlite4java.SQLiteConstants.*;

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
  private int myStepsPerCallback = DEFAULT_STEPS_PER_CALLBACK;

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

  public void setStepsPerCallback(int stepsPerCallback) {
    if (stepsPerCallback > 0) {
      myStepsPerCallback = stepsPerCallback;
    }
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
   *
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
    finalizeBlobs();
    finalizeBuffers();
    finalizeProgressHandler(handle);
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

  /**
   * Execute SQL.
   */
  public SQLiteConnection exec(String sql) throws SQLiteException {
    checkThread();
    if (Internal.isFineLogging())
      Internal.logFine(this, "exec [" + sql + "]");
    SWIGTYPE_p_sqlite3 handle = handle();
    ProgressHandler ph = getProgressHandler();
    ph.reset();
    try {
      String[] error = {null};
      int rc = _SQLiteManual.sqlite3_exec(handle, sql, error);
      throwResult(rc, "exec()", error[0]);
    } finally {
      if (Internal.isFineLogging())
        Internal.logFine(this, "exec [" + sql + "]: " + ph.getSteps() + " steps");
      ph.reset();
    }
    return this;
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

  /**
   * Prepares cached SQL statement.
   */
  public SQLiteStatement prepare(String sql) throws SQLiteException {
    return prepare(sql, true);
  }

  /**
   * @see #prepare(SQLParts, boolean)
   */
  public SQLiteStatement prepare(String sql, boolean cached) throws SQLiteException {
    return prepare(new SQLParts(sql), cached);
  }

  public SQLiteStatement prepare(SQLParts parts) throws SQLiteException {
    return prepare(parts, true);
  }

  /**
   * Prepares SQL statement
   *
   * @param cached if true, the statement handle will be cached by the connection, so after the SQLiteStatement
   *               is disposed, the handle may be reused by another prepare() call.
   */
  public SQLiteStatement prepare(SQLParts parts, boolean cached) throws SQLiteException {
    checkThread();
    if (Internal.isFineLogging())
      Internal.logFine(this, "prepare [" + parts + "]");
    if (parts == null)
      throw new IllegalArgumentException();
    SWIGTYPE_p_sqlite3 handle;
    SWIGTYPE_p_sqlite3_stmt stmt = null;
    SQLParts fixedKey = null;
    int openCounter;
    synchronized (myLock) {
      if (cached) {
        // while the statement is in work, it is removed from cache. it is put back in cache by SQLiteStatement.dispose().
        FastMap.Entry<SQLParts, SWIGTYPE_p_sqlite3_stmt> e = myStatementCache.getEntry(parts);
        if (e != null) {
          fixedKey = e.getKey();
          assert fixedKey != null;
          assert fixedKey.isFixed() : parts;
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
        Internal.logFine(this, "calling sqlite3_prepare_v2 for [" + parts + "]");
      stmt = mySQLiteManual.sqlite3_prepare_v2(handle, parts.toString());
      throwResult(mySQLiteManual.getLastReturnCode(), "prepare()", parts);
      if (stmt == null)
        throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "sqlite did not return stmt");
    } else {
      if (Internal.isFineLogging())
        Internal.logFine(this, "using cached stmt for [" + parts + "]");
    }
    SQLiteStatement statement = null;
    synchronized (myLock) {
      // the connection may close while prepare in progress
      // most probably that would throw SQLiteException earlier, but we'll check anyway
      if (myHandle != null) {
        SQLiteController controller = cached ? myCachedController : myUncachedController;
        if (fixedKey == null)
          fixedKey = parts.getFixedParts();
        statement = new SQLiteStatement(controller, stmt, fixedKey);
        myStatements.add(statement);
      } else {
        Internal.logWarn(this, "connection disposed while preparing statement for [" + parts + "]");
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

  public SQLiteBlob blob(String table, String column, long rowid, boolean writeAccess) throws SQLiteException {
    return blob(null, table, column, rowid, writeAccess);
  }

  public SQLiteBlob blob(String dbname, String table, String column, long rowid, boolean writeAccess) throws SQLiteException {
    checkThread();
    if (Internal.isFineLogging())
      Internal.logFine(this, "openBlob [" + dbname + "," + table + "," + column + "," + rowid + "," + writeAccess + "]");
    SWIGTYPE_p_sqlite3 handle = handle();
    SWIGTYPE_p_sqlite3_blob blob = mySQLiteManual.sqlite3_blob_open(handle, dbname, table, column, rowid, writeAccess);
    throwResult(mySQLiteManual.getLastReturnCode(), "openBlob()", null);
    if (blob == null)
      throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "sqlite did not return blob");
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
      throw new SQLiteException(Wrapper.WRAPPER_NOT_OPENED, "connection disposed");
    }
    return result;
  }

  /**
   * @see <a href="http://www.sqlite.org/c3ref/busy_timeout.html">sqlite3_busy_timeout</a>
   */
  public SQLiteConnection setBusyTimeout(long millis) throws SQLiteException {
    checkThread();
    int rc = _SQLiteSwigged.sqlite3_busy_timeout(handle(), (int) millis);
    throwResult(rc, "setBusyTimeout");
    return this;
  }

  public boolean getAutoCommit() throws SQLiteException {
    checkThread();
    int r = _SQLiteSwigged.sqlite3_get_autocommit(handle());
    return r != 0;
  }

  public long getLastInsertId() throws SQLiteException {
    checkThread();
    long id = _SQLiteSwigged.sqlite3_last_insert_rowid(handle());
    return id;
  }

  public int getChanges() throws SQLiteException {
    checkThread();
    int result = _SQLiteSwigged.sqlite3_changes(handle());
    return result;
  }

  public int getTotalChanges() throws SQLiteException {
    checkThread();
    int result = _SQLiteSwigged.sqlite3_total_changes(handle());
    return result;
  }

  public void interrupt() throws SQLiteException {
    _SQLiteSwigged.sqlite3_interrupt(handle());
  }

  public int getErrorCode() throws SQLiteException {
    checkThread();
    return _SQLiteSwigged.sqlite3_errcode(handle());
  }

  public String getErrorMessage() throws SQLiteException {
    checkThread();
    return _SQLiteSwigged.sqlite3_errmsg(handle());
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
    if (rc != Result.SQLITE_OK) {
      Internal.logWarn(this, "error [" + rc + "] finishing " + source);
    }
  }

  private void softClose(SWIGTYPE_p_sqlite3_blob handle, Object source) {
    int rc = _SQLiteSwigged.sqlite3_blob_close(handle);
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
      if (resultCode == Result.SQLITE_BUSY || resultCode == Result.SQLITE_IOERR_BLOCKED) {
        throw new SQLiteBusyException(resultCode, message);
      } else if (resultCode == Result.SQLITE_INTERRUPT) {
        throw new SQLiteCancelledException(resultCode, message);
      } else {
        throw new SQLiteException(resultCode, message);
      }
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
    if (rc != Result.SQLITE_OK) {
      if (handle != null) {
        if (Internal.isFineLogging())
          Internal.logFine(this, "error on open (" + rc + "), closing handle");
        try {
          _SQLiteSwigged.sqlite3_close(handle);
        } catch (Exception e) {
          Internal.log(Level.FINE, this, "error on closing after failed open", e);
        }
      }
      String errorMessage = _SQLiteSwigged.sqlite3_errmsg(null);
      throw new SQLiteException(rc, errorMessage);
    }
    if (handle == null) {
      throw new SQLiteException(Wrapper.WRAPPER_WEIRD, "sqlite didn't return db handle");
    }
    synchronized (myLock) {
      myHandle = handle;
    }
    Internal.logInfo(this, "opened");
    configureConnection(handle);
  }

  private void configureConnection(SWIGTYPE_p_sqlite3 handle) {
    int rc = _SQLiteSwigged.sqlite3_extended_result_codes(handle, 1);
    if (rc != Result.SQLITE_OK) {
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
      throw new SQLiteException(Wrapper.WRAPPER_CONFINEMENT_VIOLATED, message);
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
      throw new SQLiteException(SQLiteConstants.Wrapper.WRAPPER_WEIRD, "cannot allocate buffer [" + minimumSize + "]");
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
        columnNames[i] = String.valueOf(st.columnName(i));
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

    public String toString() {
      return SQLiteConnection.this.toString() + "[U]";
    }
  }
}
