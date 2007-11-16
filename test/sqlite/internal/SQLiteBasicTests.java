package sqlite.internal;

import static sqlite.internal.SQLiteConstants.Open;
import static sqlite.internal.SQLiteConstants.Result;

import java.util.Random;
import java.io.*;

public class SQLiteBasicTests extends SQLiteTestFixture {
  private static final int RW = Open.SQLITE_OPEN_READWRITE | Open.SQLITE_OPEN_CREATE;

  public void testOpen() {
    String name = tempName("db");
    open(name, Open.SQLITE_OPEN_READONLY);
    assertNull(lastDb());
    assertResult(Result.SQLITE_CANTOPEN);

    open(name, Open.SQLITE_OPEN_READWRITE);
    assertNull(lastDb());
    assertResult(Result.SQLITE_CANTOPEN);

    open(name, RW);
    assertDb();
    assertOk();

    close();
    assertOk();
  }

  public void testOpenMemory() {
    open(":memory:", Open.SQLITE_OPEN_READWRITE);
    assertDb();
    assertOk();

    close();
    assertOk();
  }

  public void testOpenReadOnly() {
    String name = tempName("db");
    open(name, RW);
    assertDb();
    exec("create table x (x)");
    assertOk();
    close();
    assertOk();
    open(name, Open.SQLITE_OPEN_READONLY);
    exec("select * from x");
    assertOk();

    exec("insert into x values (1)");
    assertResult(Result.SQLITE_READONLY);

    exec("drop table x");
    assertResult(Result.SQLITE_READONLY);

    exec("begin immediate");
    assertResult(Result.SQLITE_READONLY);
  }

  public void testPrepareBindStepResetFinalize() {
    String name = tempName("db");
    open(name, RW);
    assertDb();

    exec("create table x (x)");
    assertOk();

    SWIGTYPE_p_sqlite3_stmt stmt = prepare("insert into x values (?)");
    assertOk();
    assertNotNull(stmt);

    exec("begin immediate");
    assertOk();

    for (int i = 0; i < 10; i++) {
      bindLong(stmt, 1, i);
      assertOk();

      step(stmt);
      assertResult(Result.SQLITE_DONE);

      reset(stmt);
      assertOk();
    }

    exec("commit");
    assertOk();

    finalize(stmt);
    assertOk();

    close();
  }

  public void testUnparseableSql() {
    open(":memory:", Open.SQLITE_OPEN_READWRITE);
    SWIGTYPE_p_sqlite3_stmt stmt = prepare("habahaba");
    assertNull(stmt);
    assertResult(Result.SQLITE_ERROR);
  }

  public void testStatementSurvivesSchemaChange() {
    open(tempName("db"), RW);
    exec("create table x (x)");
    SWIGTYPE_p_sqlite3_stmt stmt = prepare("insert into x (x) values (?)");
    assertOk();
    exec("alter table x add column y");
    assertOk();
    bindLong(stmt, 1, 100L);
    assertOk();
    step(stmt);
    assertResult(Result.SQLITE_DONE);
    finalize(stmt);
    assertOk();
  }

  public void testBindText() {
    open(tempName("db"), RW);
    exec("create table x (x)");
    SWIGTYPE_p_sqlite3_stmt stmt = prepare("insert into x (x) values (?)");
    assertOk();
    bsr(stmt, "");
    bsr(stmt, "short text");
    String v = garbageString();
    bsr(stmt, v);
    finalize(stmt);
    close();
  }

  public void testTextBindAndColumn() {
    String name = tempName("db");
    open(name, RW);
//    exec("PRAGMA encoding = \"UTF-16\";"); 
    exec("create table x (x)");
    SWIGTYPE_p_sqlite3_stmt stmt = prepare("insert into x (x) values (?)");
    String v = garbageString();
    bsr(stmt, v);
    finalize(stmt);
    close();

    open(name, Open.SQLITE_OPEN_READONLY);
    stmt = prepare("select x from x");
    assertOk();
    step(stmt);
    assertResult(Result.SQLITE_ROW);
    String v2 = columnText(stmt, 0);
    assertOk();
    step(stmt);
    assertResult(Result.SQLITE_DONE);

    write(v, "/tmp/v1");
    write(v2, "/tmp/v2");
//    write(v, "/tmp/v1.utf8", "UTF-8");
//    write(v2, "/tmp/v2.utf8", "UTF-8");

//    assertEquals(v.length(), v2.length());


    assertEquals(v, v2);
  }

  private void write(String s, String f) {
    try {
      FileOutputStream out = new FileOutputStream(new File(f));
      BufferedOutputStream bout = new BufferedOutputStream(out);
      PrintWriter writer = new PrintWriter(bout);
      int len = s.length();
      for (int i = 0; i < len; i = s.offsetByCodePoints(i, 1))
        writer.println("0x" + Integer.toHexString(s.codePointAt(i)));
      writer.close();
      bout.close();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String garbageString() {
    StringBuilder b = new StringBuilder();
    Random r = new Random();
    for (int i = 0; i < 1000; i++) {
      int c = r.nextInt(0x110000);
      if (c >= 0xD800 && c < 0xDFFF) {
        // surrogate
        continue;
      }
//      int c = r.nextInt(0x110000);
      b.appendCodePoint(c);
    }
    b.setCharAt(b.length() / 2, (char) 0);
//    b.appendCodePoint(0x1D11E);
//    b.appendCodePoint(0x10000);
    String v = b.toString();
    return v;
  }

  private void bsr(SWIGTYPE_p_sqlite3_stmt stmt, String value) {
    bindText(stmt, 1, value);
    assertOk();
    step(stmt);
    assertResult(Result.SQLITE_DONE);
    reset(stmt);
    assertOk();
  }

}
