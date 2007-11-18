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
    Throwable bestReason = null;
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
    Throwable t;
    t = tryLoad(BASE_LIBRARY_NAME + "-" + os + "-" + arch);
    if (t == null && checkLoaded()) {
      return null;
    }
    bestReason = bestLoadFailureReason(bestReason, t);
    t = tryLoad(BASE_LIBRARY_NAME + "-" + os);
    if (t == null && checkLoaded()) {
      return null;
    }
    bestReason = bestLoadFailureReason(bestReason, t);
    t = tryLoad(BASE_LIBRARY_NAME + "-" + arch);
    if (t == null && checkLoaded()) {
      return null;
    }
    bestReason = bestLoadFailureReason(bestReason, t);
    t = tryLoad(BASE_LIBRARY_NAME);
    if (t == null && checkLoaded()) {
      return null;
    }
    bestReason = bestLoadFailureReason(bestReason, t);
    if (bestReason == null)
      bestReason = new DBException(SQLiteConstants.Wrapper.WRAPPER_WEIRD, "sqlite.DBGlobal: lib loaded, check failed");
    return bestReason;
  }

  private static Throwable bestLoadFailureReason(Throwable t1, Throwable t2) {
    if (t1 == null)
      return t2;
    if (t2 == null)
      return t1;
    if (!(t1 instanceof java.lang.UnsatisfiedLinkError))
      return t1;
    if (t2 instanceof java.lang.UnsatisfiedLinkError)
      return t1;
    return t2;
  }

  private static Throwable tryLoad(String libname) {
    logger.info("sqlite.DBGlobal: trying to load " + libname);
    try {
      System.loadLibrary(libname);
    } catch (Throwable t) {
      logger.info("sqlite.DBGlobal: cannot load " + libname + ": " + t);
      return t;
    }
    logger.info("sqlite.DBGlobal: loaded " + libname);
    return null;
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
