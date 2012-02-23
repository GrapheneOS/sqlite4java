package com.almworks.sqlite4java;

import java.io.File;
import java.util.Locale;

public class ExtensionLoadTests extends SQLiteConnectionFixture {
  private File myExtensionFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    String arch = System.getProperty("os.arch");
    String fileSuffix = arch.substring(arch.length() - 2);
    String osname = System.getProperty("os.name").toLowerCase(Locale.US);

    if (osname.startsWith("mac") || osname.startsWith("darwin") || osname.startsWith("os x")) {
      if (System.getProperty("os.version").contains("10.4")) {
        fileSuffix = "10.4";
      } else if (!arch.equals("ppc")) {
        fileSuffix = "";
      }
    }

    String sep = File.separator;
    myExtensionFile = new File(".." + sep + "build" + sep + "extension_sample" + sep + "half.sqlext." + fileSuffix);
  }

  public void testLoadFailWhenNotEnabled() throws SQLiteException {
    SQLiteConnection connection = memDb().open();
    connection.setExtensionLoadingEnabled(false);
    try {
      connection.loadExtension(myExtensionFile);
      fail("Extension load not enabled");
    } catch (SQLiteException e) {
         // O.K.
    }
  }

  public void testExtensionLoad() throws SQLiteException {
    final int number = 8;
    SQLiteConnection connection = memDb().open();
    connection.setExtensionLoadingEnabled(true);
    connection.loadExtension(myExtensionFile);
    SQLiteStatement stm = connection.prepare("select half(?)").bind(1, number);
    stm.step();
    int half = stm.columnInt(0);
    assertEquals(number / 2, half);
    stm.dispose();
    connection.dispose();
  }
}
