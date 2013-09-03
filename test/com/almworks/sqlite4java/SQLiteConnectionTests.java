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

  public void testGetTableColumnMetadata() throws SQLiteException {
    SQLiteConnection db = fileDb();
    db.open();
    db.exec("create table xxx (x INTEGER PRIMARY KEY)");

    try {
      String dbName = null;
      String tableName = "xxx";
      String columnName = "x";
      String[] dataType = {""};
      String[] collSeq = {""};
      int[] notNull = {0};
      int[] primaryKey = {0};
      int[] autoinc = {0};
      assertEquals(0, db.getTableColumnMetadata(dbName, tableName, columnName, dataType, collSeq, notNull, primaryKey, autoinc));
      assertEquals("INTEGER", dataType[0]);
      assertEquals("BINARY", collSeq[0]);
      assertEquals(0, notNull[0]);
      assertEquals(1, primaryKey[0]);
      assertEquals(0, autoinc[0]);
    } catch (SQLiteException e) {
      // ok
    }
  }

  public void testSetAndGetLimit() throws SQLiteException {
    SQLiteConnection db = fileDb();
    db.open();
    int currentLimit = db.getLimit(SQLiteConstants.SQLITE_LIMIT_COLUMN);
    assertEquals(currentLimit, db.setLimit(SQLiteConstants.SQLITE_LIMIT_COLUMN, 5));
    assertEquals(5, db.getLimit(SQLiteConstants.SQLITE_LIMIT_COLUMN));
    db.exec("create table yyy (a integer, b integer, c integer, d integer, e integer);");
    try {
      db.exec("create table y (a integer, b integer, c integer, d integer, e integer, excessiveColumnName integer);");
      fail("exec should fail due to column count limitation");
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
