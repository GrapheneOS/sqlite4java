package sqlite;

import static sqlite.internal.SQLiteConstants.*;
import sqlite.internal.SQLiteManual;
import sqlite.internal.SQLiteSwigged;
import sqlite.internal.SWIGTYPE_p_sqlite3;
import sqlite.internal.SQLiteConstants;

import java.io.File;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * DBConnection is a single connection to sqlite database. Most methods are thread-confined,
 * and will throw errors if called from alien thread. Confinement thread is defined at the
 * construction time.
 * <p>
 * DBConnection should be expicitly closed before the object is disposed. Failing to do so
 * may result in unpredictable behavior from sqlite. 
 */
public final class DBConnection {
  private static final Logger logger = Logger.getLogger("sqlite");
  /**
   * The database file, or null if it is memory database
   */
  private final File myFile;
  private final Thread myConfinement;

  /**
   * Handle to the db. Confined.
   */
  private SWIGTYPE_p_sqlite3 myHandle;

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
   * be true
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

  public boolean isOpen() {
    try {
      checkThread();
    } catch (DBException e) {
      recoverableError("isOpen() " + e.getMessage(), true);
    }
    return myHandle != null;
  }

  /**
   * Closes database. After database is closed, it may be reopened again. In case of in-memory
   * database, the reopened database will be empty.
   *
   * @throws DBException may be thrown if closing from alien thread, or if uncontrolled statement was not
   * released
   * @see #closeByEmergency()
   */
  public void close() throws DBException {
    checkThread();
    closeX();
  }

  /**
   * Attempts to close database. May be called from another thread, for example if database
   * thread is dead.
   */
  public void closeByEmergency() {
    logger.warning(this + " performing emergency close");
    try {
      checkThread();
    } catch (DBException e) {
      recoverableError("closeByEmergency() " + e.getMessage(), false);
    }
    try {
      closeX();
    } catch (Exception e) {
      recoverableError("closeByEmergency() " + e.getMessage(), false);
    }
  }

  private void closeX() throws DBException {
    SWIGTYPE_p_sqlite3 handle = myHandle;
    if (handle == null)
      return;
    int rc = SQLiteSwigged.sqlite3_close(handle);
    // rc may be SQLiteConstants.Result.SQLITE_BUSY if statements are open
    throwResult(rc, "close()");
    myHandle = null;
    logger.info(this + " closed");
  }

  private void throwResult(int resultCode, String operation) throws DBException {
    if (resultCode != SQLiteConstants.Result.SQLITE_OK) {
      SWIGTYPE_p_sqlite3 handle = myHandle;
      String message = this + " " + operation;
      if (handle != null) {
        try {
          message += ": " + SQLiteSwigged.sqlite3_errmsg(handle);
        } catch (Exception e) {
          logger.log(Level.WARNING, "cannot get sqlite3_errmsg", e);
        }
      }
      throw new DBException(resultCode, message);
    }
  }

  private void openX(int flags) throws DBException {
    checkThread();
    if (myHandle != null) {
      recoverableError("already opened", true);
      return;
    }
    String dbname = getSqliteDbName();
    int[] rc = {Integer.MIN_VALUE};
    SWIGTYPE_p_sqlite3 dbHandle = SQLiteManual.sqlite3_open_v2(dbname, flags, rc);
    if (rc[0] != Result.SQLITE_OK) {
      if (dbHandle != null) {
        try {
          SQLiteSwigged.sqlite3_close(dbHandle);
        } catch (Exception e) {
          // ignore
        }
      }
      String errorMessage = SQLiteSwigged.sqlite3_errmsg(null);
      throw new DBException(rc[0], errorMessage);
    }
    if (dbHandle == null) {
      throw new DBException(Wrapper.WRAPPER_WEIRD, "sqlite didn't return db handle");
    }
    myHandle = dbHandle;
    logger.info(this + " opened(" + flags + ")");
  }

  private String getSqliteDbName() {
    return myFile == null ? ":memory:" : myFile.getAbsolutePath();
  }

  private void checkThread() throws DBException {
    Thread thread = Thread.currentThread();
    if (thread != myConfinement) {
      String message = this + " confined(" + myConfinement + ") used(" + thread + ")";
      throw new DBException(Wrapper.WRAPPER_CONFINEMENT_VIOLATED, message);
    }
  }

  private void recoverableError(String message, boolean throwAssertion) {
    message = this + " " + message;
    assert !throwAssertion : message;
    logger.warning(message);
  }

  public String toString() {
    return "sqlite[" + getSqliteDbName() + "]";
  }
}
