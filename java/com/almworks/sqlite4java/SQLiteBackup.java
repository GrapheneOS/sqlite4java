package com.almworks.sqlite4java;

import static com.almworks.sqlite4java.SQLiteConstants.SQLITE_DONE;
import static com.almworks.sqlite4java.SQLiteConstants.WRAPPER_BACKUP_DISPOSED;

/**
 * SQLiteBackup wraps an instance of SQLite database backup, represented as <strong><code>sqlite3_backup*</strong></code>
 * in SQLite C API.
 * <p>
 * Use example:
 * <pre>
 * SQLiteBackup backup = connection.initializeBackup(new File("filename"));
 * try{
 *   while(!backup.isFinished()) {
 *      backup.backupStep(2);
 *   }
 * } finally {
 *   backup.dispose();
 * }
 * </pre>
 * </p>
 * <p>
 * Unless a method is marked as thread-safe, it is confined to the thread that has opened the connection to source
 * database. Calling a confined method from a different thread will result in exception.
 * </p>
 *
 * @author Igor Korsunov
 * @see <a href=http://www.sqlite.org/c3ref/backup_finish.html#sqlite3backupinit> SQLite Online Backup API</a>,
 * <a href=http://www.sqlite.org/backup.html>Using the SQLite Online Backup API</a>
 */
public class SQLiteBackup {

  /**
   * Source database connection
   */
  private final SQLiteConnection mySource;

  /**
   * Destination database connection
   */
  private final SQLiteConnection myDestination;

  /**
   * Handle for native operations
   */
  private SWIGTYPE_p_sqlite3_backup myHandle;

  /**
   *
   */
  private SQLiteController myDestinationController;

  /**
   *
   */
  private SQLiteController mySourceController;

  /**
   * If true, last call to sqlite3_backup_step() returned SQLITE_DONE
   */
  private boolean myFinished;

  SQLiteBackup(SQLiteController sourceController, SQLiteController destinationController, SWIGTYPE_p_sqlite3_backup handle, SQLiteConnection source, SQLiteConnection destination) {
    mySourceController = sourceController;
    myDestinationController = destinationController;
    myHandle = handle;
    myDestination = destination;
    mySource = source;
    Internal.logFine(this, "instantiated");
  }

  /**
   * Copy up to pagesToBackup pages from source database to destination. If pagesToBackup are negative, all remaining
   * pages are copied.
   * <p>
   *   If source database will be modified during backup by connection other than source connection, then backup will be restarted
   *   by the next call to backupStep. Else if source database will be modified by source connection itself, then destination
   *   database will be updated without restarting.
   * </p>
   *
   * @param pagesToBackup - the number of page, that will be backed up during this step.
   * @return true if back up was finished, false if there are still more pages to back up.
   * @throws SQLiteException if SQLite return an error or if the call violates the contract of this class
   * @throws SQLiteBusyException if SQLite cannot established SHARED_LOCK on source DB or RESERVED_LOCK on
   * destination DB or source connection is used to write to DB. In these cases call to backupStep can be
   * retried later
   * @see <a href=http://www.sqlite.org/c3ref/backup_finish.html#sqlite3backupstep>sqlite3_backup_step</a>
   */
  public boolean backupStep(int pagesToBackup) throws SQLiteException, SQLiteBusyException {
    mySourceController.validate();
    myDestinationController.validate();
    if (myFinished) {
      Internal.logWarn(this, "already finished");
      return true;
    }
    if (Internal.isFineLogging()) {
      Internal.logFine(this, "backupStep(" + pagesToBackup + ")");
    }
    SWIGTYPE_p_sqlite3_backup handle = handle();
    int rc = _SQLiteSwigged.sqlite3_backup_step(handle, pagesToBackup);
    throwResult(rc, "backupStep failed");
    if (rc == SQLITE_DONE) {
      if (Internal.isFineLogging()) {
        Internal.logFine(this, "finished");
      }
      myFinished = true;
    }
    return myFinished;
  }

  /**
   * Checks whether back up was successfully finished.
   *
   * @return true if last call to {@link #backupStep} has returned true.
   */
  public boolean isFinished() {
    return myFinished;
  }

  /**
   * Returns connection to destination database, that was opened by {@link com.almworks.sqlite4java.SQLiteConnection#initializeBackup}.
   * <p>
   * <strong>NB!</strong> If you get connection to destination database, you should care about disposing order of
   * backup and that connection. That connection must be disposed <strong>after</strong> disposing SQLiteBackup instance.
   * </p>
   *
   * @return destination database connection.
   */
  public SQLiteConnection getDestinationConnection() {
    return myDestination;
  }

  /**
   * Dispose this backup instance and, if <code>disposeDestination</code> is true, connection to destination database.
   *
   * @param disposeDestination if true, connection to destination database will be disposed.
   */
  public void dispose(boolean disposeDestination) {
    try {
      mySourceController.validate();
      myDestinationController.validate();
    } catch (SQLiteException e) {
      Internal.recoverableError(this, "invalid dispose: " + e, true);
      return;
    }
    Internal.logFine(this, "disposing");
    SWIGTYPE_p_sqlite3_backup handle = myHandle;
    if (handle != null) {
      _SQLiteSwigged.sqlite3_backup_finish(handle);
      myHandle = null;
      mySourceController = SQLiteController.getDisposed(mySourceController);
      myDestinationController = SQLiteController.getDisposed(myDestinationController);
    }
    if (disposeDestination) {
      myDestination.dispose();
    }
  }

  /**
   * Dispose this backup instance and connection to destination database.
   * <p>
   *   This is convenience method, equivalent to <code>dispose(true)</code>.
   * </p>
   */
  public void dispose() {
    dispose(true);
  }

  /**
   * Returns the total number of pages in source database.
   *
   * @return destination database's number of pages.
   * @throws SQLiteException if called from differed thread or if source or destination connection are disposed
   */
  public int getPageCount() throws SQLiteException {
    mySourceController.validate();
    myDestinationController.validate();
    SWIGTYPE_p_sqlite3_backup handle = handle();
    return _SQLiteSwigged.sqlite3_backup_pagecount(handle);
  }

  /**
   * Returns the number of pages still to be backed up.
   *
   * @return number of remaining pages.
   * @throws SQLiteException if called from differed thread or if source or destination connection are disposed
   */
  public int getRemaining() throws SQLiteException {
    mySourceController.validate();
    myDestinationController.validate();
    SWIGTYPE_p_sqlite3_backup handle = handle();
    return _SQLiteSwigged.sqlite3_backup_remaining(handle);
  }

  @Override
  public String toString() {
    return "Backup [" + mySource + " -> " + myDestination + "]";
  }

  private SWIGTYPE_p_sqlite3_backup handle() throws SQLiteException {
    SWIGTYPE_p_sqlite3_backup handle = myHandle;
    if (handle == null) {
      throw new SQLiteException(WRAPPER_BACKUP_DISPOSED, null);
    }
    return handle;
  }

  private void throwResult(int rc, String operation) throws SQLiteException {
    if (rc == SQLITE_DONE) return;
    myDestination.throwResult(rc, operation);
  }
}
