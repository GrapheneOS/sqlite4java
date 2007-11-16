package sqlite;

import java.io.File;

public class DBConnectionTests extends DBConnectionFixture {
  public void testOpenFile() throws DBException {
    DBConnection connection = fileDb();
    assertFalse(connection.isOpen());
    try {
      connection.openReadonly();
      fail("successfully opened");
    } catch (DBException e) {
      // norm
    }
    assertFalse(connection.isOpen());
    connection.close();
    assertFalse(connection.isOpen());

    boolean allowCreate = false;
    try {
      connection.open(allowCreate);
      fail("successfully opened");
    } catch (DBException e) {
      // norm
    }

    connection.open(true);
    assertTrue(connection.isOpen());
    assertEquals(dbFile(), connection.getDatabaseFile());

    connection.close();
    assertFalse(connection.isOpen());
  }

  public void testOpenMemory() throws DBException {
    DBConnection connection = memDb();
    assertFalse(connection.isOpen());
    try {
      connection.openReadonly();
      fail("successfully opened");
    } catch (DBException e) {
      // norm
    }
    assertFalse(connection.isOpen());
    connection.close();
    assertFalse(connection.isOpen());

    try {
      connection.open(false);
      fail("successfully opened");
    } catch (DBException e) {
      // norm
    }

    connection.open();
    assertTrue(connection.isOpen());
    assertNull(connection.getDatabaseFile());
    assertTrue(connection.isMemoryDatabase());

    connection.close();
    assertFalse(connection.isOpen());
  }

  public void testExec() throws DBException {
    DBConnection db = fileDb();
    try {
      db.exec("create table xxx (x)");
      fail("exec unopened");
    } catch (DBException e) {
      // ok
    }

    db.open();
    db.exec("pragma encoding=\"UTF-8\";");
    db.exec("create table x (x)");
    db.exec("insert into x values (1)");
    try {
      db.exec("blablabla");
      fail("execed bad sql");
    } catch (DBException e) {
      // ok
    }
  }

}
