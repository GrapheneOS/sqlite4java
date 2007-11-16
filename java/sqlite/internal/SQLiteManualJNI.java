package sqlite.internal;

class SQLiteManualJNI {
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

  /**
   * @param stmt executed statement
   * @param column index of column, 0-based
   * @param ppValue String[1] container for the result
   * @return result code
   */
  public final static native int sqlite3_column_text(long stmt, int column, String[] ppValue);
}
