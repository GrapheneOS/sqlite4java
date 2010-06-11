/*
 * Copyright 2010 ALM Works Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.almworks.sqlite4java;

import java.io.File;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

final class Internal {
  private static final Logger logger = Logger.getLogger("com.almworks.sqlite4java");
  private static final String LOG_PREFIX = "[sqlite] ";

  private static final String BASE_LIBRARY_NAME = "sqlite4java";
  private static final String[] DEBUG_SUFFIXES = {"-d", ""};
  private static final String[] RELEASE_SUFFIXES = {"", "-d"};

  private static final AtomicInteger lastConnectionNumber = new AtomicInteger(0);

  static int nextConnectionNumber() {
    return lastConnectionNumber.incrementAndGet();
  }

  static void recoverableError(Object source, String message, boolean throwAssertion) {
    logWarn(source, message);
    assert !throwAssertion : source + " " + message;
  }

  static void log(Level level, Object source, Object message, Throwable exception) {
    if (!logger.isLoggable(level)) {
      return;
    }
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
    String defaultPath = getDefaultLibPath();
    logFine(Internal.class, "loading library");
    logFine(Internal.class, "java.library.path=" + System.getProperty("java.library.path"));
    logFine(Internal.class, "cwd=" + new File(".").getAbsolutePath());
    logFine(Internal.class, "defaultLibPath=" + (defaultPath == null ? "null " : new File(defaultPath).getAbsolutePath()));
    String os = getOs();
    String arch = getArch(os);
    RuntimeException loadedSignal = new RuntimeException("loaded");
    Throwable bestReason = null;
    try {
      String[] suffixes = SQLite.isDebugBinaryPreferred() ? DEBUG_SUFFIXES : RELEASE_SUFFIXES;
      for (String suffix : suffixes) {
        bestReason = tryLoadWithSuffix(suffix, os, arch, bestReason, loadedSignal, defaultPath);
      }
      if (bestReason == null)
        bestReason = new SQLiteException(SQLiteConstants.WRAPPER_WEIRD, "sqlite4java.Internal: lib loaded, check failed");
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

  private static String getArch(String os) {
    String arch = System.getProperty("os.arch");
    if (arch == null) {
      logWarn(Internal.class, "os.arch is null");
      arch = "x86";
    } else {
      arch = arch.toLowerCase(Locale.US);
      if ("win32".equals(os) && "amd64".equals(arch)) {
        arch = "x64";
      }
    }
    logFine(Internal.class, "os.arch=" + arch);
    return arch;
  }

  private static String getOs() {
    String osname = System.getProperty("os.name");
    String os;
    if (osname == null) {
      logWarn(Internal.class, "os.name is null");
      os = "linux";
    } else {
      osname = osname.toLowerCase(Locale.US);
      if (osname.startsWith("mac") || osname.startsWith("darwin") || osname.startsWith("os x")) {
        os = "osx";
      } else if (osname.startsWith("windows")) {
        os = "win32";
      } else {
        os = "linux";
      }
    }
    logFine(Internal.class, "os.name=" + osname + "; os=" + os);
    return os;
  }

  private static String getDefaultLibPath() {
    Class c = Internal.class;
    String name = c.getName().replace('.', '/') + ".class";
    URL url = c.getClassLoader().getResource(name);
    if (url == null)
      return null;
    String propKey = "java.library.path";
    String oldPath = System.getProperty(propKey);
    return getDefaultLibPath(oldPath, url.toString());
  }

  static String getDefaultLibPath(String libraryPath, String classUrl) {
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
      return null;
    } else {
//      logFine(Internal.class, "appending to library path: [" + loadPath + "]");
      return loadPath;
    }
  }

  private static Throwable tryLoadWithSuffix(String suffix, String os, String arch, Throwable bestReason, RuntimeException loadedSignal, String defaultPath) {
    Throwable t = bestReason;
    t = tryLoad(BASE_LIBRARY_NAME + "-" + os + "-" + arch + suffix, t, loadedSignal, defaultPath);
    if (arch.equals("x86_64") || arch.equals("x64")) {
      t = tryLoad(BASE_LIBRARY_NAME + "-" + os + "-amd64" + suffix, t, loadedSignal, defaultPath);
    } else if (arch.equals("powerpc")) {
      t = tryLoad(BASE_LIBRARY_NAME + "-" + os + "-ppc" + suffix, t, loadedSignal, defaultPath);
    } else if (arch.equals("x86")) {
      t = tryLoad(BASE_LIBRARY_NAME + "-" + os + "-i386" + suffix, t, loadedSignal, defaultPath);
    } else if (arch.equals("i386")) {
      t = tryLoad(BASE_LIBRARY_NAME + "-" + os + "-x86" + suffix, t, loadedSignal, defaultPath);
    }
    t = tryLoad(BASE_LIBRARY_NAME + "-" + os + suffix, t, loadedSignal, defaultPath);
    t = tryLoad(BASE_LIBRARY_NAME + suffix, t, loadedSignal, defaultPath);
    return t;
  }

  private static Throwable tryLoad(String libname, Throwable bestReason, RuntimeException loadedSignal, String defaultPath) {
    Throwable t = bestReason;
    t = tryLoadFromSystemPath(libname, t, loadedSignal);
    if (defaultPath != null)
      t = tryLoadFromDefaultPath(libname, t, loadedSignal, defaultPath);
    return t;
  }

  private static Throwable tryLoadFromDefaultPath(String libname, Throwable bestReason, RuntimeException loadedSignal, String defaultPath) {
    String libFile = System.mapLibraryName(libname);
    File lib = new File(new File(defaultPath), libFile);
    if (!lib.isFile() || !lib.canRead())
      return bestReason;
    String logname = libname + " from " + lib;
    logFine(Internal.class, "trying to load " + logname);
    try {
      System.load(lib.getAbsolutePath());
    } catch (Throwable t) {
      logFine(Internal.class, "cannot load " + logname + ": " + t);
      return bestLoadFailureReason(bestReason, t);
    }
    return verifyLoading(bestReason, loadedSignal, logname);
  }

  private static Throwable tryLoadFromSystemPath(String libname, Throwable bestReason, RuntimeException loadedSignal) {
    logFine(Internal.class, "trying to load " + libname);
    try {
      System.loadLibrary(libname);
    } catch (Throwable t) {
      logFine(Internal.class, "cannot load " + libname + ": " + t);
      return bestLoadFailureReason(bestReason, t);
    }
    return verifyLoading(bestReason, loadedSignal, libname);
  }

  private static Throwable verifyLoading(Throwable bestReason, RuntimeException loadedSignal, String logname) {
    logInfo(Internal.class, "loaded " + logname);
    LinkageError linkError = checkLoaded();
    if (linkError == null) {
      // done -- exit cycle by throwing exception
      throw loadedSignal;
    }
    logFine(Internal.class, "cannot use " + logname + ": " + linkError);
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