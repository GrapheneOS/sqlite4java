package sqlite;

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
    SQLiteStatement st4 = connection.prepare(sql, true);
    assertNotSame(st1, st2);
    assertNotSame(st1, st3);
    assertNotSame(st1, st4);
    assertNotSame(st2, st3);
    assertNotSame(st2, st4);
    assertSame(st3, st4);
    assertEquals(3, connection.getStatementCount());
    assertFalse(st1.isFinished());
    assertFalse(st2.isFinished());
    assertFalse(st3.isFinished());
    st1.dispose();
    assertTrue(st1.isFinished());
    assertFalse(st2.isFinished());
    assertFalse(st3.isFinished());
    connection.close();
    assertTrue(st2.isFinished());
    assertTrue(st3.isFinished());
  }

  public void testCloseFromAnotherThread() throws SQLiteException, InterruptedException {
    final SQLiteConnection connection = fileDb().open().exec("create table x (x)");
    final SQLiteStatement st = connection.prepare("insert into x values (?)");
    assertFalse(st.isFinished());
    assertTrue(st.isUsable());

    Thread closer = new Thread() {
      public void run() {
        try {
          st.finish();
          fail("disposed " + st + " from another thread");
        } catch (SQLiteException e) {
          // ok
        }

        connection.close();
      }
    };
    closer.start();
    closer.join();
    assertFalse(connection.isOpen());

    // cannot dispose from another thread actually:
    assertFalse(st.isFinished());
    assertFalse(st.isUsable());

    connection.open();
    assertTrue(connection.isOpen());
    assertFalse(st.isUsable());
  }

  public void testCloseFromCorrectThreadWithOpenStatement() throws SQLiteException {
    SQLiteConnection connection = fileDb().open().exec("create table x (x, y)");
    connection.exec("insert into x values (2, '3');");
    SQLiteStatement st = connection.prepare("select x, y from x");
    st.step();

    assertTrue(st.hasRow());
    connection.close();

    assertFalse(connection.isOpen());
    assertTrue(st.isFinished());
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
}
