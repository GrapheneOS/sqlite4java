package sqlite.internal;

public class SQLiteManual {
  public static SWIGTYPE_p_sqlite3 sqlite3_open_v2(String filename, int flags, int[] returnCode) {
    long[] ppDb = {0};
    int rc = SQLiteManualJNI.sqlite3_open_v2(filename, ppDb, flags);
    if (returnCode != null) {
      if (returnCode.length == 1) {
        returnCode[0] = rc;
      } else {
        assert false : returnCode.length;
      }
    }
    long ptr = ppDb[0];
    return ptr == 0 ? null : new SWIGTYPE_p_sqlite3(ptr, true);
  }

  public static int sqlite3_exec(SWIGTYPE_p_sqlite3 db, String sql, String[] outParseError) {
    assert outParseError == null || outParseError.length == 1 : outParseError.length;
    return SQLiteManualJNI.sqlite3_exec(SWIGTYPE_p_sqlite3.getCPtr(db), sql, outParseError);
  }
}
