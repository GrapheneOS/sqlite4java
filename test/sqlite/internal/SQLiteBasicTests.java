package sqlite.internal;

import static sqlite.internal.SQLiteConstants.*;

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
}
