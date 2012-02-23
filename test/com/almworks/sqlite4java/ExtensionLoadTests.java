package com.almworks.sqlite4java;

import java.io.File;
import java.net.URL;
import java.util.Locale;

public class ExtensionLoadTests extends SQLiteConnectionFixture {
  private File myExtensionFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    URL url = getClass().getClassLoader().getResource(getClass().getName().replace('.', '/') + ".class");
    String path = url.getPath();
    int k = path.indexOf('!');
    if (k >= 0) path = path.substring(0, k);
    File dir = new File(path).getParentFile();
    if (dir == null || !dir.isDirectory()) throw new IllegalStateException("cannot find class directory");
    while (dir != null) {
      File f = new File(new File(dir, "build"), "extension_sample");
      if (f.isDirectory()) {
        myExtensionFile = new File(f, "half.sqlext." + System.getProperty("os.arch"));
        break;
      }
      dir = dir.getParentFile();
    }
    if (myExtensionFile == null) throw new IllegalStateException("cannot locate build directory");
    if (!myExtensionFile.isFile()) throw new IllegalStateException("cannot find extension");
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
