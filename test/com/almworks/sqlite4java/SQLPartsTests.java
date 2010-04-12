package com.almworks.sqlite4java;

import junit.framework.TestCase;

public class SQLPartsTests extends TestCase {
  public void testAppendParams() {
    assertEquals("", createParams(0));
    assertEquals("", createParams(-1));
    assertEquals("?", createParams(1));
    assertEquals("?,?", createParams(2));
    for (int i = 3; i < 1000; i++)
      check(i);
  }

  private void check(int count) {
    String params = createParams(count);
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0)
        b.append(',');
      b.append('?');
    }
  }

  private String createParams(int count) {
    return new SQLParts().appendParams(count).toString();
  }
}
