package com.almworks.sqlite4java;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IncrementalBlobIOAnalysis extends SQLiteConnectionFixture {
  private static final int COUNT = 1000;
  private static final int ROWS = 6000;

  public void testRead() throws SQLiteException {
    Logger.getLogger("sqlite").setLevel(Level.INFO);
    SQLiteConnection db = fileDb().open(true);
    db.exec("PRAGMA page_size = 1024");
    db.exec("PRAGMA cache_size = 100");
    db.exec("PRAGMA legacy_file_format = off");
    db.exec("create table A (id integer not null primary key autoincrement, value integer)");
    db.exec("create table B (value)");

    db.exec("begin");
    SQLiteStatement st = db.prepare("insert into A (value) values (?)");
    Random r = new Random();
    for (int i = 0; i < ROWS; i++) {
      st.bind(1, r.nextInt());
      st.step();
      st.reset();
    }
    st.dispose();
    db.exec("commit");

    int blobSize = 1 << 20;
    byte[] data = generate(blobSize);
    db.prepare("insert into B values (?)").bind(1, data).stepThrough().dispose();

    st = db.prepare("select value from A");

    System.out.println("Without blob access:");
    go(db, st, data, false);
    System.out.println("With blob access:");
    go(db, st, data, true);
  }

  private void go(SQLiteConnection db, SQLiteStatement st, byte[] data, boolean readBlob) throws SQLiteException {
    for (int k = 0; k < 5; k++) {
      long total = 0;
      for (int i = 0; i < COUNT; i++) {
        if (readBlob)
          readBlob(db, data);
        st.reset();
        long start = System.nanoTime();
        while (st.step()) st.columnInt(0);
        total += System.nanoTime() - start;
      }
      System.out.println("total = " + (total / 1000000L) + "ms");
    }
  }

  private void readBlob(SQLiteConnection db, byte[] data) throws SQLiteException {
    SQLiteBlob b = db.blob("B", "value", 1, false);
    for (int j = 0; j < data.length;) {
      int len = Math.min(data.length - j, 5000);
      b.read(j, data, j, len);
      j +=len;
    }
  }
}
