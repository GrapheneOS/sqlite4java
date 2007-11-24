package sqlite.internal;

import junit.framework.TestCase;
import sqlite.SQLite;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.logging.*;

public abstract class SQLiteTestFixture extends TestCase {
  private File myTempDir;
  private int myLastResult;
  private SWIGTYPE_p_sqlite3 myLastDb;
  private final boolean myAutoLoad;

  static {
    installFormatter(Logger.getLogger("sqlite"), new DecentFormatter(), Level.FINE);
  }

  private static void installFormatter(Logger logger, Formatter formatter, Level level) {
    logger.setLevel(level);
    Handler[] handlers = logger.getHandlers();
    if (handlers != null) {
      for (Handler handler : handlers) {
        handler.setFormatter(formatter);
        handler.setLevel(level);
      }
    }
    Logger parent = logger.getParent();
    if (parent != null)
      installFormatter(parent, formatter, level);
  }

  public SQLiteTestFixture(boolean autoLoad) {
    myAutoLoad = autoLoad;
  }

  protected void setUp() throws Exception {
    if (myAutoLoad) {
      SQLite.loadLibrary();
    }
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
    myLastDb = _SQLiteManual.sqlite3_open_v2(name, flags, rc);
    myLastResult = rc[0];
  }

  protected int lastResult() {
    return myLastResult;
  }

  protected SWIGTYPE_p_sqlite3 lastDb() {
    return myLastDb;
  }

  protected void close() {
    long before = _SQLiteSwigged.sqlite3_memory_used();
    myLastResult = _SQLiteSwigged.sqlite3_close(myLastDb);
    long after = _SQLiteSwigged.sqlite3_memory_used();
    System.out.println("mem: " + before + "->" + after);
    myLastDb = null;
  }

  protected void assertResult(int result) {
    assertEquals("result code", result, lastResult());
  }

  protected void exec(String sql) {
    String[] error = {null};
    myLastResult = _SQLiteManual.sqlite3_exec(myLastDb, sql, error);
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
    SWIGTYPE_p_sqlite3_stmt stmt = _SQLiteManual.sqlite3_prepare_v2(myLastDb, sql, rc);
    myLastResult = rc[0];
    return stmt;
  }

  protected void bindLong(SWIGTYPE_p_sqlite3_stmt stmt, int index, long value) {
    myLastResult = _SQLiteSwigged.sqlite3_bind_int64(stmt, index, value);
  }

  protected void step(SWIGTYPE_p_sqlite3_stmt stmt) {
    myLastResult = _SQLiteSwigged.sqlite3_step(stmt);
  }

  protected void reset(SWIGTYPE_p_sqlite3_stmt stmt) {
    myLastResult = _SQLiteSwigged.sqlite3_reset(stmt);
  }

  protected void finalize(SWIGTYPE_p_sqlite3_stmt stmt) {
    myLastResult = _SQLiteSwigged.sqlite3_finalize(stmt);
  }

  protected void bindText(SWIGTYPE_p_sqlite3_stmt stmt, int index, String value) {
    myLastResult = _SQLiteManual.sqlite3_bind_text(stmt, index, value);
  }

  protected String columnText(SWIGTYPE_p_sqlite3_stmt stmt, int column) {
    int[] rc = {0};
    String r = _SQLiteManual.sqlite3_column_text(stmt, column, rc);
    myLastResult = rc[0];
    return r;
  }

  protected static String garbageString(int count) {
    StringBuilder b = new StringBuilder();
    Random r = new Random();
    for (int i = 0; i < count; i++) {
      if (i == 500) {
        b.appendCodePoint(0);
        continue;
      }
      int c = r.nextInt(0x110000);
      if (c >= 0xD800 && c <= 0xDFFF) {
        // surrogate
        continue;
      }
      if (c == 0xFFFF || c == 0xFFFE || c == 0xFEFF) {
        continue;
      }
//      int c = r.nextInt(0x110000);
      b.appendCodePoint(c);
    }

//    b.appendCodePoint(0x1D11E);
//    b.appendCodePoint(0x10000);
    String v = b.toString();
    return v;
  }

  private static class DecentFormatter extends Formatter {
    private static final DateFormat dateFormat = new SimpleDateFormat("yyMMdd-HHmmss");

    public String format(LogRecord record) {
      StringBuilder sb = new StringBuilder();
      sb.append(dateFormat.format(new Date(record.getMillis())));
      sb.append(' ');
      sb.append(record.getLevel().getLocalizedName());
      sb.append(' ');
      sb.append(record.getMessage());
      if (record.getThrown() != null) {
        try {
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          record.getThrown().printStackTrace(pw);
          pw.close();
          sb.append('\n');
          sb.append(sw.toString());
        } catch (Exception ex) {
        }
      }
      sb.append('\n');
      return sb.toString();
    }
  }
}
