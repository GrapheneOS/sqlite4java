package sqlite;

import sqlite.internal.SQLiteConstants;
import sqlite.internal._SQLiteManual;
import sqlite.internal._SQLiteSwigged;

import java.io.File;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

final class Internal {
  private static final Logger logger = Logger.getLogger("sqlite");
  private static final String LOG_PREFIX = "[sqlite] ";

  private static final String BASE_LIBRARY_NAME = "sqlite";
  private static final String[] DEBUG_SUFFIXES = {"d", ""};
  private static final String[] RELEASE_SUFFIXES = {"", "d"};

  private static int lastConnectionNumber = 0;

  static synchronized int nextConnectionNumber() {
    return ++lastConnectionNumber;
  }

  static void recoverableError(Object source, String message, boolean throwAssertion) {
    logWarn(source, message);
    assert !throwAssertion : source + " " + message;
  }

  static void log(Level level, Object source, Object message, Throwable exception) {
    StringBuilder builder = new StringBuilder(LOG_PREFIX);
    if (source != null) {
      if (source instanceof Class) {
        String className = ((Class) source).getName();
        builder.append(className.substring(className.lastIndexOf('.') + 1));
      } else {
        builder.append(source);
      }
      builder.append(": ");
    }
    if (message != null)
      builder.append(message);
    logger.log(level, builder.toString(), exception);
  }

  static void logFine(Object source, Object message) {
    log(Level.FINE, source, message, null);
  }

  static void logInfo(Object source, Object message) {
    log(Level.INFO, source, message, null);
  }

  static void logWarn(Object source, Object message) {
    log(Level.WARNING, source, message, null);
  }

  static boolean isFineLogging() {
    return logger.isLoggable(Level.FINE);
  }

  static Throwable loadLibraryX() {
    if (checkLoaded() == null)
      return null;
    adjustLibraryPath();
    logFine(Internal.class, "loading library");
    logFine(Internal.class, "java.library.path=" + System.getProperty("java.library.path"));
    logFine(Internal.class, "cwd=" + new File(".").getAbsolutePath());
    String arch = System.getProperty("os.arch");
    if (arch == null) {
      logWarn(Internal.class, "os.arch is null");
      arch = "x86";
    }
    RuntimeException loadedSignal = new RuntimeException("loaded");
    Throwable bestReason = null;
    try {
      String[] suffixes = SQLite.isPreferDebugLibrary() ? DEBUG_SUFFIXES : RELEASE_SUFFIXES;
      for (String suffix : suffixes) {
        bestReason = tryLoadWithSuffix(suffix, arch, bestReason, loadedSignal);
      }
      if (bestReason == null)
        bestReason = new SQLiteException(SQLiteConstants.Wrapper.WRAPPER_WEIRD, "sqlite.Internal: lib loaded, check failed");
      return bestReason;
    } catch (RuntimeException e) {
      if (e == loadedSignal) {
        String msg = getLibraryVersionMessage();
        Internal.logInfo(Internal.class, msg);
        return null;
      } else {
        throw e;
      }
    }
  }

  private static void adjustLibraryPath() {
    Class c = Internal.class;
    String name = c.getName().replace('.', '/') + ".class";
    URL url = c.getClassLoader().getResource(name);
    if (url == null)
      return;
    String propKey = "java.library.path";
    String oldPath = System.getProperty(propKey);
    String newPath = getAdjustedLibraryPath(oldPath, url.toString());
    if (newPath != null) {
      System.setProperty(propKey, newPath);
    }
  }

  static String getAdjustedLibraryPath(String libraryPath, String classUrl) {
    String s = classUrl;
    String prefix = "jar:file:";
    if (!s.startsWith(prefix))
      return null;
    s = s.substring(prefix.length());
    int k = s.lastIndexOf('!');
    if (k < 0)
      return null;
    File jar = new File(s.substring(0, k));
    if (!jar.isFile())
      return null;
    File loadDir = jar.getParentFile();
    if (loadDir.getPath().length() == 0 || !loadDir.isDirectory())
      return null;
    if (libraryPath == null)
      libraryPath = "";
    boolean contains = false;
    char sep = File.pathSeparatorChar;
    if (libraryPath.length() > 0) {
      k = 0;
      while (k < libraryPath.length()) {
        int p = libraryPath.indexOf(sep, k);
        if (p < 0) {
          p = libraryPath.length();
        }
        if (loadDir.equals(new File(libraryPath.substring(k, p)))) {
          contains = true;
          break;
        }
        k = p + 1;
      }
    }
    String loadPath = loadDir.getPath();
    if (contains) {
      logFine(Internal.class, "not adjusting library path with [" + loadPath + "]");
      return null;
    } else {
      logFine(Internal.class, "appending to library path: [" + loadPath + "]");
      StringBuilder b = new StringBuilder(libraryPath);
      int len = b.length();
      if (len > 0 && b.charAt(len - 1) != sep)
        b.append(sep);
      b.append(loadPath);
      return b.toString();
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
    logFine(Internal.class, "trying to load " + libname);
    try {
      System.loadLibrary(libname);
    } catch (Throwable t) {
      logFine(Internal.class, "cannot load " + libname + ": " + t);
      return bestLoadFailureReason(bestReason, t);
    }
    logInfo(Internal.class, "loaded " + libname);
    LinkageError linkError = checkLoaded();
    if (linkError == null) {
      // done -- exit cycle by throwing exception
      throw loadedSignal;
    }
    logFine(Internal.class, "cannot use " + libname + ": " + linkError);
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
      getLibraryVersionMessage();
      return null;
    } catch (LinkageError e) {
      return e;
    }
  }

  private static String getLibraryVersionMessage() {
    String version = _SQLiteSwigged.sqlite3_libversion();
    String wrapper = _SQLiteManual.wrapper_version();
    return "loaded sqlite " + version + ", wrapper " + wrapper;
  }
}