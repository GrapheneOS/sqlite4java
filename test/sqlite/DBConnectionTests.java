package sqlite;

import sqlite.internal.SQLiteTestFixture;

import java.io.File;

public class DBConnectionTests extends SQLiteTestFixture {
  public void testOpenFile() throws DBException {
    String filename = tempName("db");
    File dbfile = new File(filename);
    DBConnection connection = new DBConnection(dbfile);
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
    assertEquals(dbfile, connection.getDatabaseFile());

    connection.close();
    assertFalse(connection.isOpen());
  }

  public void testOpenMemory() throws DBException {
    DBConnection connection = new DBConnection();
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
}
