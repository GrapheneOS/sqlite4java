package sqlite;

import sqlite.internal.SQLiteTestFixture;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class MiscTests extends SQLiteTestFixture {
  public MiscTests() {
    super(false); 
  }

  public void testAdjustingLibPath() throws IOException {
    String jar = tempName("sqlite4java.jar");
    File jarFile = new File(jar);
    new RandomAccessFile(jarFile, "rw").close();
    assertTrue(jarFile.exists());
    jarFile.deleteOnExit();
    String dir = jarFile.getParentFile().getPath();
    char c = File.pathSeparatorChar;
    String url = "jar:file:" + jar + "!/sqlite/Internal.class";

    assertEquals(dir, Internal.getAdjustedLibraryPath(null, url));
    assertEquals("xxx" + c + dir, Internal.getAdjustedLibraryPath("xxx", url));
    assertEquals("xxx" + c + c + "yyy" + c + dir, Internal.getAdjustedLibraryPath("xxx" + c + c + "yyy" + c, url));
  }
}
