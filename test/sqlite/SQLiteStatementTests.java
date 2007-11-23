package sqlite;

import sqlite.internal.SWIGTYPE_p_sqlite3_stmt;
import sqlite.internal._SQLiteSwigged;

public class SQLiteStatementTests extends SQLiteConnectionFixture {
  public void testPrepareBad() throws SQLiteException {
    SQLiteConnection connection = fileDb();
    connection.open();
    try {
      connection.prepare("wrong sql");
      fail("prepared wrong sql");
    } catch (SQLiteException e) {
      // ok
    }
    try {
      connection.prepare(null);
      fail("prepared null");
    } catch (SQLiteException e) {
      //ok
    }
    try {
      connection.prepare("   ");
      fail("prepared empty");
    } catch (SQLiteException e) {
      // ok
    }
    try {
      connection.prepare("");
      fail("prepared empty");
    } catch (SQLiteException e) {
      // ok
    }
    try {
      connection.prepare("select * from x");
      fail("prepared invalid");
    } catch (SQLiteException e) {
      // ok
    }
  }

  public void testStatementLifecycle() throws SQLiteException {
    SQLiteConnection connection = fileDb();
    connection.open();
    connection.exec("create table x (x)");
    String sql = "insert into x values (?)";
    SQLiteStatement st1 = connection.prepare(sql, false);
    SQLiteStatement st2 = connection.prepare(sql, false);
    SQLiteStatement st3 = connection.prepare(sql, true);
    st3.dispose();
    SQLiteStatement st4 = connection.prepare(sql, true);
    assertNotSame(st1, st2);
    assertNotSame(st1, st3);
    assertNotSame(st1, st4);
    assertNotSame(st2, st3);
    assertNotSame(st2, st4);
    assertSame(st3, st4);
    assertEquals(3, connection.getStatementCount());
    assertFalse(st1.isDisposed());
    assertFalse(st2.isDisposed());
    assertFalse(st3.isDisposed());
    st1.dispose();
    assertTrue(st1.isDisposed());
    assertFalse(st2.isDisposed());
    assertFalse(st3.isDisposed());
    connection.dispose();
    assertTrue(st2.isDisposed());
    assertTrue(st3.isDisposed());
  }

  public void testCloseFromAnotherThread() throws SQLiteException, InterruptedException {
    final SQLiteConnection connection = fileDb().open().exec("create table x (x)");
    final SQLiteStatement st = connection.prepare("insert into x values (?)");
    assertFalse(st.isDisposed());

    Thread closer = new Thread() {
      public void run() {
        try {
          st.dispose();
          fail("disposed " + st + " from another thread");
        } catch (AssertionError e) {
          // ok
        }

        connection.dispose();
      }
    };
    closer.start();
    closer.join();
    assertFalse(connection.isOpen());

    // cannot dispose from another thread actually:
    assertFalse(st.isDisposed());


    connection.open();
    assertTrue(connection.isOpen());
  }

  public void testCloseFromCorrectThreadWithOpenStatement() throws SQLiteException {
    SQLiteConnection connection = fileDb().open().exec("create table x (x, y)");
    connection.exec("insert into x values (2, '3');");
    SQLiteStatement st = connection.prepare("select x, y from x");
    st.step();

    assertTrue(st.hasRow());
    connection.dispose();

    assertFalse(connection.isOpen());
    assertTrue(st.isDisposed());
    assertFalse(st.hasRow());
  }

  public void testBadBindIndexes() throws SQLiteException {
    SQLiteConnection connection = fileDb().open().exec("create table x (x, y)");
    SQLiteStatement st = connection.prepare("insert into x values (?, ?)");
    try {
      st.bind(0, "0");
      fail("bound to 0");
    } catch (SQLiteException e) {
      // norm
    }
    st.bind(1, "1");
    st.bind(2, "2");
    try {
      st.bind(3, "3");
      fail("bound to 3");
    } catch (SQLiteException e) {
      // norm
    }
    try {
      st.bind(-99999, "-99999");
      fail("bound to 0-99999");
    } catch (SQLiteException e) {
      // norm
    }
    try {
      st.bind(99999, "99999");
      fail("bound to 99999");
    } catch (SQLiteException e) {
      // norm
    }
  }

  public void testBadColumnUse() throws SQLiteException {
    SQLiteConnection connection = fileDb().open().exec("create table x (x, y)");
    connection.exec("insert into x values (2, '3');");
    SQLiteStatement st = connection.prepare("select x, y from x");

    assertFalse(st.hasRow());
    try {
      st.columnInt(0);
      fail("got column before step");
    } catch (SQLiteException e) {
      // norm
    }

    boolean r = st.step();
    assertTrue(r);
    assertTrue(st.hasRow());

    st.columnInt(0);
    st.columnString(1);

    try {
      st.columnInt(-1);
      fail("got column -1");
    } catch (SQLiteException e) {
      // norm
    }
    try {
      st.columnInt(-999999);
      fail("got column -999999");
    } catch (SQLiteException e) {
      // norm
    }
    try {
      st.columnInt(3);
      fail("got column 3");
    } catch (SQLiteException e) {
      // norm
    }
    try {
      st.columnInt(999999);
      fail("got column 999999");
    } catch (SQLiteException e) {
      // norm
    }
  }

  public void testForgottenStatement() throws SQLiteException, InterruptedException {
    SQLiteConnection connection = fileDb().open().exec("create table x (x)");
    connection.exec("insert into x values (1);");
    SQLiteStatement st = connection.prepare("select x + ? from x");
    st.bind(1, 1);
    st.step();
    assertTrue(st.hasRow());
    assertTrue(st.hasBindings());
    st = connection.prepare("select x + ? from x");
    assertFalse(st.hasRow());
    assertFalse(st.hasBindings());
    st.bind(1, 1);
    st.step();
    assertTrue(st.hasRow());
    assertTrue(st.hasBindings());
    st = null;
    System.gc();
    Thread.sleep(500);
    System.gc();
    st = connection.prepare("select x + ? from x");
    assertFalse(st.hasRow());
    assertFalse(st.hasBindings());
    st.bind(1, 1);
    st.step();
    assertTrue(st.hasRow());
    assertTrue(st.hasBindings());
  }

  public void testForgottenStatementNotReused() throws SQLiteException {
    SQLiteConnection connection = fileDb().open().exec("create table x (x)");
    connection.exec("insert into x values (1);");
    SQLiteStatement st = connection.prepare("select x from x");
    assertNotSame(st, connection.prepare("select x from x"));
    st.step();
    assertTrue(st.hasRow());
    assertNotSame(st, connection.prepare("select x from x"));
    st.step();
    assertFalse(st.hasRow());
    assertNotSame(st, connection.prepare("select x from x"));
    st.reset();
    assertNotSame(st, connection.prepare("select x from x"));
    st.dispose();
    assertSame(st, connection.prepare("select x from x"));
    st = connection.prepare("select x + ? from x");
    assertNotSame(st, connection.prepare("select x + ? from x"));
    st.bind(1, 1);
    assertNotSame(st, connection.prepare("select x + ? from x"));
    st.reset();
    assertNotSame(st, connection.prepare("select x + ? from x"));
    st.dispose();
    assertSame(st, connection.prepare("select x + ? from x"));
  }

  public void testCaching() throws SQLiteException {
    SQLiteConnection connection = fileDb().open().exec("create table x (x)");
    String sql = "select * from x";
    SQLiteStatement st1 = connection.prepare(sql);
    SQLiteStatement st2 = connection.prepare(sql);
    assertNotSame(st1, st2);
    st1.dispose();
    st2.dispose();
    SQLiteStatement st3 = connection.prepare(sql);
    // first returned is in cache
    assertSame(st1, st3);
    SQLiteStatement st4 = connection.prepare(sql);
    assertNotSame(st1, st4);
    assertNotSame(st2, st4);
    assertNotSame(st3, st4);
    st4.dispose();
    SQLiteStatement st5 = connection.prepare(sql);
    assertSame(st4, st5);
    st5.dispose();
    st3.dispose();
    SQLiteStatement st6 = connection.prepare(sql);
    assertSame(st5, st6);
  }

  public void testExpungedStatementFinished() throws SQLiteException {
    SQLiteConnection connection = fileDb().open().exec("create table x (x)");
    String sql = "select * from x";
    SQLiteStatement st1 = connection.prepare(sql);
    SQLiteStatement st2 = connection.prepare(sql);
    SWIGTYPE_p_sqlite3_stmt h = st1.statementHandle();
    assertNotSame(h, st2.statementHandle());
    st2.dispose();
    st1.dispose();
    int rc = _SQLiteSwigged.sqlite3_finalize(h);
    if (rc == 0) {
      fail("successfully finalized st1 => it wasn't finalized yet");
    }
  }
}
