package sqlite;

import sqlite.internal.SQLiteTestFixture;

import java.io.File;

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
      myDB.close();
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
      myDB.close();
    }
    myDB = new SQLiteConnection(dbfile);
    return myDB;
  }
}
