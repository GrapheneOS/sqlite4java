# Download latest version:

  * [sqlite4java-392](https://d1.almworks.com/.files/sqlite4java/sqlite4java-392.zip) with **SQLite 3.8.7**, Windows/Linux/Mac OS X/Android binaries
  * [OSGi bundle 1.0.392](https://d1.almworks.com/.files/sqlite4java/com.almworks.sqlite4java-1.0.392.jar) with sqlite4java-392

Files for previous versions are available in the Downloads section.

## Useful links:

* [Javadocs](http://almworks.com/sqlite4java/javadoc/index.html)
* [SQLite](http://sqlite.org)
* [Google Group - sqlite4java](https://groups.google.com/forum/#!forum/sqlite4java)

---


# About sqlite4java

Sqlite4java is a minimalistic, open-source Java wrapper for SQLite. [SQLite](http://sqlite.org) is a free, compact, robust, embeddable SQL database engine. **sqlite4java** is built with the purpose to provide high-performance, low-garbage interface to SQLite for desktop Java applications.

Sqlite4java is **not a JDBC driver**. Access to the database is made through the custom interfaces of `com.almworks.sqlite4java` package. Tighter integration with SQLite offers better performance and features not available through JDBC interfaces.

Sqlite4java is built for use on Windows, Linux, Mac OS X and Android, although you can try to compile it on other platforms. Required JRE version is 1.5. SQLite is pre-compiled and distributed along with the Java classes as dynamic JNI libraries.

Sqlite4java is a stable library that we (ALM Works) use in our production applications. The API may not support some of the SQLite functions, but most functionality is covered. Feel free to request improvements or suggest patches.

  * [Getting Started](https://bitbucket.org/almworks/sqlite4java/wiki/GettingStarted)
  * [Comparison to Other Java Wrappers for SQLite](https://bitbucket.org/almworks/sqlite4java/wiki/ComparisonToOtherWrappers)
  * [Javadoc](http://almworks.com/sqlite4java/javadoc/index.html)
  * [Using in a Maven 2 project](https://bitbucket.org/almworks/sqlite4java/wiki/UsingWithMaven)

# Supported Platforms

  * Windows i386/x64
  * Linux i686/amd64
  * Mac OS X 10.5 or later (i686/x64)
  * Android x86/armv7/armv5

# Features

  * **Thin JNI-based wrapper** for [SQLite C Interface](http://sqlite.org/c3ref/funclist.html). Most of SQLite's user functions (not extender functions) are either already provided by the library or can be easily added.
  * **Single-threaded model** - each SQLite connection is confined to a single thread, all calls must come from that thread. Application may open several connections to the same database from different threads. Along with the Serializable isolation level from SQLite, this feature facilitates writing very clean and predictable code.
  * **Bulk retrieval** from SELECT statements, greatly improving speed and garbage rate via minimizing the number of JNI calls to `step()` and `column...()` methods. See  [SQLiteStatement.loadInts()](http://almworks.com/sqlite4java/javadoc/index.html) for example.
  * **Interruptible statements** support allows to cancel a long-running query or update. See [SQLiteConnection.interrupt()](http://almworks.com/sqlite4java/javadoc/index.html).
  * **Long array binding** allows to represent a `long[]` Java array as an SQL table. Table lookup is optimized if you specify that the array is sorted and/or has unique values. See [SQLiteLongArray](http://almworks.com/sqlite4java/javadoc/index.html).
  * **Incremental BLOB I/O** maps to `sqlite3_blob...` methods, which provide means to read/write portions of a large BLOB. See [SQLiteBlob](http://almworks.com/sqlite4java/javadoc/index.html).
  * **BLOBs as streams** - you can bind parameter as an `OutputStream` and read column value as `InputStream`. See [SQLiteStatement.bindStream()](http://almworks.com/sqlite4java/javadoc/index.html) for example.
  * **Job queue implementation** lets you queue database jobs in a multi-threaded application, to be executed one-by-one in a dedicated database thread. See [JobQueue](https://bitbucket.org/almworks/sqlite4java/wiki/JobQueue).
  * **SQL Profiler** collects statistics on the executed SQL.
  * **Backup API** support lets you use SQLite's hot backup feature. See [SQLiteConnection.initializeBackup()](http://almworks.com/sqlite4java/javadoc/index.html).


# License

**sqlite4java** is licensed under Apache License 2.0. 

Contact info@almworks.com if you'd like to have it licensed under different terms.


# Contributors

* [ALM Works](http://almworks.com) team
* Ivan Voronov