package com.almworks.sqlite4java;

import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.almworks.sqlite4java.SQLiteTestFixture.createRandom;

public class NonASCIIIssue17Tests {
  private static final String C01 = "0000" + (char)55360 + (char)56384;
  private SQLiteConnection cnx;

  @After
  public void after() {
    cnx.dispose();
  }

  @Before
  public void before() throws SQLiteException {
    cnx = new SQLiteConnection();
    cnx.open();
  }

  public void run(SQLiteStatement st, String expected) throws SQLiteException {
    st.step();
    String result = st.columnString(0);
    Assert.assertEquals(expected, result);
  }

  @Test
  public void testBind() throws SQLiteException {
    String v = "select ?;";
    SQLiteStatement st = cnx.prepare(v);
    st.bind(1, C01);
    run(st, C01);
  }

  @Test
  public void testConcatSimple() throws SQLiteException {
    String v = "select '" + C01 + "';";
    SQLiteStatement st = cnx.prepare(v);
    run(st, C01);
  }

  @Test
  public void testConcat() throws SQLiteException {
    Random RAND = createRandom();
    Logger.getLogger("com.almworks.sqlite4java").setLevel(Level.OFF);

    int attempts = 50000;
    for (int i = 0; i < attempts; i++) {
      int val = 1 + RAND.nextInt(0xd800);
      if (val >= 0xd800) val += 0x800;
      if (val == '\'') continue;
      String s = "000" + (char) val;
      SQLiteStatement st = cnx.prepare("select '" + s + "';", false);
      try {
        run(st, s);
      } catch (SQLiteException ex) {
        System.out.printf("code char = %d\n", val);
        throw ex;
      } finally {
        st.dispose();
      }
    }

    for (int i = 0; i < attempts; i++) {
      int valTop = 0xd800 + RAND.nextInt(0x0400);
      int valLow = 0xdc00 + RAND.nextInt(0x0400);

      String s = "000" + (char) (valTop) + (char) (valLow);
      SQLiteStatement st = cnx.prepare("select '" + s + "';", false);
      try {
        run(st, s);
      } catch (SQLiteException ex) {
        System.out.printf("code chars: %d %d\n", valTop, valLow);
        throw ex;
      } finally {
        st.dispose();
      }
    }
  }
}
