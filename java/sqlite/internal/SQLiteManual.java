package sqlite.internal;

public class SQLiteManual {
  public static String wrapper_version() {
    return SQLiteManualJNI.wrapper_version();
  }
  
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

  public static int sqlite3_exec(SWIGTYPE_p_sqlite3 db, String sql, String[] outError) {
    assert outError == null || outError.length == 1 : outError.length;
    return SQLiteManualJNI.sqlite3_exec(SWIGTYPE_p_sqlite3.getCPtr(db), sql, outError);
  }

  public static SWIGTYPE_p_sqlite3_stmt sqlite3_prepare_v2(SWIGTYPE_p_sqlite3 db, String sql, int[] returnCode) {
    long[] ppStmt = {0};
    int rc = SQLiteManualJNI.sqlite3_prepare_v2(SWIGTYPE_p_sqlite3.getCPtr(db), sql, ppStmt);
    if (returnCode != null) {
      if (returnCode.length == 1) {
        returnCode[0] = rc;
      } else {
        assert false : returnCode.length;
      }
    }
    long ptr = ppStmt[0];
    return ptr == 0 ? null : new SWIGTYPE_p_sqlite3_stmt(ptr, true);
  }

  public static int sqlite3_bind_text(SWIGTYPE_p_sqlite3_stmt stmt, int index, String value) {
    return SQLiteManualJNI.sqlite3_bind_text(SWIGTYPE_p_sqlite3_stmt.getCPtr(stmt), index, value);
  }

  public static String sqlite3_column_text(SWIGTYPE_p_sqlite3_stmt stmt, int column, int[] returnCode) {
    String[] ppResult = {null};
    int rc = SQLiteManualJNI.sqlite3_column_text(SWIGTYPE_p_sqlite3_stmt.getCPtr(stmt), column, ppResult);
    if (returnCode != null) {
      if (returnCode.length == 1) {
        returnCode[0] = rc;
      } else {
        assert false : returnCode.length;
      }
    }
    return ppResult[0];
  }
}
