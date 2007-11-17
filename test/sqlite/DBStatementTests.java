package sqlite;

public class DBStatementTests extends DBConnectionFixture {
  public void testPrepareBad() throws DBException {
    DBConnection connection = fileDb();
    connection.open();
    try {
      connection.prepare("wrong sql");
      fail("prepared wrong sql");
    } catch (DBException e) {
      // ok
    }
    try {
      connection.prepare(null);
      fail("prepared null");
    } catch (DBException e) {
      //ok
    }
    try {
      connection.prepare("   ");
      fail("prepared empty");
    } catch (DBException e) {
      // ok
    }
    try {
      connection.prepare("");
      fail("prepared empty");
    } catch (DBException e) {
      // ok
    }
    try {
      connection.prepare("select * from x");
      fail("prepared invalid");
    } catch (DBException e) {
      // ok
    }
  }

  public void testStatementLifecycle() throws DBException {
    DBConnection connection = fileDb();
    connection.open();
    connection.exec("create table x (x)");
    String sql = "insert into x values (?)";
    DBStatement st1 = connection.prepare(sql, false);
    DBStatement st2 = connection.prepare(sql, false);
    DBStatement st3 = connection.prepare(sql, true);
    DBStatement st4 = connection.prepare(sql, true);
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
    connection.close();
    assertTrue(st2.isDisposed());
    assertTrue(st3.isDisposed());
  }

  public void testCloseFromAnotherThread() throws DBException, InterruptedException {
    final DBConnection connection = fileDb().open().exec("create table x (x)");
    final DBStatement st = connection.prepare("insert into x values (?)");
    assertFalse(st.isDisposed());
    assertTrue(st.isUsable());

    Thread closer = new Thread() {
      public void run() {
        try {
          st.dispose();
          fail("disposed " + st + " from another thread");
        } catch (DBException e) {
          // ok
        }

        connection.close();
      }
    };
    closer.start();
    closer.join();
    assertFalse(connection.isOpen());

    // cannot dispose from another thread actually:
    assertFalse(st.isDisposed());
    assertFalse(st.isUsable());

    connection.open();
    assertTrue(connection.isOpen());
    assertFalse(st.isUsable());
  }

  public void testCloseFromCorrectThreadWithOpenStatement() throws DBException {
    DBConnection connection = fileDb().open().exec("create table x (x, y)");
    connection.exec("insert into x values (2, '3');");
    DBStatement st = connection.prepare("select x, y from x");
    st.step();

    assertTrue(st.hasRow());
    connection.close();

    assertFalse(connection.isOpen());
    assertTrue(st.isDisposed());
    assertFalse(st.hasRow());
  }

  public void testBadBindIndexes() throws DBException {
    DBConnection connection = fileDb().open().exec("create table x (x, y)");
    DBStatement st = connection.prepare("insert into x values (?, ?)");
    try {
      st.bind(0, "0");
      fail("bound to 0");
    } catch (DBException e) {
      // norm
    }
    st.bind(1, "1");
    st.bind(2, "2");
    try {
      st.bind(3, "3");
      fail("bound to 3");
    } catch (DBException e) {
      // norm
    }
    try {
      st.bind(-99999, "-99999");
      fail("bound to 0-99999");
    } catch (DBException e) {
      // norm
    }
    try {
      st.bind(99999, "99999");
      fail("bound to 99999");
    } catch (DBException e) {
      // norm
    }
  }

  public void testBadColumnUse() throws DBException {
    DBConnection connection = fileDb().open().exec("create table x (x, y)");
    connection.exec("insert into x values (2, '3');");
    DBStatement st = connection.prepare("select x, y from x");

    assertFalse(st.hasRow());
    try {
      st.columnInt(0);
      fail("got column before step");
    } catch (DBException e) {
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
    } catch (DBException e) {
      // norm
    }
    try {
      st.columnInt(-999999);
      fail("got column -999999");
    } catch (DBException e) {
      // norm
    }
    try {
      st.columnInt(3);
      fail("got column 3");
    } catch (DBException e) {
      // norm
    }
    try {
      st.columnInt(999999);
      fail("got column 999999");
    } catch (DBException e) {
      // norm
    }
  }

  public void testForgottenStatement() throws DBException, InterruptedException {
    DBConnection connection = fileDb().open().exec("create table x (x)");
    connection.exec("insert into x values (1);");
    DBStatement st = connection.prepare("select x + ? from x");
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
