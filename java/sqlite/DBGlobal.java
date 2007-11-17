package sqlite;

import sqlite.internal.SQLiteSwigged;

import java.util.logging.Logger;

public final class DBGlobal {
  static final Logger logger = Logger.getLogger("sqlite");

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

  public static synchronized void loadLibrary() {
    if (libraryLoaded)
      return;
    Throwable t;
    getSqliteVersion();
  }

  public static String getSqliteVersion() {
    loadLibrary();
    return SQLiteSwigged.sqlite3_libversion();
  }
}
