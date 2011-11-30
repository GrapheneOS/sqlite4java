package com.almworks.sqlite4java;

import static com.almworks.sqlite4java.SQLiteConstants.SQLITE_DONE;
import static com.almworks.sqlite4java.SQLiteConstants.WRAPPER_BACKUP_DISPOSED;

public class SQLiteBackup {

  private final SQLiteConnection mySource;

  private final SQLiteConnection myDestination;

  private SWIGTYPE_p_sqlite3_backup myHandle;

  private SQLiteController myDestinationController;

  private SQLiteController mySourceController;

  private boolean myFinished;

  SQLiteBackup(SQLiteController sourceController, SQLiteController destinationController, SWIGTYPE_p_sqlite3_backup handle, SQLiteConnection source, SQLiteConnection destination) {
    mySourceController = sourceController;
    myDestinationController = destinationController;
    myHandle = handle;
    myDestination = destination;
    mySource = source;
    Internal.logFine(this, "instantiated");
  }

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

  public boolean isFinished() {
    return myFinished;
  }

  public SQLiteConnection getDestinationConnection() {
    return myDestination;
  }

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

  public void dispose() {
    dispose(true);
  }

  public int getPageCount() throws SQLiteException {
    mySourceController.validate();
    myDestinationController.validate();
    SWIGTYPE_p_sqlite3_backup handle = handle();
    return _SQLiteSwigged.sqlite3_backup_pagecount(handle);
  }

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
