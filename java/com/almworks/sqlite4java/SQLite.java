package com.almworks.sqlite4java;

import static com.almworks.sqlite4java.SQLiteConstants.Wrapper;

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

  public static String getSQLiteVersion() throws SQLiteException {
    loadLibrary();
    return _SQLiteSwigged.sqlite3_libversion();
  }

  public static int getSQLiteVersionNumber() throws SQLiteException {
    loadLibrary();
    return _SQLiteSwigged.sqlite3_libversion_number();
  }

  public static boolean isThreadSafe() throws SQLiteException {
    loadLibrary();
    return _SQLiteSwigged.sqlite3_threadsafe() != 0;
  }

  public static boolean isComplete(String sql) throws SQLiteException {
    loadLibrary();
    return _SQLiteSwigged.sqlite3_complete(sql) != 0;
  }

  public static long getMemoryUsed() throws SQLiteException {
    loadLibrary();
    return _SQLiteSwigged.sqlite3_memory_used();
  }

  public static long getMemoryHighwater(boolean reset) throws SQLiteException {
    loadLibrary();
    return _SQLiteSwigged.sqlite3_memory_highwater(reset ? 1 : 0);
  }

  public static void releaseMemory(int m) throws SQLiteException {
    loadLibrary();
    int rc = _SQLiteSwigged.sqlite3_release_memory(m);
    if (rc != SQLiteConstants.Result.SQLITE_OK) {
      throw new SQLiteException(rc, "");
    }
  }

  public static void setSoftHeapLimit(int limit) throws SQLiteException {
    loadLibrary();
    _SQLiteSwigged.sqlite3_soft_heap_limit(limit);
  }

  public static void setSharedCache(boolean enabled) throws SQLiteException {
    loadLibrary();
    _SQLiteSwigged.sqlite3_enable_shared_cache(enabled ? 1 : 0);
  }

  private SQLite() {
  }
}
