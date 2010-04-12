package com.almworks.sqlite4java;

import java.io.File;
import java.util.Random;

public abstract class SQLiteConnectionFixture extends SQLiteTestFixture {
  private SQLiteConnection myDB;
  private File myDbFile;

  protected SQLiteConnectionFixture() {
    super(false);
  }

  protected void setUp() throws Exception {
    super.setUp();    
    myDbFile = new File(tempName("db"));
  }

  protected void tearDown() throws Exception {
    if (myDB != null) {
      myDB.dispose();
      myDB = null;
    }
    myDbFile = null;
    super.tearDown();
  }

  protected File dbFile() {
    return myDbFile;
  }

  protected SQLiteConnection fileDb() {
    return createDb(myDbFile);
  }

  protected SQLiteConnection memDb() {
    return createDb(null);
  }

  private SQLiteConnection createDb(File dbfile) {
    if (myDB != null) {
      myDB.dispose();
    }
    myDB = new SQLiteConnection(dbfile);
    myDB.setStepsPerCallback(1);
    return myDB;
  }

  protected byte[] generate(int size) {
    byte[] result = new byte[size];
    Random r = new Random();
    for (int i = 0; i < result.length; i++)
      result[i] = (byte) r.nextInt();
    return result;
  }
}
