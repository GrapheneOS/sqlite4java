package sqlite.internal;

import static sqlite.internal.SQLiteConstants.Open;
import static sqlite.internal.SQLiteConstants.Result;

import java.util.Random;

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

    assertEquals(v, v2);
  }

  private static String garbageString() {
    StringBuilder b = new StringBuilder();
    Random r = new Random();
    for (int i = 0; i < 100000; i++) {
      int c = r.nextInt(0x110000);
      b.appendCodePoint(c);
    }
    b.setCharAt(b.length() / 2, (char)0);
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
