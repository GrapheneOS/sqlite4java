package sqlite.internal;

class _SQLiteManualJNI {
  public final static native String wrapper_version();

  /**
   * @param filename database file name, not null
   * @param ppDb long[1] container for the db handle
   * @param flags see SQLITE_OPEN_* constants
   * @return return code SQLITE_OK or other
   */
  public final static native int sqlite3_open_v2(String filename, long[] ppDb, int flags);

  /**
   * @param db handle
   * @param sql sql statements
   * @param ppParseError (nullable) container for parsing errors
   * @return
   */
  public final static native int sqlite3_exec(long db, String sql, String[] ppParseError);

  /**
   * @param db handle
   * @param sql sql statement
   * @param ppStmt long[1] container for statement handle
   * @return result code
   */
  public final static native int sqlite3_prepare_v2(long db, String sql, long[] ppStmt);

  /**
   * @param stmt prepared statement
   * @param index index of param, 1-based
   * @param value string value, UTF-safe
   * @return result code
   */
  public final static native int sqlite3_bind_text(long stmt, int index, String value);

  public final static native int sqlite3_bind_blob(long stmt, int index, byte[] value, int offset, int length);

  /**
   * @param stmt executed statement
   * @param column index of column, 0-based
   * @param ppValue String[1] container for the result
   * @return result code
   */
  public final static native int sqlite3_column_text(long stmt, int column, String[] ppValue);

  public final static native int sqlite3_column_blob(long stmt, int column, byte[][] ppValue);

  /**
   * @param db database
   * @param database db name
   * @param table table name
   * @param column column name
   * @param rowid rowid of the blob to open
   * @param writeAccess if true, can read/write, if false, can only read
   * @param ppBlob output
   * @return result code
   */
  public final static native int sqlite3_blob_open(long db, String database, String table, String column, long rowid, boolean writeAccess, long[] ppBlob);

  public final static native int sqlite3_blob_read(long blob, int blobOffset, byte[] buffer, int bufferOffset, int length);

  public final static native int sqlite3_blob_write(long blob, int blobOffset, byte[] buffer, int bufferOffset, int length);
}
