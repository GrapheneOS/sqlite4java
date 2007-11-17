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
    DBStatement st = connection.prepare("insert into x values (?)");
    Thread closer = new Thread() {
      public void run() {
        connection.close();
      }
    };
    closer.start();
    closer.join();
    assertFalse(connection.isOpen());

    // cannot dispose from another thread actually:
    assertFalse(st.isDisposed());
  }
}
