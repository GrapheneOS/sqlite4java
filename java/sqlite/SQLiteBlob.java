package sqlite;

import sqlite.internal.SQLiteConstants;
import sqlite.internal.SWIGTYPE_p_sqlite3_blob;
import sqlite.internal._SQLiteManual;
import sqlite.internal._SQLiteSwigged;

/**
 * This class encapsulates blob handle which is open for direct reading / writing
 */
public final class SQLiteBlob {
  /**
   * Debug name
   */
  private final String myName;

  /**
   * Whether blob was opened for writing
   */
  private final boolean myWriteAccess;

  /**
   * Controller, not null
   */
  private SQLiteController myController;

  /**
   * Handle, set to null when disposed
   */
  private SWIGTYPE_p_sqlite3_blob myHandle;

  /**
   * Cached length
   */
  private int myLength = -1;

  SQLiteBlob(SQLiteController controller, SWIGTYPE_p_sqlite3_blob handle, String dbname, String table, String column,
    long rowid, boolean writeAccess)
  {
    assert controller != null;
    assert handle != null;
    myController = controller;
    myHandle = handle;
    myWriteAccess = writeAccess;
    myName = dbname + "." + table + "." + column + ":" + rowid;
  }

  /**
   * Disposes this statement and frees allocated resources. If statement handle is cached,
   * it is returned to connection's cache.
   * <p/>
   * After statement is disposed, it is no longer usable and holds no references to connection
   * or sqlite db.
   */
  public void dispose() {
    if (myHandle == null)
      return;
    try {
      myController.validate();
    } catch (SQLiteException e) {
      Internal.recoverableError(this, "invalid dispose: " + e, true);
      return;
    }
    Internal.logFine(this, "disposing");
    myController.dispose(this);
    // clear may be called from dispose() too
    clear();
  }

  /**
   * @return true if the statement is disposed and cannot be used.
   */
  public boolean isDisposed() {
    return myHandle == null;
  }

  public int getLength() throws SQLiteException {
    myController.validate();
    if (myLength < 0) {
      myLength = _SQLiteSwigged.sqlite3_blob_bytes(handle());
    }
    return myLength;
  }

  public void read(int blobOffset, byte[] buffer, int offset, int length) throws SQLiteException {
    myController.validate();
    if (Internal.isFineLogging())
      Internal.logFine(this, "read[" + blobOffset + "," + length + "]");
    int rc = _SQLiteManual.sqlite3_blob_read(handle(), blobOffset, buffer, offset, length);
    myController.throwResult(rc, "read", this);
  }

  public void write(int blobOffset, byte[] buffer, int offset, int length) throws SQLiteException {
    myController.validate();
    if (Internal.isFineLogging())
      Internal.logFine(this, "write[" + blobOffset + "," + length + "]");
    int rc = _SQLiteManual.sqlite3_blob_write(handle(), blobOffset, buffer, offset, length);
    myController.throwResult(rc, "write", this);
  }

  private SWIGTYPE_p_sqlite3_blob handle() throws SQLiteException {
    SWIGTYPE_p_sqlite3_blob handle = myHandle;
    if (handle == null) {
      throw new SQLiteException(SQLiteConstants.Wrapper.WRAPPER_BLOB_DISPOSED, null);
    }
    return handle;
  }

  SWIGTYPE_p_sqlite3_blob blobHandle() {
    return myHandle;
  }

  /**
   * Clear all data, disposing the blob. May be called by SQLiteConnection on close.
   */
  void clear() {
    myHandle = null;
    myController = myController.getDisposedController();
    Internal.logFine(this, "cleared");
  }

  public String toString() {
    return "[" + myName + "]" + myController;
  }

  public boolean canWrite() {
    return myWriteAccess;
  }
}
