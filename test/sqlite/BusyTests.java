package sqlite;

import java.util.concurrent.Semaphore;
import java.io.File;

public class BusyTests extends SQLiteConnectionFixture {
  public void testReadLockTransactionFails() throws SQLiteException, InterruptedException {
    final String[] failure = {null};
    SQLiteConnection readc = fileDb();
    try {
      readc.open().exec("create table x (x)").exec("insert into x values (1)");
      SQLiteStatement readst = readc.prepare("select * from x");
      readst.step();
      assertTrue(readst.hasRow());

      Thread t = new Thread() {
        public void run() {
          SQLiteConnection writec = fileDb();
          try {
            writec.open();
            writec.exec("begin immediate");
            writec.exec("insert into x values (2)");
            try {
              writec.exec("commit");
              failure[0] = "successfully committed";
            } catch (SQLiteException e) {
              e.printStackTrace();
            }
          } catch (SQLiteException e) {
            e.printStackTrace();
            failure[0] = String.valueOf(e);
          } finally {
            writec.dispose();
          }
        }
      };
      t.start();
      t.join();
      
    } finally {
      readc.dispose();
    }
    if (failure[0] != null)
      fail(failure[0]);
  }

  public void testReadLockTransactionFailsWithTimeout() throws SQLiteException, InterruptedException {
    final String[] failure = {null};
    SQLiteConnection readc = fileDb();
    try {
      readc.open().exec("create table x (x)").exec("insert into x values (1)");
      SQLiteStatement readst = readc.prepare("select * from x");
      readst.step();
      assertTrue(readst.hasRow());

      Thread t = new Thread() {
        public void run() {
          SQLiteConnection writec = fileDb();
          try {
            writec.open();
            int timeout = 2000;
            writec.setBusyTimeout(timeout);
            writec.exec("begin immediate");
            writec.exec("insert into x values (2)");
            long t1 = System.currentTimeMillis();
            try {
              writec.exec("commit");
              failure[0] = "successfully committed";
            } catch (SQLiteException e) {
              long t2 = System.currentTimeMillis();
              assertTrue(String.valueOf(t2 - t1), t2 - t1 > timeout - 100);
              e.printStackTrace();
            }
          } catch (SQLiteException e) {
            e.printStackTrace();
            failure[0] = String.valueOf(e);
          } finally {
            writec.dispose();
          }
        }
      };
      t.start();
      t.join();

    } finally {
      readc.dispose();
    }
    if (failure[0] != null)
      fail(failure[0]);
  }

  public void testReadLockTransactionWaits() throws SQLiteException, InterruptedException {
    final String[] failure = {null};
    final int timeout = 2000;
    SQLiteConnection readc = fileDb();
    try {
      readc.open().exec("create table x (x)").exec("insert into x values (1)");
      SQLiteStatement readst = readc.prepare("select * from x");
      readst.step();
      assertTrue(readst.hasRow());

      final Semaphore s = new Semaphore(1);
      s.acquire();
      Thread t = new Thread() {
        public void run() {
          SQLiteConnection writec = fileDb();
          try {
            writec.open();
            writec.setBusyTimeout(timeout);
            writec.exec("begin immediate");
            writec.exec("insert into x values (2)");
            s.release();
            long t1 = System.currentTimeMillis();
            writec.exec("commit");
            long t2 = System.currentTimeMillis();
            System.out.println("commit waited for " + (t2 - t1));
          } catch (SQLiteException e) {
            e.printStackTrace();
            failure[0] = String.valueOf(e);
          } finally {
            writec.dispose();
          }
        }
      };
      t.start();
      s.acquire();
      s.release();
      Thread.sleep(timeout / 2);
      readst.reset();
      t.join();
    } finally {
      readc.dispose();
    }
    if (failure[0] != null)
      fail(failure[0]);
  }

  public void testBusySpill() throws SQLiteException, InterruptedException {
    final String[] failure = {null};
    final File file = dbFile();
    assertFalse(file.exists());
    SQLiteConnection readc = new SQLiteConnection(file);
    try {
      readc.open().exec("pragma cache_size=5").exec("pragma page_size=1024");
      readc.exec("create table x (x)").exec("insert into x values (1)");
      SQLiteStatement readst = readc.prepare("select * from x");
      readst.step();
      assertTrue(readst.hasRow());

      final Semaphore s = new Semaphore(1);
      s.acquire();
      Thread t = new Thread() {
        public void run() {
          SQLiteConnection writec = new SQLiteConnection(file);
          try {
            writec.open().exec("pragma cache_size=5");
//            writec.setBusyTimeout(timeout);
            writec.exec("begin immediate");
            SQLiteStatement st = writec.prepare("insert into x values (?)");
            for (int i = 0; i < 20; i++) {
              st.bind(1, garbageString(512));
              st.step();
              st.reset();
            }
            st.dispose();
            s.release();
            long t1 = System.currentTimeMillis();
            writec.exec("commit");
            long t2 = System.currentTimeMillis();
            System.out.println("commit waited for " + (t2 - t1));
          } catch (SQLiteException e) {
            e.printStackTrace();
            failure[0] = String.valueOf(e);
          } finally {
            writec.dispose();
          }
        }
      };
      t.start();
//      s.acquire();
//      s.release();
//      Thread.sleep(timeout / 2);
//      readst.reset();
      t.join();
    } finally {
      readc.dispose();
    }
    if (failure[0] != null)
      fail(failure[0]);

  }

  protected SQLiteConnection fileDb() {
    return new SQLiteConnection(dbFile());
  }

}
