package sqlite.internal;

public class SQLiteManual {
  /**
   * @param filename
   * @param flags
   * @param returnCode
   * @return null if could not open
   */
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
}
