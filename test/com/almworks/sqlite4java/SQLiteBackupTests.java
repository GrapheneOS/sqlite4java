package com.almworks.sqlite4java;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;

import static com.almworks.sqlite4java.SQLiteConstants.WRAPPER_MISUSE;

public class SQLiteBackupTests extends SQLiteConnectionFixture {

  private static final int ROWS_NUMBER = 5400;

  public void testOneStepBackupMemoryToFile() throws SQLiteException {
    backupOneStep(true, new File(tempName("db")));
  }

  public void testOneStepBackupMemoryToMemory() throws SQLiteException {
    backupOneStep(true, null);
  }


  public void testOneStepBackupFileToFile() throws SQLiteException {
    backupOneStep(false, new File(tempName("db1")));
  }

  public void testOneStepBackupFileToMemory() throws SQLiteException {
    backupOneStep(false, null);
  }

  public void testStepFailWhenConnectionDisposed() throws SQLiteException {
    SQLiteConnection source = createDB(true);
    SQLiteBackup backup = source.initializeBackup(null);

    source.dispose();
    assertStepFailsWithError(backup, WRAPPER_MISUSE);

    source = createDB(true);
    backup = source.initializeBackup(null);
    SQLiteConnection destination = backup.getDestinationConnection();
    destination.dispose();
    assertStepFailsWithError(backup, WRAPPER_MISUSE);
    source.dispose();

    source = createDB(true);
    backup = source.initializeBackup(null);
    destination = backup.getDestinationConnection();
    source.dispose();
    destination.dispose();
    assertStepFailsWithError(backup, WRAPPER_MISUSE);
  }

  public void testDestinationAutoUpdate() throws SQLiteException {
    SQLiteConnection source = createDB(false);

    SQLiteBackup backup = source.initializeBackup(null);
    SQLiteConnection destination = backup.getDestinationConnection();
    boolean finished = backup.backupStep(10);
    assertFalse(finished);

    int oldPageCount = backup.getPageCount();
    int oldRemaining = backup.getRemaining();

    modifyDB(source);

    int nPages = 1;
    finished = backup.backupStep(nPages);
    assertFalse(finished);

    int newPageCount = backup.getPageCount();
    int newRemaining = backup.getRemaining();
    int additionalPages = newPageCount - oldPageCount;
    int newRemainingExpected = oldRemaining - nPages + additionalPages;
    assertEquals(newRemainingExpected, newRemaining);

    backup.backupStep(-1);
    backup.dispose(false);

    assertDBSEquals(source, destination);

    source.dispose();
    destination.dispose();
  }

  public void testBackupRestarting() throws SQLiteException {
    SQLiteConnection source = createDB(false);
    File sourceDBFile = source.getDatabaseFile();
    SQLiteConnection anotherConnectionToSource = new SQLiteConnection(sourceDBFile).open();

    SQLiteBackup backup = source.initializeBackup(null);
    SQLiteConnection destination = backup.getDestinationConnection();
    boolean finished = backup.backupStep(10);
    assertFalse(finished);

    modifyDB(anotherConnectionToSource);

    int nPages = 1;
    finished = backup.backupStep(nPages);
    assertFalse(finished);

    int newPageCount = backup.getPageCount();
    int newRemaining = backup.getRemaining();
    int newRemainingExpected = newPageCount - nPages;

    assertEquals(newRemainingExpected, newRemaining);

    backup.backupStep(-1);
    backup.dispose(false);

    assertDBSEquals(source, destination);
    assertDBSEquals(anotherConnectionToSource, destination);

    source.dispose();
    destination.dispose();
    anotherConnectionToSource.dispose();
  }

  public void testBackupWithSharingLockOnSource() throws SQLiteException {
    SQLiteConnection source = createDB(false);
    SQLiteConnection anotherConnectionToSource = new SQLiteConnection(source.getDatabaseFile()).open();

    SQLiteBackup backup = source.initializeBackup(null);
    SQLiteConnection destination = backup.getDestinationConnection();

    SQLiteStatement select = anotherConnectionToSource.prepare("select * from tab");
    select.step();
    backup.backupStep(-1);
    backup.dispose(false);

    assertDBSEquals(source, destination);

    source.dispose();
    destination.dispose();
    anotherConnectionToSource.dispose();
  }

  public void testBackupWithReservedLock() throws SQLiteException {
    SQLiteConnection source = createDB(false);
    SQLiteConnection anotherConnectionToSource = new SQLiteConnection(source.getDatabaseFile()).open();

    SQLiteBackup backup = source.initializeBackup(null);

    anotherConnectionToSource.exec("begin immediate");

    boolean finished = backup.backupStep(-1);
    assertTrue(finished);

    backup.dispose();
    source.dispose();
    anotherConnectionToSource.dispose();
  }

  public void testBackupFailWithReservedLockEstablishedBySourceConnection() throws SQLiteException {
    SQLiteConnection source = createDB(false);
    SQLiteBackup backup = source.initializeBackup(null);

    source.exec("begin immediate");

    try {
      backup.backupStep(-1);
      fail("Backup when RESERVED lock established on source connection by itself");
    } catch (SQLiteBusyException e) {
      //ok
    }

    backup.dispose();
    source.dispose();
  }

  public void testBackupFailWhenExclusiveLockOnSourceEstablished() throws SQLiteException {
    SQLiteConnection source = createDB(false);
    SQLiteConnection anotherConnectionToSource = new SQLiteConnection(source.getDatabaseFile()).open();

    SQLiteBackup backup = source.initializeBackup(null);

    anotherConnectionToSource.exec("begin exclusive");

    try {
      backup.backupStep(-1);
      fail("Backup when EXCLUSIVE lock established on source db");
    } catch (SQLiteBusyException e) {
      //ok
    }
    backup.dispose();
    source.dispose();
  }


  private SQLiteConnection createDB(boolean inMemory) throws SQLiteException {
    SQLiteConnection connection = inMemory ? memDb() : fileDb();
    connection = connection.open().exec("create table tab (val integer)");

    SQLiteStatement statement = connection.prepare("insert into tab values (?)");

    //Setting log level to WARNING because FINE logging cause many useless and similarly identical messages/ that crash JUnit
    Level previousLevel = java.util.logging.Logger.getLogger("com.almworks.sqlite4java").getLevel();
    java.util.logging.Logger.getLogger("com.almworks.sqlite4java").setLevel(java.util.logging.Level.WARNING);
    connection.exec("begin immediate");
    for (long i = 0; i < ROWS_NUMBER; i++) {
      statement.bind(1, i);
      statement.step();
      statement.reset();
    }
    java.util.logging.Logger.getLogger("com.almworks.sqlite4java").setLevel(previousLevel);
    connection.exec("commit");
    statement.dispose();

    return connection;
  }


  private void modifyDB(SQLiteConnection connection) throws SQLiteException {
    SQLiteStatement modifyStatement = connection.prepare("delete from tab where val <= 1000");
    modifyStatement.step();
//    SQLiteStatement modifyStatement = connection.prepare("insert into tab values(?)");
//    connection.exec("begin immediate");
//    for (int i = 1; i < 400; i++) {
//      modifyStatement.bind(1, ROWS_NUMBER + i);
//      modifyStatement.step();
//      modifyStatement.reset();
//    }
//    connection.exec("commit");
    modifyStatement.dispose();
  }

  private void assertDBSEquals(SQLiteConnection source, SQLiteConnection backup) throws SQLiteException {
    int sourceColumnCount = columnCount(source);
    int backupColumnCount = columnCount(backup);
    assertEquals(sourceColumnCount, backupColumnCount);

    long sourceValues[] = getArray(source, sourceColumnCount);
    long backupValues[] = getArray(backup, backupColumnCount);
    assertTrue(Arrays.equals(sourceValues, backupValues));
  }

  private int columnCount(SQLiteConnection connection) throws SQLiteException {
    SQLiteStatement countStatement = connection.prepare("select count(val) from tab");
    countStatement.step();
    int result = countStatement.columnInt(0);
    countStatement.dispose();
    return result;
  }

  private long[] getArray(SQLiteConnection connection, int arrayLength) throws SQLiteException {
    long result[] = new long[arrayLength];
    SQLiteStatement selectStatement = connection.prepare("select val from tab order by val");
    selectStatement.loadLongs(0, result, 0, arrayLength);

    selectStatement.dispose();
    return result;
  }

  private void backupOneStep(boolean sourceInMemory, File destinationFile) throws SQLiteException {
    SQLiteConnection source = createDB(sourceInMemory);
    SQLiteBackup backup = source.initializeBackup(destinationFile);

    SQLiteConnection destination = backup.getDestinationConnection();
    boolean finished = backup.backupStep(-1);
    assertTrue(finished);

    backup.dispose(false);
    assertDBSEquals(source, destination);
    source.dispose();
    destination.dispose();
  }

  private void assertStepFailsWithError(SQLiteBackup backup, int errorCode) {
    try {
      backup.backupStep(-1);
      fail("Backup disposed DB");
    } catch (SQLiteException e) {
      assertEquals(e.getErrorCode(), errorCode);
    }
  }

}
