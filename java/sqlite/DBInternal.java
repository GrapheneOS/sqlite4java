package sqlite;

import sqlite.internal.SQLiteConstants;
import sqlite.internal.SQLiteManual;
import sqlite.internal.SQLiteSwigged;

import java.io.File;
import java.util.logging.Logger;

final class DBInternal {
  static final Logger logger = Logger.getLogger("sqlite");

  private static final String BASE_LIBRARY_NAME = "sqlite";
  private static final String[] DEBUG_SUFFIXES = {"d", ""};
  private static final String[] RELEASE_SUFFIXES = {"", "d"};

  private static int lastConnectionNumber = 0;

  static synchronized int nextConnectionNumber() {
    return ++lastConnectionNumber;
  }

  static void recoverableError(Object source, String message, boolean throwAssertion) {
    message = source + " " + message;
    assert !throwAssertion : message;
    logger.warning(message);
  }

  static Throwable loadLibraryX() {
    if (checkLoaded() == null)
      return null;
    logger.fine("java.library.path=" + System.getProperty("java.library.path"));
    logger.fine("cwd=" + new File(".").getAbsolutePath());
    String arch = System.getProperty("os.arch");
    if (arch == null) {
      logger.warning("sqlite.DBInternal: os.arch is null");
      arch = "x86";
    }
    RuntimeException loadedSignal = new RuntimeException("loaded");
    Throwable bestReason = null;
    try {
      String[] suffixes = DBGlobal.isPreferDebugLibrary() ? DEBUG_SUFFIXES : RELEASE_SUFFIXES;
      for (String suffix : suffixes) {
        bestReason = tryLoadWithSuffix(suffix, arch, bestReason, loadedSignal);
      }
      if (bestReason == null)
        bestReason = new DBException(SQLiteConstants.Wrapper.WRAPPER_WEIRD, "sqlite.DBInternal: lib loaded, check failed");
      return bestReason;
    } catch (RuntimeException e) {
      if (e == loadedSignal) {
        // done
        return null;
      } else {
        throw e;
      }
    }
  }

  private static Throwable tryLoadWithSuffix(String suffix, String arch, Throwable bestReason, RuntimeException loadedSignal) {
    Throwable t = bestReason;
    t = tryLoad(BASE_LIBRARY_NAME + "w" + arch + suffix, t, loadedSignal);
    if (arch.indexOf("64") > 0) {
      t = tryLoad(BASE_LIBRARY_NAME + "wx86" + suffix, t, loadedSignal);
    }
    t = tryLoad(BASE_LIBRARY_NAME + "w" + suffix, t, loadedSignal);
    t = tryLoad(BASE_LIBRARY_NAME + suffix, t, loadedSignal);
    return t;
  }

  private static Throwable tryLoad(String libname, Throwable bestReason, RuntimeException loadedSignal) {
    logger.fine("sqlite.DBInternal: trying to load " + libname);
    try {
      System.loadLibrary(libname);
    } catch (Throwable t) {
      logger.fine("sqlite.DBInternal: cannot load " + libname + ": " + t);
      return bestLoadFailureReason(bestReason, t);
    }
    logger.info("sqlite.DBInternal: loaded " + libname);
    LinkageError linkError = checkLoaded();
    if (linkError == null) {
      // done -- exit cycle by throwing exception
      throw loadedSignal;
    }
    logger.fine("sqlite.DBInternal: cannot use " + libname + ": " + linkError);
    return bestLoadFailureReason(bestReason, linkError);
  }

  /**
   * This method is used to decide which exception describes the real problem. If the file is simply not found
   * (which we gather from the fact that message contains "java.library.path"), then it may or may not be the
   * real reason. If there is another exception which has something else to say, it's given the priority.
   */
  private static Throwable bestLoadFailureReason(Throwable t1, Throwable t2) {
    if (t1 == null)
      return t2;
    if (t2 == null)
      return t1;
    String m1 = t1.getMessage();
    if (m1 == null || !m1.contains("java.library.path"))
      return t1;
    String m2 = t2.getMessage();
    if (m2 != null && m2.contains("java.library.path"))
      return t1;
    return t2;
  }

  private static LinkageError checkLoaded() {
    try {
      String version = SQLiteSwigged.sqlite3_libversion();
      String wrapper = SQLiteManual.wrapper_version();
      logger.info("sqlite.DBInternal: loaded sqlite " + version + ", wrapper " + wrapper);
      return null;
    } catch (LinkageError e) {
      return e;
    }
  }
}