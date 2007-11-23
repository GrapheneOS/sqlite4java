package sqlite;

import java.util.concurrent.Semaphore;

public class SQLiteConnectionTests extends SQLiteConnectionFixture {
  public void testOpenFile() throws SQLiteException {
    SQLiteConnection connection = fileDb();
    assertFalse(connection.isOpen());
    try {
      connection.openReadonly();
      fail("successfully opened");
    } catch (SQLiteException e) {
      // norm
    }
    assertFalse(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());

    boolean allowCreate = false;
    try {
      connection.open(allowCreate);
      fail("successfully opened");
    } catch (SQLiteException e) {
      // norm
    }

    connection.open(true);
    assertTrue(connection.isOpen());
    assertEquals(dbFile(), connection.getDatabaseFile());

    connection.dispose();
    assertFalse(connection.isOpen());
  }

  public void testOpenMemory() throws SQLiteException {
    SQLiteConnection connection = memDb();
    assertFalse(connection.isOpen());
    try {
      connection.openReadonly();
      fail("successfully opened");
    } catch (SQLiteException e) {
      // norm
    }
    assertFalse(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());

    try {
      connection.open(false);
      fail("successfully opened");
    } catch (SQLiteException e) {
      // norm
    }

    connection.open();
    assertTrue(connection.isOpen());
    assertNull(connection.getDatabaseFile());
    assertTrue(connection.isMemoryDatabase());

    connection.dispose();
    assertFalse(connection.isOpen());
  }

  public void testExec() throws SQLiteException {
    SQLiteConnection db = fileDb();
    try {
      db.exec("create table xxx (x)");
      fail("exec unopened");
    } catch (SQLiteException e) {
      // ok
    }

    db.open();
    db.exec("pragma encoding=\"UTF-8\";");
    db.exec("create table x (x)");
    db.exec("insert into x values (1)");
    try {
      db.exec("blablabla");
      fail("execed bad sql");
    } catch (SQLiteException e) {
      // ok
    }
  }

  public void testCloseFromAnotherThread() throws SQLiteException, InterruptedException {
    final SQLiteConnection connection = fileDb();
    for (int i = 0; i < 100; i++) {
      connection.open();
      assertTrue(connection.isOpen());
      final Semaphore s = new Semaphore(1);
      s.acquire();
      new Thread() {
        public void run() {
          connection.dispose();
          assertFalse(connection.isOpen());
          s.release();
        }
      }.start();
      s.acquire();
      s.release();
    }
  }
}
