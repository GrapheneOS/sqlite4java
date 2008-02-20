package sqlite.internal;

public final class _SQLiteManual {
  /**
   * These arrays are used for return values. SQLiteConnection facade must ensure the methods are called
   * from the same thread, so these values are confined.
   */
  private final long[] myLong = {0};
  private final String[] myString = {null};
  private final byte[][] myByteArray = {null};

  /**
   * Last return code received for non-static methods.
   */
  private int myLastReturnCode = 0;

  public static String wrapper_version() {
    return _SQLiteManualJNI.wrapper_version();
  }

  public static int sqlite3_exec(SWIGTYPE_p_sqlite3 db, String sql, String[] outError) {
    assert outError == null || outError.length == 1 : outError.length;
    return _SQLiteManualJNI.sqlite3_exec(SWIGTYPE_p_sqlite3.getCPtr(db), sql, outError);
  }

  public static int sqlite3_bind_text(SWIGTYPE_p_sqlite3_stmt stmt, int index, String value) {
    return _SQLiteManualJNI.sqlite3_bind_text(SWIGTYPE_p_sqlite3_stmt.getCPtr(stmt), index, value);
  }

  public static int sqlite3_bind_blob(SWIGTYPE_p_sqlite3_stmt stmt, int index, byte[] value, int offset, int length) {
    return _SQLiteManualJNI.sqlite3_bind_blob(SWIGTYPE_p_sqlite3_stmt.getCPtr(stmt), index, value, offset, length);
  }

  public static int sqlite3_blob_read(SWIGTYPE_p_sqlite3_blob blob, int blobOffset, byte[] buffer, int bufferOffset, int length) {
    return _SQLiteManualJNI.sqlite3_blob_read(SWIGTYPE_p_sqlite3_blob.getCPtr(blob), blobOffset, buffer, bufferOffset, length);
  }

  public static int sqlite3_blob_write(SWIGTYPE_p_sqlite3_blob blob, int blobOffset, byte[] buffer, int bufferOffset, int length) {
    return _SQLiteManualJNI.sqlite3_blob_write(SWIGTYPE_p_sqlite3_blob.getCPtr(blob), blobOffset, buffer, bufferOffset, length);
  }

  public int getLastReturnCode() {
    return myLastReturnCode;
  }

  public SWIGTYPE_p_sqlite3 sqlite3_open_v2(String filename, int flags) {
    myLastReturnCode = 0;
    myLong[0] = 0;
    myLastReturnCode = _SQLiteManualJNI.sqlite3_open_v2(filename, myLong, flags);
    long ptr = myLong[0];
    myLong[0] = 0;
    return ptr == 0 ? null : new SWIGTYPE_p_sqlite3(ptr, true);
  }

  public SWIGTYPE_p_sqlite3_stmt sqlite3_prepare_v2(SWIGTYPE_p_sqlite3 db, String sql) {
    myLastReturnCode = 0;
    myLong[0] = 0;
    myLastReturnCode = _SQLiteManualJNI.sqlite3_prepare_v2(SWIGTYPE_p_sqlite3.getCPtr(db), sql, myLong);
    long ptr = myLong[0];
    myLong[0] = 0;
    return ptr == 0 ? null : new SWIGTYPE_p_sqlite3_stmt(ptr, true);
  }

  public String sqlite3_column_text(SWIGTYPE_p_sqlite3_stmt stmt, int column) {
    myLastReturnCode = 0;
    myString[0] = null;
    myLastReturnCode = _SQLiteManualJNI.sqlite3_column_text(SWIGTYPE_p_sqlite3_stmt.getCPtr(stmt), column, myString);
    String r = myString[0];
    myString[0] = null;
    return r;
  }

  public byte[] sqlite3_column_blob(SWIGTYPE_p_sqlite3_stmt stmt, int column) {
    myLastReturnCode = 0;
    myByteArray[0] = null;
    myLastReturnCode = _SQLiteManualJNI.sqlite3_column_blob(SWIGTYPE_p_sqlite3_stmt.getCPtr(stmt), column, myByteArray);
    byte[] r = myByteArray[0];
    myByteArray[0] = null;
    return r;
  }

  public SWIGTYPE_p_sqlite3_blob sqlite3_blob_open(SWIGTYPE_p_sqlite3 db, String database, String table, String column, long rowid, boolean writeAccess) {
    myLastReturnCode = 0;
    myLong[0] = 0;
    myLastReturnCode = _SQLiteManualJNI.sqlite3_blob_open(SWIGTYPE_p_sqlite3.getCPtr(db), database, table, column, rowid, writeAccess, myLong);
    long ptr = myLong[0];
    myLong[0] = 0;
    return ptr == 0 ? null : new SWIGTYPE_p_sqlite3_blob(ptr, true);
  }
}
