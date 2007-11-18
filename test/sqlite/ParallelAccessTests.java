package sqlite;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

public class ParallelAccessTests extends DBConnectionFixture {
  public void testParallelReads() throws DBException, InterruptedException {
    TestThread t1 = new TestThread();
    TestThread t2 = new TestThread();
    t1.exec("create table x (x)");
    t1.exec("insert into x values (1);");
    t1.exec("insert into x values (2);");
    t1.exec("insert into x values (3);");


    t1.finish();
    t2.finish();
  }


  private class TestThread extends Thread {
    private Exception myException;
    private DBConnection myConnection;
    private List<DBRunnable> myQueue = new ArrayList<DBRunnable>();

    private TestThread() {
      start();
    }

    public void run() {
      try {
        myConnection = fileDb();
        while (true) {
          DBRunnable r;
          synchronized (this) {
            if (myQueue.isEmpty()) {
              wait(500);
              continue;
            }
            r = myQueue.remove(0);
          }
          if (r == null)
            break;
          r.dbrun();
        }
      } catch (Exception e) {
        myException = e;
      }
    }

    public void exec(final String sql) throws DBException, InterruptedException {
      perform(true, new DBRunnable() {
        public void dbrun() throws DBException {
          myConnection.exec(sql);
        }
      });
    }

    private void perform(boolean wait, final DBRunnable runnable) throws InterruptedException, DBException {
      if (this == Thread.currentThread()) {
        runnable.dbrun();
        return;
      }
      DBRunnable r = runnable;
      Semaphore p = null;
      if (wait){
        final Semaphore sp = new Semaphore(1);
        sp.acquire();
        r = new DBRunnable() {
          public void dbrun() throws DBException {
            try {
              runnable.dbrun();
            } finally {
              sp.release();
            }
          }
        };
        p = sp;
      }
      synchronized (this) {
        myQueue.add(r);
        notify();
      }
      if (p != null) {
        p.acquire();
      }
    }

    public void finish() throws InterruptedException {
      synchronized (this) {
        myQueue.add(null);
        notify();
      }
      join();
    }
  }
}
