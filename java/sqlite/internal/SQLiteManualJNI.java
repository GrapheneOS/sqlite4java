package sqlite.internal;

class SQLiteManualJNI {
  public final static native int sqlite3_open_v2(String filename, long[] ppDb, int flags);
}
