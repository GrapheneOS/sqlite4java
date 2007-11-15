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
}
