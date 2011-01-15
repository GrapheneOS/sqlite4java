package com.almworks.sqlite4java;

public class LongArrayTests extends SQLiteConnectionFixture {
  private final long[] BUFFER = new long[1000];

  public void testBasic() throws SQLiteException {
    SQLiteConnection con = fileDb().open();
    check(con, 3, 1, 4, 1, 5, 9, 2, 6);
    check(con, null);
  }

  public void testBoundaries() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    check(con, Long.MIN_VALUE, Long.MAX_VALUE, 0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  public void testRegion() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a1 = con.createArray();
    long[] values = {1, 2, 3, 4, 5, 6, 7, 8};
    a1.bind(values, 1, 3);
    checkContents(con, a1, 2, 3, 4);
    a1.bind(values, 6, 2);
    checkContents(con, a1, 7, 8);
  }

  public void testDispose() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a1 = con.createArray();
    assertFalse(a1.isDisposed());
    a1.dispose();
    assertTrue(a1.isDisposed());
    a1.dispose();
    assertTrue(a1.isDisposed());
    try {
      a1.bind(new long[]{1});
      fail("no exception when binding to a disposed array");
    } catch (SQLiteException e) {
      // normal
    }
  }

  public void testCaching() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a1 = con.createArray();
    a1.bind(new long[]{1, 2, 3});
    a1.dispose();
    SQLiteLongArray a2 = con.createArray();
    assertEquals(a1.getName(), a2.getName());
    checkContents(con, a2);
    SQLiteLongArray a3 = con.createArray();
    assertNotSame(a2.getName(), a3.getName());
    a2.bind(new long[]{4, 5, 6});
    checkContents(con, a2, 4, 5, 6);
    checkContents(con, a3);
    a2.dispose();
    checkContents(con, a3);
    assertTrue(tableExists(con, a1.getName()));
    assertTrue(tableExists(con, a2.getName()));
    assertTrue(tableExists(con, a3.getName()));
  }

  public void testNoCaching() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a1 = con.createArray(null, false);
    assertTrue(tableExists(con, a1.getName()));
    a1.dispose();
    assertFalse(tableExists(con, a1.getName()));
    SQLiteLongArray a2 = con.createArray(null, false);
    SQLiteLongArray a3 = con.createArray(null, true);
    assertNotSame(a1.getName(), a2.getName());
    assertNotSame(a1.getName(), a3.getName());
    assertNotSame(a2.getName(), a3.getName());
  }

  // See http://code.google.com/p/sqlite4java/issues/detail?id=4
  public void testCrashFollowingFailedCreate() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a1 = con.createArray("a1", true);
    try {
      SQLiteLongArray a2 = con.createArray("a1", true);
      fail("no name conflict?");
    } catch (SQLiteException e) {
      assertTrue(true);
      // ok
    }
    a1.dispose(); // CRASH!
  }

  public void testRollbackSurvival() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    con.exec("BEGIN IMMEDIATE");
    SQLiteLongArray a1 = con.createArray("a1", true);
    a1.bind(new long[]{1, 2, 3});
    con.exec("ROLLBACK");

    try {
      SQLiteStatement st = con.prepare("SELECT * FROM a1");
      fail("a1 exists?");
    } catch (SQLiteException e) {
      // normal
    }

    con.exec("BEGIN IMMEDIATE");
    a1.bind(new long[]{1, 2, 3});
    checkContents(con, a1, 1, 2, 3);
  }

  public void testNaming() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a1 = con.createArray("a1", true);
    assertEquals("a1", a1.getName());
    assertTrue(tableExists(con, "a1"));
    SQLiteLongArray a2 = con.createArray("a2", false);
    assertEquals("a2", a2.getName());
    assertTrue(tableExists(con, "a2"));

    a2.dispose();
    a1.dispose();
    assertFalse(tableExists(con, "a2"));

    SQLiteLongArray a3 = con.createArray();
    assertEquals("a1", a3.getName());
    assertTrue(tableExists(con, "a1"));
    a3.dispose();

    SQLiteLongArray a4 = con.createArray("a1", true);
    assertEquals("a1", a3.getName());
    a4.dispose();

    SQLiteLongArray a5 = con.createArray("a1", false);
    assertEquals("a1", a5.getName());
    a5.dispose();

    // a5 still was cached
    assertTrue(tableExists(con, "a1"));
  }

  public void testCannotBindWhileCursorIsOpen() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a1 = con.createArray("a1", true);
    a1.bind(new long[]{1, 2, 3});
    SQLiteStatement st = con.prepare("SELECT * FROM a1");
    assertTrue(st.step());
    try {
      a1.bind(new long[]{2, 3, 4});
      fail("bind must fail");
    } catch (SQLiteException e) {
      assertTrue(true);
    }
  }

  public void testCannotCreateTableThroughSQL() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a1 = con.createArray("a1", true);
    try {
      con.exec("CREATE VIRTUAL TABLE TEMP.a2 USING INTARRAY");
      fail("must fail");
    } catch (SQLiteException e) {
      assertTrue(true);
    }
  }

  private boolean tableExists(SQLiteConnection con, String name) {
    try {
      SQLiteStatement st = con.prepare("SELECT * FROM " + name);
      // need to step, as the statement may be cached and not recompiled
      st.step();
      st.dispose();
      return true;
    } catch (SQLiteException e) {
      return false;
    }
  }

  private void check(SQLiteConnection con, long... values) throws SQLiteException {
    SQLiteLongArray array = con.createArray();
    array.bind(values);
    checkContents(con, array, values);
    array.dispose();
  }

  private void checkContents(SQLiteConnection con, SQLiteLongArray array, long... values) throws SQLiteException {
    SQLiteStatement select = con.prepare("SELECT value FROM " + array.getName());
    int n = select.loadLongs(0, BUFFER, 0, BUFFER.length);
    checkBuffer(values, n);
    select.dispose();
  }

  private void checkBuffer(long[] sample, int n) {
    assertEquals(sample == null ? 0 : sample.length, n);
    for (int i = 0; i < n && sample != null; i++) {
      assertEquals("[" + i + "]", sample[i], BUFFER[i]);
    }
  }
}
