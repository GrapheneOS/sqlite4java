package sqlite;

import sqlite.internal.SQLiteTestFixture;

import java.io.File;

public abstract class DBConnectionFixture extends SQLiteTestFixture {
  private DBConnection myDB;
  private File myDbFile;

  protected DBConnectionFixture() {
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

  protected DBConnection fileDb() {
    return createDb(myDbFile);
  }

  protected DBConnection memDb() {
    return createDb(null);
  }

  private DBConnection createDb(File dbfile) {
    if (myDB != null) {
      myDB.close();
    }
    myDB = new DBConnection(dbfile);
    return myDB;
  }
}
