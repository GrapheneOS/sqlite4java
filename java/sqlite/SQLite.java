package sqlite;

import sqlite.internal._SQLiteSwigged;
import static sqlite.internal.SQLiteConstants.Wrapper;

public final class SQLite {
  private static boolean preferDebugLibrary = "true".equalsIgnoreCase(System.getProperty("sqlite.prefer.debug.lib"));
  private static boolean libraryLoaded = false;

  public static synchronized void setPreferDebugLibrary(boolean debug) {
    if (libraryLoaded) {
      Internal.logger.warning("SQLite: cannot prefer debug library, library already loaded");
      return;
    }
    preferDebugLibrary = debug;
  }

  public static synchronized void loadLibrary() throws SQLiteException {
    if (!libraryLoaded) {
      Throwable t = Internal.loadLibraryX();
      if (t != null)
        throw new SQLiteException(Wrapper.WRAPPER_CANNOT_LOAD_LIBRARY, "cannot load library: " + t, t);
      libraryLoaded = true;
    }
  }

  public static String getSqliteVersion() throws SQLiteException {
    loadLibrary();
    return _SQLiteSwigged.sqlite3_libversion();
  }

  static boolean isPreferDebugLibrary() {
    return preferDebugLibrary;
  }

  private SQLite() {}
}
