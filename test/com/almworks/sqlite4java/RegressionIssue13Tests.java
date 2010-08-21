package com.almworks.sqlite4java;

import junit.framework.Assert;
import junit.framework.TestCase;

public class RegressionIssue13Tests extends TestCase {
  /**
   * @author Olivier Monaco
   */
  public void testColumnCountBug2() throws Exception {
    SQLiteConnection cnx = new SQLiteConnection();
    cnx.open();
    try {
      SQLiteStatement st = cnx.prepare("create table t (c text);");
      try {
        Assert.assertFalse(st.step());
        Assert.assertEquals(0, st.columnCount());
      }
      finally {
        st.dispose();
      }
      st = cnx.prepare("select name, type from sqlite_master;");
      try {
        Assert.assertTrue(st.step());
        Assert.assertEquals(2, st.columnCount());
      }
      finally {
        st.dispose();
      }
      st =
        cnx.prepare("select name, type from sqlite_master "
          + "where name='not_exists';");
      try {
        Assert.assertFalse(st.step());
        Assert.assertEquals(2, st.columnCount());
      }
      finally {
        st.dispose();
      }
    }
    finally {
      cnx.dispose();
    }
  }

  public void testUnstableColumnResult() throws SQLiteException {
    SQLiteConnection c = new SQLiteConnection().open();
    c.exec("create table A (x, y)");
    c.exec("insert into A values (1, 2)");
    SQLiteStatement st = c.prepare("select * from A");
    assertEquals(2, st.columnCount());
    c.exec("alter table A add column z");
    // unstable - nothing changed! even if we we to call sqlite3_column_count, it would still return 2!
    assertEquals(2, st.columnCount());
    // now the statement has been recompiled - the result updated
    st.step();
    assertEquals(3, st.columnCount());
  }
}