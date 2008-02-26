package sqlite;

import static sqlite.SQLiteConstants.Wrapper;

public final class SQLite {
  private static boolean preferDebugLibrary = "true".equalsIgnoreCase(System.getProperty("sqlite.prefer.debug.lib"));
  private static boolean libraryLoaded = false;

  public static synchronized void setPreferDebugLibrary(boolean debug) {
    if (libraryLoaded) {
      Internal.logWarn(SQLite.class, "cannot set library preference, library already loaded");
      return;
    }
    preferDebugLibrary = debug;
  }

  public static synchronized boolean isPreferDebugLibrary() {
    return preferDebugLibrary;
  }

  public static synchronized void loadLibrary() throws SQLiteException {
    if (!libraryLoaded) {
      Throwable t = Internal.loadLibraryX();
      if (t != null)
        throw new SQLiteException(Wrapper.WRAPPER_CANNOT_LOAD_LIBRARY, "cannot load library: " + t, t);
      libraryLoaded = true;
      int threadSafe = _SQLiteSwigged.sqlite3_threadsafe();
      if (threadSafe == 0) {
        Internal.logWarn(SQLite.class, "library is not thread-safe");
      }
    }
  }

  public static String getSqliteVersion() throws SQLiteException {
    loadLibrary();
    return _SQLiteSwigged.sqlite3_libversion();
  }

  private SQLite() {}
}
