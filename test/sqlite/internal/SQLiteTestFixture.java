package sqlite.internal;

import junit.framework.TestCase;

import java.io.File;

import sqlite.DBGlobal;

public abstract class SQLiteTestFixture extends TestCase {
  private File myTempDir;
  private int myLastResult;
  private SWIGTYPE_p_sqlite3 myLastDb;

  protected void setUp() throws Exception {
    DBGlobal.loadLibrary();
    String name = getClass().getName();
    File dir = File.createTempFile(name.substring(name.lastIndexOf('.') + 1) + "_", ".test");
    boolean success = dir.delete();
    assert success : dir;
    success = dir.mkdirs();
    assert success : dir;
    myTempDir = dir;
  }

  protected void tearDown() throws Exception {
    if (myLastDb != null) {
      try {
        close();
      } catch (Throwable e) {
        // to heck
      }
    }
    File dir = myTempDir;
    if (dir != null) {
      myTempDir = null;
      boolean success = dir.delete();
//      assert success : dir;
    }
  }

  protected File tempDir() {
    File dir = myTempDir;
    if (dir == null) {
      assert false;
    }
    return dir;
  }

  protected String tempName(String fileName) {
    return new File(tempDir(), fileName).getAbsolutePath();
  }

  protected void open(String name, int flags) {
    int[] rc = {0};
    myLastDb = SQLiteManual.sqlite3_open_v2(name, flags, rc);
    myLastResult = rc[0];
  }

  protected int lastResult() {
    return myLastResult;
  }

  protected SWIGTYPE_p_sqlite3 lastDb() {
    return myLastDb;
  }

  protected void close() {
    long before = SQLiteSwigged.sqlite3_memory_used();
    myLastResult = SQLiteSwigged.sqlite3_close(myLastDb);
    long after = SQLiteSwigged.sqlite3_memory_used();
    System.out.println("mem: " + before + "->" + after);
    myLastDb = null;
  }

  protected void assertResult(int result) {
    assertEquals("result code", result, lastResult());
  }

  protected void exec(String sql) {
    String[] error = {null};
    myLastResult = SQLiteManual.sqlite3_exec(myLastDb, sql, error);
    if (error[0] != null) {
      System.out.println("error: " + error[0]);
    }
  }

  protected void assertDb() {
    assertNotNull(lastDb());
  }

  protected void assertOk() {
    assertResult(SQLiteConstants.Result.SQLITE_OK);
  }

  protected SWIGTYPE_p_sqlite3_stmt prepare(String sql) {
    int[] rc = {0};
    SWIGTYPE_p_sqlite3_stmt stmt = SQLiteManual.sqlite3_prepare_v2(myLastDb, sql, rc);
    myLastResult = rc[0];
    return stmt;
  }

  protected void bindLong(SWIGTYPE_p_sqlite3_stmt stmt, int index, long value) {
    myLastResult = SQLiteSwigged.sqlite3_bind_int64(stmt, index, value);
  }

  protected void step(SWIGTYPE_p_sqlite3_stmt stmt) {
    myLastResult = SQLiteSwigged.sqlite3_step(stmt);
  }

  protected void reset(SWIGTYPE_p_sqlite3_stmt stmt) {
    myLastResult = SQLiteSwigged.sqlite3_reset(stmt);
  }

  protected void finalize(SWIGTYPE_p_sqlite3_stmt stmt) {
    myLastResult = SQLiteSwigged.sqlite3_finalize(stmt);
  }

  protected void bindText(SWIGTYPE_p_sqlite3_stmt stmt, int index, String value) {
    myLastResult = SQLiteManual.sqlite3_bind_text(stmt, index, value);
  }

  protected String columnText(SWIGTYPE_p_sqlite3_stmt stmt, int column) {
    int[] rc = {0};
    String r = SQLiteManual.sqlite3_column_text(stmt, column, rc);
    myLastResult = rc[0];
    return r;
  }
}
