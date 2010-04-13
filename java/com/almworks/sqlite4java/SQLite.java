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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.almworks.sqlite4java.SQLiteConstants.Wrapper;

public final class SQLite {
  private static boolean preferDebugLibrary = "true".equalsIgnoreCase(System.getProperty("sqlite.prefer.debug.lib"));
  private static boolean libraryLoaded = false;
  private static String jarVersion = null;

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

  public static synchronized String getJarVersion() {
    if (jarVersion == null) {
      String name = SQLite.class.getName().replace('.', '/') + ".class";
      URL url = SQLite.class.getClassLoader().getResource(name);
      if (url == null)
        return null;
      String s = url.toString();
      if (!s.startsWith("jar:"))
        return null;
      int k = s.lastIndexOf('!');
      if (k < 0)
        return null;
      s = s.substring(0, k + 1) + "/META-INF/MANIFEST.MF";
      InputStream input = null;
      Manifest manifest = null;
      try {
        input = new URL(s).openStream();
        manifest = new Manifest(input);
      } catch (IOException e) {
        Internal.logWarn(SQLite.class, "error reading jar manifest" + e);
      } finally {
        try {
          if (input != null) input.close();
        } catch (IOException e) { /**/}
      }
      if (manifest != null) {
        Attributes attr = manifest.getMainAttributes();
        jarVersion = attr.getValue("Implementation-Version");
      }
    }
    if (jarVersion == null) {
      Internal.logWarn(SQLite.class, "unknown jar version");
    }
    return jarVersion;
  }

  private SQLite() {
  }

  public static void main(String[] args) {
    if (args.length > 0 && "-d".equals(args[0])) {
      // debug
      Logger.getLogger("com.almworks.sqlite4java").setLevel(Level.FINE);
      Handler[] handlers = Logger.getLogger("").getHandlers();
      for (Handler handler : handlers) {
        if (handler instanceof ConsoleHandler) handler.setLevel(Level.FINE);
      }
    } else {
      Logger.getLogger("com.almworks.sqlite4java").setLevel(Level.SEVERE);
    }
    String v = getJarVersion();
    if (v == null) v = "(UNKNOWN VERSION)";
    System.out.println("sqlite4java " + v);
    Throwable t = libraryLoaded ? null : Internal.loadLibraryX();
    if (t != null) {
      System.out.println("Error: cannot load SQLite");
      t.printStackTrace();
    } else {
      try {
        System.out.println("SQLite " + getSQLiteVersion());
      } catch (SQLiteException e) {
        e.printStackTrace();
      }
    }
  }
}
