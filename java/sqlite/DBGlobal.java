package sqlite;

import sqlite.internal.SQLiteConstants;
import sqlite.internal.SQLiteSwigged;

import java.util.Locale;
import java.util.logging.Logger;

public final class DBGlobal {
  private static final String BASE_LIBRARY_NAME = "sqlite";
  static final Logger logger = Logger.getLogger(BASE_LIBRARY_NAME);

  private static int lastConnectionNumber = 0;
  private static boolean libraryLoaded = false;

  static synchronized int nextConnectionNumber() {
    return ++lastConnectionNumber;
  }

  static void recoverableError(Object source, String message, boolean throwAssertion) {
    message = source + " " + message;
    assert !throwAssertion : message;
    logger.warning(message);
  }

  public static synchronized void loadLibrary() throws DBException {
    if (!libraryLoaded) {
      Throwable t = loadLibraryX();
      if (t != null)
        throw new DBException(SQLiteConstants.Wrapper.WRAPPER_CANNOT_LOAD_LIBRARY, "cannot load library", t);
      libraryLoaded = true;
    }
  }

  private static Throwable loadLibraryX() {
    Throwable x;
    if (checkLoaded())
      return null;
    String os;
    String osname = System.getProperty("os.name", "").toUpperCase(Locale.US);
    if (osname.indexOf("WINDOWS") >= 0) {
      os = "windows";
    } else if (osname.indexOf("MAC OS X") >= 0) {
      os = "mac";
    } else if (osname.indexOf("MAC") >= 0) {
      logger.warning("sqlite.DBGlobal: Mac OS version not recognized (" + osname + ")");
      os = "mac";
    } else if (osname.indexOf("LINUX") >= 0) {
      os = "linux";
    } else {
      logger.warning("sqlite.DBGlobal: OS not recognized (" + osname + ")");
      os = "linux";
    }
    String arch = System.getProperty("os.arch") + "xxx";
    Throwable t = tryLoad(BASE_LIBRARY_NAME + "-" + os + "-" + arch);
    if (t != null)
      t.printStackTrace();
    return t;
  }

  private static Throwable tryLoad(String libname) {
    try {
      System.loadLibrary(libname);
      return null;
    } catch (Throwable t) {
      return t;
    }
  }

  private static boolean checkLoaded() {
    try {
      String version = SQLiteSwigged.sqlite3_libversion();
      logger.info("loaded sqlite " + version);
      return true;
    } catch (LinkageError e) {
      return false;
    }
  }

  public static String getSqliteVersion() throws DBException {
    loadLibrary();
    return SQLiteSwigged.sqlite3_libversion();
  }
}
