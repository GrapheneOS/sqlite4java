package com.almworks.sqlite4java;

import java.util.Random;

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
    a1.bind(1, 2, 3);
    con.exec("ROLLBACK");

    try {
      SQLiteStatement st = con.prepare("SELECT * FROM a1");
      fail("a1 exists?");
    } catch (SQLiteException e) {
      //  normal
    }

    con.exec("BEGIN IMMEDIATE");
    a1.bind(1, 2, 3);
    checkContents(con, a1, 1, 2, 3);
  }

  public void testRollbackSurvival2() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    con.exec("BEGIN IMMEDIATE");
    SQLiteLongArray a1 = con.createArray("a1", true);
    a1.bind(1, 2, 3);
    checkContents(con, a1, 1, 2, 3);
    a1.dispose();
    con.exec("ROLLBACK");

    try {
      SQLiteStatement st = con.prepare("SELECT * FROM a1");
      fail("a1 exists?");
    } catch (SQLiteException e) {
      // normal
    }

    con.exec("BEGIN IMMEDIATE");
    a1 = con.createArray("a1", true);
    a1.bind(1, 2, 3);
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

  public void testSearchNotOrderedNotUnique() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a = con.createArray("a", true);
    a.bind(new long[] {-89, 7, 1, 0, 0, 4, 3, 10, 9, 30, -8});
    checkSelect(con, a, "value > 0", 7, 1, 4, 3, 10, 9, 30);
    checkSelect(con, a, "value >= 4", 7, 4, 10, 9, 30);
    checkSelect(con, a, "value = 0", 0, 0);
    checkSelect(con, a, "value < 9", -89, 7, 1, 0, 0, 4, 3, -8);
    checkSelect(con, a, "value <= 1", -89, 1, 0, 0, -8);

    checkSelect(con, a, "value > 0 and value <= 9", 7, 1, 4, 3, 9);
    checkSelect(con, a, "value between 4 and 4", 4);
    checkSelect(con, a, "value is not null", -89, 7, 1, 0, 0, 4, 3, 10, 9, 30, -8);
    checkSelect(con, a, "value is null");
    checkSelect(con, a, "value > 1 and value < -1");

    checkSelect(con, a, "value > 0 and value <= 9 and value = 1", 1);
    checkSelect(con, a, "value > 0 and value > 9 and value < 111", 10, 30);
    checkSelect(con, a, "value >= 20 or value is null or value = 4", 4, 30);
  }

  public void testSearchByRowId() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a = con.createArray("a", true);
    a.bind(new long[] {-89, 7, 1, 0, 0, 4, 3, 10, 9, 30, -8});
    checkSelect(con, a, "rowid = 0", -89);
    checkSelect(con, a, "rowid > 9", -8);
    checkSelect(con, a, "rowid > -11 and rowid <= 1", -89, 7);
  }

  public void testSearchNotOrderedUnique() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a = con.createArray("a", true);
    a.bind(new long[] {-89, 7, 1, 0, 4, 3, 10, 9, 30, -8}, false, true);
    checkSelect(con, a, "value > 0", 7, 1, 4, 3, 10, 9, 30);
    checkSelect(con, a, "value between 3 and 4", 4, 3);
    checkSelect(con, a, "value = 0", 0);
    checkSelect(con, a, "value < 9", -89, 7, 1, 0, 4, 3, -8);
    checkSelect(con, a, "value <= 1", -89, 1, 0, -8);
    checkSelect(con, a, "value > 0 and value <= 9", 7, 1, 4, 3, 9);
    checkSelect(con, a, "value <= 9.9",-89, 7, 1, 0, 4, 3, -8);
    checkSelect(con, a, "value = 9.9 and value > 9");
    checkSelect(con, a, "value < '1111111x'", -89, 7, 1, 0, 4, 3, 10, 9, 30, -8);
    checkSelect(con, a, "value <= '1111111x'");
    checkSelect(con, a, "value > 2e24");
    checkSelect(con, a, "value < 2e24", -89, 7, 1, 0, 4, 3, 10, 9, 30, -8);
    checkSelect(con, a, "value = '9'");
    checkSelect(con, a, "value >= '1111111x'");
    checkSelect(con, a, "value > '1111111x' and value < 3");
    checkSelect(con, a, "value < 3 and value < 'xyz'", -89, 1, 0, -8);
    checkSelect(con, a, "value between 4 and 4", 4);
    checkSelect(con, a, "value is null");
    checkSelect(con, a, "value > null");
    checkSelect(con, a, "value > null or  value < 3", -89, 1, 0, -8);
    checkSelect(con, a, "value > null and  value < 3");
    checkSelect(con, a, "value is not null", -89, 7, 1, 0, 4, 3, 10, 9, 30, -8);
    checkSelect(con, a, "value > 1 and value < -1");
  }

  public void testSearchOrdered() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a = con.createArray("a", true);
    a.bind(new long[] {-89, -8, 0, 3, 3, 10, 30, 100, 112, 112, 112, 112, 113, 145}, true, false);
    checkSelect(con, a, "value > 0", 3, 3, 10, 30, 100, 112, 112, 112, 112, 113, 145);
    checkSelect(con, a, "value between 3 and 4", 3, 3);
    checkSelect(con, a, "value = 0", 0);
    checkSelect(con, a, "value < 9", -89, -8, 0, 3, 3);
    checkSelect(con, a, "value <= 30", -89, -8, 0, 3, 3, 10, 30);

    checkSelect(con, a, "value > 0 and value <= 10", 3, 3, 10);
    checkSelect(con, a, "value between 10 and 10", 10);
    checkSelect(con, a, "value > 1 and value < -1");
  }

  public void testSearchOrderedUnique() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a = con.createArray("a", true);
    a.bind(new long[] {-89, -8, 0, 3, 10, 30, 100, 112, 113, 145}, true, true);
    checkSelect(con, a, "value > 0", 3, 10, 30, 100, 112, 113, 145);
    checkSelect(con, a, "value between 3 and 4", 3);
    checkSelect(con, a, "value = 0", 0);
    checkSelect(con, a, "value < 9", -89, -8, 0, 3);
    checkSelect(con, a, "value <= 30", -89, -8, 0, 3, 10, 30);

    checkSelect(con, a, "value > 0 and value <= 10", 3, 10);
    checkSelect(con, a, "value between 10 and 10", 10);
    checkSelect(con, a, "value > 1 and value < -1");
  }

  public void testSearchingAfterRebind() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a = con.createArray("a", true);
    SQLiteStatement select = con.prepare("SELECT value FROM a WHERE value between 0 and 10");

    a.bind(new long[]{-89, -8, 0, 3, 10, 30, 100, 112, 113, 145}, true, true);
    checkBuffer(select.loadLongs(0, BUFFER, 0, BUFFER.length), 0, 3, 10);
    select.reset();

    a.bind(new long[]{0, -10, 10, -10, 4, 11, 4, 1032}, false, false);
    checkBuffer(select.loadLongs(0, BUFFER, 0, BUFFER.length), 0, 10, 4, 4);
    select.reset();

    a.bind(new long[]{1010101, 0, -10, 10, 4, 11, 1032}, false, true);
    checkBuffer(select.loadLongs(0, BUFFER, 0, BUFFER.length), 0, 10, 4);
    select.reset();
  }

  public void testXConnect_Regression() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    con.exec("BEGIN IMMEDIATE");
    con.exec("create table id (item INTEGER, value INTEGER)").exec("insert into id values (1, 2)");
    SQLiteLongArray a = con.createArray();
    a.bind(1, 2, 3);
    SQLiteStatement st = con.prepare("SELECT t1.value, t2.value FROM " + a.getName() + " t1 LEFT OUTER JOIN id t2 ON t1.value = t2.item ORDER BY 1");
    checkBuffer(st.loadLongs(0, BUFFER, 0, BUFFER.length), 1, 2, 3);

    con.exec("CREATE TABLE x (x)");

    st.reset();
    a.bind(1, 2, 3);

    checkBuffer(st.loadLongs(0, BUFFER, 0, BUFFER.length), 1, 2, 3);
  }

  public void testOrderedPerformance() throws SQLiteException {
    int COUNT = 50000;
    SQLiteConnection con = memDb().open();
    SQLiteLongArray a = con.createArray("a", true);
    long[] array = new long[COUNT];
    for (int i = 0; i < array.length; i++) {
      array[i] = i + 1;
    }

    a.bind(array, true, true);

    // without binary search this will work ~ 100 times slower (n^2)
    SQLiteStatement select = con.prepare("SELECT v1.value FROM a v1, a v2 WHERE v1.value = v2.value");
    long[] r = new long[array.length];
    select.loadLongs(0, r, 0, r.length);

    checkBuffer2(r, array);
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

  public void testManyTables() throws SQLiteException {
    SQLiteConnection con = memDb().open();
    con.exec("BEGIN IMMEDIATE");
    int COUNT = 100;
    Random random = new Random(1919919);
    for (int i = 0; i < COUNT; i++) {
      int r = random.nextInt(100000);
      long[] v = {r, r + 1, r + 2};
      SQLiteLongArray a = con.createArray("X" + r, i % 2 == 0);
      a.bind(v);
      checkContents(con, a, v);
      a.dispose();
    }
    con.exec("COMMIT");
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
    checkBuffer(n, values);
    select.dispose();
  }

  private void checkSelect(SQLiteConnection con, SQLiteLongArray array, String where, long... values) throws SQLiteException {
    SQLiteStatement select = con.prepare("SELECT value FROM " + array.getName() + " WHERE " + where);
    int n = select.loadLongs(0, BUFFER, 0, BUFFER.length);
    checkBuffer(n, values);
    select.dispose();
  }

  private void checkBuffer(int n, long ... sample) {
    assertEquals(sample == null ? 0 : sample.length, n);
    for (int i = 0; i < n && sample != null; i++) {
      assertEquals("[" + i + "]", sample[i], BUFFER[i]);
    }
  }

  private void checkBuffer2(long[] result, long[] sample) {
    assertEquals(sample == null ? 0 : sample.length, result.length);
    for (int i = 0; i < result.length && sample != null; i++) {
      assertEquals("[" + i + "]", sample[i], result[i]);
    }
  }
}
