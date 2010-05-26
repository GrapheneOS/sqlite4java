package com.almworks.sqlite4java;

public class ProfilerTests extends SQLiteConnectionFixture {
  public void testProfiler() throws SQLiteException {
    SQLiteConnection connection = memDb().open();
    SQLiteProfiler profiler = connection.profile();
    connection.exec("BEGIN IMMEDIATE");
    connection.exec("CREATE TABLE test (id INT PRIMARY KEY)");
    SQLiteStatement st = connection.prepare("INSERT INTO test (id) VALUES (?)");
    for (int i = 1; i < 10; i++) {
      st.reset(true);
      st.bind(1, i);
      st.step();
    }
    st.dispose();
    connection.exec("COMMIT");
    st = connection.prepare("SELECT id FROM test ORDER BY id DESC");
    while (st.step()) {
      st.columnLong(0);
    }
    st.reset();
    st.loadInts(0, new int[10], 0, 10);
    st.dispose();
    SQLiteProfiler p = connection.stopProfiling();

    assertSame(profiler, p);

    System.out.println("profiler output:");
    System.out.println(profiler.printReport());
  }
}
