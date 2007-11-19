package sqlite;

import sqlite.internal.SQLiteConstants;
import sqlite.internal.SQLiteSwigged;
import static sqlite.internal.SQLiteConstants.Wrapper;

public final class DBGlobal {
  private static boolean preferDebugLibrary = "true".equalsIgnoreCase(System.getProperty("sqlite.prefer.debug.lib"));
  private static boolean libraryLoaded = false;

  public static synchronized void setPreferDebugLibrary(boolean debug) {
    if (libraryLoaded) {
      DBInternal.logger.warning("DBGlobal: cannot prefer debug library, library already loaded");
      return;
    }
    preferDebugLibrary = debug;
  }

  public static synchronized void loadLibrary() throws DBException {
    if (!libraryLoaded) {
      Throwable t = DBInternal.loadLibraryX();
      if (t != null)
        throw new DBException(Wrapper.WRAPPER_CANNOT_LOAD_LIBRARY, "cannot load library: " + t, t);
      libraryLoaded = true;
    }
  }

  public static String getSqliteVersion() throws DBException {
    loadLibrary();
    return SQLiteSwigged.sqlite3_libversion();
  }

  static boolean isPreferDebugLibrary() {
    return preferDebugLibrary;
  }

  private DBGlobal() {}
}
