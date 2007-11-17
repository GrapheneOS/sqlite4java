package sqlite;

public class DBValueTests extends DBConnectionFixture {
  public void testString() throws DBException {
    DBConnection con = fileDb().open();
    DBStatement st;

    st = insertAndSelect(con, "xyz");
    assertEquals("xyz", st.columnString(0));
    assertFalse(st.columnNull(0));
    st.reset();

    st = insertAndSelect(con, "1");
    assertEquals("1", st.columnString(0));
    assertEquals(1, st.columnInt(0));
    assertEquals(1, st.columnLong(0));
    assertFalse(st.columnNull(0));
    st.reset();

    st = insertAndSelect(con, "");
    assertEquals("", st.columnString(0));
    assertFalse(st.columnNull(0));
    st.reset();

    st = insertAndSelect(con, null);
    assertNull(st.columnString(0));
    assertTrue(st.columnNull(0));
    st.reset();
  }

  private static DBStatement insertAndSelect(DBConnection con, String value) throws DBException {
    recreateX(con);
    DBStatement st = con.prepare("insert into x values (?)");
    st.bind(1, value);
    st.step();
    st.reset();
    st = con.prepare("select x from x");
    st.step();
    assertTrue(st.hasRow());
    return st;
  }

  private static void recreateX(DBConnection con) throws DBException {
    con.exec("drop table if exists x");
    con.exec("create table x (x)");
  }
}
