package com.almworks.sqlite4java;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ReuseStatementIssue67Tests extends SQLiteConnectionFixture {
  public void testTest() throws SQLiteException {
    Logger.getLogger("com.almworks.sqlite4java").setLevel(Level.OFF);
    SQLiteConnection connection = fileDb().open();
    connection.exec("create table TBL (id unique, val integer)");

    SQLiteStatement st = connection.prepare("insert into TBL values (?,?)", false);

    int id = 0;
    while (id != 4) {
      try {
        st.bind(1, id);
        st.bind(2, id*id);
        st.step();
        st.reset(true);
      } catch (SQLiteException ex) {
        st.cancel();
        st.clearBindings();
        st.reset(true);
        id++;
      }
    }
    checkTable(connection, 0, 0, 1, 1, 2, 4, 3, 9);
  }

  private void checkTable(SQLiteConnection connection, int ... expected) throws SQLiteException {
    int idx = 0;
    SQLiteStatement st;
    st = connection.prepare("select * from TBL");
    while (st.step()) {
      assertTrue("idx >= expected.length" + idx + " " + expected.length, idx < expected.length);
      for (int i = 0; i < st.columnCount(); i++) {
        assertEquals(st.columnInt(i), expected[idx++]);
      }
    }
  }
}
