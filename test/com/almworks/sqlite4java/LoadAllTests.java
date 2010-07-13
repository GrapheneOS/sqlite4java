package com.almworks.sqlite4java;

public class LoadAllTests extends SQLiteConnectionFixture {
  public void testInts() throws SQLiteException {
    int COUNT = 1000;
    SQLiteConnection sqlite = fileDb().open();
    sqlite.exec("create table x (id integer not null primary key)");
    sqlite.exec("begin");
    SQLiteStatement st = sqlite.prepare("insert into x values(?)");
    for (int i = 0; i < COUNT; i++) {
      st.bind(1, i);
      st.step();
      st.reset();
    }
    st.dispose();
    sqlite.exec("commit");
    st = sqlite.prepare("select id from x order by (500-id)*(250-id)");
    int[] buffer = new int[249];
    int loaded = 0;
    int count = 0;
    int lastv = Integer.MIN_VALUE; 
    while ((loaded = st.loadInts(0, buffer, 0, buffer.length)) > 0) {
      for (int i = 0; i < loaded; i++) {
        int id = buffer[i];
        int v = (int)(((long)(500 - id)) * (long)(250 - id));
        if (v < lastv) {
          assertTrue(lastv + " " + v, v >= lastv);
        }
        lastv = v;
        count++;
      }
    }
    assertEquals(COUNT, count);
  }
  public void testLongs() throws SQLiteException {
    int COUNT = 1000;
    SQLiteConnection sqlite = fileDb().open();
    sqlite.exec("create table x (id integer not null primary key)");
    sqlite.exec("begin");
    SQLiteStatement st = sqlite.prepare("insert into x values(?)");
    for (int i = 0; i < COUNT; i++) {
      st.bind(1, (long)i);
      st.step();
      st.reset();
    }
    st.dispose();
    sqlite.exec("commit");
    st = sqlite.prepare("select id from x order by (500-id)*(250-id)");
    long[] buffer = new long[249];
    int loaded = 0;
    int count = 0;
    int lastv = Integer.MIN_VALUE;
    while ((loaded = st.loadLongs(0, buffer, 0, buffer.length)) > 0) {
      for (int i = 0; i < loaded; i++) {
        long id = buffer[i];
        int v = (int)(((long)(500 - id)) * (long)(250 - id));
        if (v < lastv) {
          assertTrue(lastv + " " + v, v >= lastv);
        }
        lastv = v;
        count++;
      }
    }
    assertEquals(COUNT, count);
  }
}
