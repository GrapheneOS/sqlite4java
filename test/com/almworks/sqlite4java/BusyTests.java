package com.almworks.sqlite4java;

import java.io.File;
import java.util.concurrent.Semaphore;

public class BusyTests extends SQLiteConnectionFixture {
  private SQLiteConnection myReader;
  private SQLiteConnection myWriter;
  private String myFailure;

  protected void setUp() throws Exception {
    super.setUp();
    File dbfile = dbFile();
    assertFalse(dbfile.exists());
    myReader = new SQLiteConnection(dbfile);
    myWriter = new SQLiteConnection(dbfile);
    myReader.open().exec("pragma cache_size=5").exec("pragma page_size=1024");
    myReader.exec("create table x (x)").exec("insert into x values (1)");
    myFailure = null;
  }

  protected void tearDown() throws Exception {
    myReader.dispose();
    myReader = null;
    myWriter = null;
    super.tearDown();
    if (myFailure != null)
      fail(myFailure);
  }

  public void testReadLockTransactionFails() throws SQLiteException, InterruptedException {
    SQLiteStatement st = myReader.prepare("select * from x");
    st.step();
    assertTrue(st.hasRow());

    Thread t = new Thread() {
      public void run() {
        try {
          myWriter.open();
          myWriter.exec("begin immediate");
          myWriter.exec("insert into x values (2)");
          try {
            myWriter.exec("commit");
            myFailure = "successfully committed";
          } catch (SQLiteBusyException e) {
            e.printStackTrace();
            if (myWriter.getAutoCommit())
              myFailure = "transaction rolled back";
          }
        } catch (SQLiteException e) {
          e.printStackTrace();
          myFailure = String.valueOf(e);
        } finally {
          myWriter.dispose();
        }
      }
    };
    t.start();
    t.join();
  }

  public void testReadLockTransactionFailsWithTimeout() throws SQLiteException, InterruptedException {
    SQLiteStatement st = myReader.prepare("select * from x");
    st.step();
    assertTrue(st.hasRow());

    Thread t = new Thread() {
      public void run() {
        try {
          myWriter.open();
          int timeout = 2000;
          myWriter.setBusyTimeout(timeout);
          myWriter.exec("begin immediate");
          myWriter.exec("insert into x values (2)");
          long t1 = System.currentTimeMillis();
          try {
            myWriter.exec("commit");
            myFailure = "successfully committed";
          } catch (SQLiteBusyException e) {
            long t2 = System.currentTimeMillis();
            assertTrue(String.valueOf(t2 - t1), t2 - t1 > timeout - 100);
            e.printStackTrace();
            if (myWriter.getAutoCommit())
              myFailure = "transaction rolled back";
          }
        } catch (SQLiteException e) {
          e.printStackTrace();
          myFailure = String.valueOf(e);
        } finally {
          myWriter.dispose();
        }
      }
    };
    t.start();
    t.join();

  }

  public void testReadLockTransactionWaits() throws SQLiteException, InterruptedException {
    final int timeout = 2000;
    SQLiteStatement st = myReader.prepare("select * from x");
    st.step();
    assertTrue(st.hasRow());

    final Semaphore s = new Semaphore(1);
    s.acquire();
    Thread t = new Thread() {
      public void run() {
        try {
          myWriter.open();
          myWriter.setBusyTimeout(timeout);
          myWriter.exec("begin immediate");
          myWriter.exec("insert into x values (2)");
          s.release();
          long t1 = System.currentTimeMillis();
          myWriter.exec("commit");
          long t2 = System.currentTimeMillis();
          System.out.println("commit waited for " + (t2 - t1));
        } catch (SQLiteException e) {
          e.printStackTrace();
          myFailure = String.valueOf(e);
        } finally {
          myWriter.dispose();
        }
      }
    };
    t.start();
    s.acquire();
    s.release();
    Thread.sleep(timeout / 2);
    st.reset();
    t.join();
  }

  public void testBusySpillPre36() throws SQLiteException, InterruptedException {
    if (SQLite.getSQLiteVersionNumber() >= 3006000) {
      // skipping
      return;
    }
    SQLiteStatement st = myReader.prepare("select * from x");
    st.step();
    assertTrue(st.hasRow());
    Thread t = new Thread() {
      public void run() {
        try {
          myWriter.open().exec("pragma cache_size=5");
          myWriter.exec("begin immediate");
          SQLiteStatement st = myWriter.prepare("insert into x values (?)");
          try {
            for (int i = 0; i < 20; i++) {
              st.bind(1, garbageString(512));
              st.step();
              st.reset();
            }
            myFailure = "successfully inserted data in one transaction that exceeds disk cache and shared lock is preserved";
          } catch (SQLiteBusyException e) {
            if (!myWriter.getAutoCommit())
              myFailure = "transaction not rolled back";
          } finally {
            st.dispose();
          }
        } catch (SQLiteException e) {
          e.printStackTrace();
          myFailure = String.valueOf(e);
        } finally {
          myWriter.dispose();
        }
      }
    };
    t.start();
    t.join();
  }

  public void testBusySpillPost36() throws SQLiteException, InterruptedException {
    if (SQLite.getSQLiteVersionNumber() < 3006000) {
      // skipping
      return;
    }
    SQLiteStatement st = myReader.prepare("select * from x");
    st.step();
    assertTrue(st.hasRow());
    Thread t = new Thread() {
      public void run() {
        try {
          myWriter.open().exec("pragma cache_size=5");
          myWriter.exec("begin immediate");
          SQLiteStatement st = myWriter.prepare("insert into x values (?)");
          try {
            myFailure = "couldn't insert data";
            for (int i = 0; i < 20; i++) {
              st.bind(1, garbageString(512));
              st.step();
              st.reset();
            }
            // should get here
            myFailure = "commit didn't throw busy exception";
            myWriter.exec("commit");
          } catch (SQLiteBusyException e) {
            if (myWriter.getAutoCommit())
              myFailure = "transaction rolled back";
            myFailure = null;
          } finally {
            st.dispose();
          }
        } catch (SQLiteException e) {
          e.printStackTrace();
          myFailure = String.valueOf(e);
        } finally {
          myWriter.dispose();
        }
      }
    };
    t.start();
    t.join();
  }
}
