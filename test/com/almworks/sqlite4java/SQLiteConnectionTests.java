package com.almworks.sqlite4java;

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
    connection.dispose();
    assertFalse(connection.isOpen());

    connection = fileDb();
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
    connection.dispose();
    assertFalse(connection.isOpen());

    connection = memDb();

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

  public void testCannotReopen() throws SQLiteException {
    SQLiteConnection connection = fileDb();
    connection.open();
    assertTrue(connection.isOpen());
    try {
      connection.open();
    } catch (AssertionError e) {
      // ok
    }
    assertTrue(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());
    try {
      connection.open();
      fail("reopened connection");
    } catch (SQLiteException e) {
      // ok
    }
    assertFalse(connection.isOpen());
  }

  public void testOpenV2() throws SQLiteException {
    SQLiteConnection db = fileDb();
    db.openV2(SQLiteConstants.SQLITE_OPEN_CREATE | SQLiteConstants.SQLITE_OPEN_READWRITE | SQLiteConstants.SQLITE_OPEN_NOMUTEX);
    db.exec("create table x(x)");
    db.dispose();
  }
}
