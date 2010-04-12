package com.almworks.sqlite4java;

public class BlobTests extends SQLiteConnectionFixture {
  private static final int SIZE = 100000;

  public void testOpen() throws SQLiteException {
    SQLiteConnection db = open();

    SQLiteBlob blob = db.blob(null, "T", "value", 1, true);
    assertFalse(blob.isDisposed());
    assertTrue(blob.canWrite());
    blob.dispose();
    assertTrue(blob.isDisposed());

    blob = db.blob(null, "T", "value", 1, false);
    assertFalse(blob.isDisposed());
    assertFalse(blob.canWrite());
    assertEquals(SIZE, blob.getLength());
    blob.dispose();
    assertTrue(blob.isDisposed());

    try {
      blob = db.blob(null, "T", "value", -99, false);
      fail("opened " + blob);
    } catch (SQLiteException e) {
      // normal
    }
    try {
      blob = db.blob(null, "T", "value", 2, false);
      fail("opened " + blob);
    } catch (SQLiteException e) {
      // normal
    }
    try {
      blob = db.blob(null, "T", "value", 0, false);
      fail("opened " + blob);
    } catch (SQLiteException e) {
      // normal
    }
    try {
      blob = db.blob(null, "T", "value1", 1, false);
      fail("opened " + blob);
    } catch (SQLiteException e) {
      // normal
    }
    try {
      blob = db.blob(null, "T1", "value", 1, false);
      fail("opened " + blob);
    } catch (SQLiteException e) {
      // normal
    }
    try {
      blob = db.blob("x", "T", "value", 1, false);
      fail("opened " + blob);
    } catch (SQLiteException e) {
      // normal
    }

    db.dispose();
  }

  private SQLiteConnection open() throws SQLiteException {
    SQLiteConnection db = fileDb().open(true);
    db.exec("drop table if exists T");
    db.exec("create table T (id integer not null primary key autoincrement, value blob)");
    db.prepare("insert into T (value) values (?)").bindZeroBlob(1, SIZE).stepThrough().dispose();
    long id = db.getLastInsertId();
    assertEquals(1, id);
    return db;
  }

  public void testReadWrite() throws SQLiteException {
    SQLiteConnection db = open();
    db.exec("BEGIN IMMEDIATE");
    SQLiteBlob blob = db.blob(null, "T", "value", 1, true);
    byte[] data = generate(SIZE);
    int CHUNK = 8192;
    int p = 0;
    while (p < data.length) {
      int length = Math.min(CHUNK, data.length - p);
      blob.write(p, data, p, length);
      p += length;
    }
    blob.dispose();
    db.exec("COMMIT");

    blob = db.blob(null, "T", "value", 1, false);
    byte[] chunk = new byte[CHUNK];
    p = 0;
    while (p < data.length) {
      int length = Math.min(CHUNK, data.length - p);
      blob.read(p, chunk, 0, length);
      for (int i = 0; i < length; i++)
        assertEquals("[" + (p + i) + "]", data[p + i], chunk[i]);
      p += length;
    }
    db.dispose();
  }

  public void testMultipleOpen() throws SQLiteException {
    SQLiteConnection db = open();

    doMultipleCheck(db);

    db.exec("BEGIN DEFERRED");
    doMultipleCheck(db);
    db.exec("COMMIT");

    db.exec("BEGIN IMMEDIATE");
    doMultipleCheck(db);
    db.exec("COMMIT");

    db.dispose();
  }

  private static void doMultipleCheck(SQLiteConnection db) throws SQLiteException {
    SQLiteBlob blob1 = db.blob(null, "T", "value", 1, true);
    SQLiteBlob blob2 = db.blob(null, "T", "value", 1, true);
    SQLiteBlob blob3 = db.blob(null, "T", "value", 1, false);
    SQLiteBlob blob4 = db.blob(null, "T", "value", 1, false);

    byte[] data = {111, 0, 0, 0, 0};
    blob1.write(0, data, 0, 1);
    blob2.write(1, data, 0, 1);
    blob3.read(0, data, 1, 2);
    blob4.read(0, data, 3, 2);
    for (int i = 1; i < 5; i++)
      assertEquals("[" + i + "]", data[i], data[0]);

    blob1.dispose();
    blob2.dispose();
    blob3.dispose();
    blob4.dispose();
  }

  public void testRowidEqualsPK() throws SQLiteException {
    SQLiteConnection db = fileDb().open(true);
    db.exec("drop table if exists T");
    db.exec("create table T (id integer not null primary key autoincrement, value blob)");
    db.prepare("insert into T (id, value) values (?, ?)").bind(1, 999).bindZeroBlob(2, SIZE).stepThrough().dispose();
    SQLiteBlob blob = db.blob("T", "value", 999, true);
    assertNotNull(blob);
    db.dispose();
  }

  public void testBlobBindColumn() throws SQLiteException {
    byte[] data = generate(SIZE);
    SQLiteConnection db = open();
    SQLiteStatement st = db.prepare("insert into T (value) values (?)");
    st.bind(1, data);
    st.step();
    st.dispose();
    st = db.prepare("select value from T");
    assertTrue(st.step());
    byte[] b1 = st.columnBlob(0);
    assertEquals(SIZE, b1.length);
    for (int i = 0; i < b1.length; i++)
      assertEquals("[" + i + "]", 0, b1[i]);
    assertTrue(st.step());
    b1 = st.columnBlob(0);
    for (int i = 0; i < b1.length; i++)
      assertEquals("[" + i + "]", data[i], b1[i]);
    assertFalse(st.step());
    db.dispose();
  }
}
