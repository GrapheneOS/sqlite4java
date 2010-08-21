package com.almworks.sqlite4java;

import java.io.File;
import java.util.concurrent.*;

public class JobQueueTests extends SQLiteConnectionFixture {
  private TestQueue myQueue;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myQueue = new TestQueue().start();
  }

  @Override
  protected void tearDown() throws Exception {
    myQueue.stop(false).join();
    super.tearDown();
  }


  public void testStartStop() {
    assertFalse(myQueue.isStopped());
    assertNull(myQueue.getDatabaseFile());
    myQueue.stop(false);
    assertTrue(myQueue.isStopped());
  }

  public void testStartStopUnusual() throws InterruptedException {
    myQueue.start();
    myQueue.start();
    myQueue.stop(true);
    myQueue.stop(false);
    myQueue.join();
    myQueue.join();
  }

  public void testBadThreadFactory() throws InterruptedException {
    myQueue.stop(true).join();
    myQueue = new TestQueue(null, new ThreadFactory() {
      public Thread newThread(Runnable r) {
        return null;
      }
    });
    try {
      myQueue.start();
      fail("started without thread?");
    } catch (RuntimeException e) {
      // ok
    }
  }

  public void testGracefulStop() {
    BarrierJob job1 = myQueue.execute(new BarrierJob());
    BarrierJob job2 = myQueue.execute(new BarrierJob());
    myQueue.stop(true);
    assertTrue(job1.await());
    assertTrue(job2.await());
    job1.testResult(true);
    job2.testResult(true);
  }

  public void testNonGracefulStop() {
    BarrierJob job1 = myQueue.execute(new BarrierJob());
    BarrierJob job2 = myQueue.execute(new BarrierJob());
    myQueue.stop(false);
    job1.await(); // job1 may be executed if it is started before stop() is called - no check
    assertFalse(job2.await());
    job2.testNoResult(false, true, null);
  }

  public void testExecuteAfterStop() throws InterruptedException {
    myQueue.stop(true);
    SimpleJob job = myQueue.execute(new SimpleJob());
    job.testNoResult(false, true, null);
    myQueue.join();
    myQueue = new TestQueue().start();
    myQueue.stop(false).join();
    job = myQueue.execute(new SimpleJob());
    job.testNoResult(false, true, null);
  }

  public void testExecuteBeforeStart() throws InterruptedException {
    myQueue.stop(true).join();
    myQueue = new TestQueue();
    SimpleJob job = myQueue.execute(new SimpleJob());
    myQueue.start();
    job.testResult(true);
  }

  public void testFlush() throws InterruptedException {
    for (int i = 0; i < 100; i++) myQueue.execute(new SimpleJob());
    SimpleJob job = myQueue.execute(new SimpleJob());
    myQueue.flush();
    job.testState(true, true, false, false);
  }

  public void testExecute() {
    Boolean r = myQueue.execute(new SQLiteJob<Boolean>() {
      @Override
      protected Boolean job(SQLiteConnection connection) throws Throwable {
        return connection.getAutoCommit();
      }
    }).complete();
    assertEquals((Boolean) true, r);
  }

  public void testBasicOpen() throws InterruptedException {
    SQLiteQueue queue = new SQLiteQueue().start();
    assertTrue(queue.execute(new SimpleJob()).complete());
    queue.stop(false).join();
  }

  public void testCancelRollback() {
    myQueue.execute(new SQLiteJob<Object>() {
      @Override
      protected Object job(SQLiteConnection connection) throws Throwable {
        connection.exec("create table x (x)");
        return null;
      }
    }).complete();
    BarrierJob job = myQueue.execute(new BarrierJob() {
      @Override
      protected Boolean job(SQLiteConnection connection) throws Throwable {
        connection.exec("begin");
        connection.exec("insert into x values (1)");
        return super.job(connection);
      }
    });
    while (job.barrier.getNumberWaiting() < 1) {}
    job.cancel(true);
    job.testNoResult(true, true, null);
    assertEquals((Integer)0, myQueue.execute(new SQLiteJob<Integer>() {
      @Override
      protected Integer job(SQLiteConnection connection) throws Throwable {
        SQLiteStatement st = connection.prepare("select count(*) from x");
        st.step();
        int r = st.columnInt(0);
        st.dispose();
        return r;
      }
    }).complete());
  }

  public void testJobError() {
    myQueue.execute(new SimpleJob() {
      @Override
      protected Boolean job(SQLiteConnection connection) throws Throwable {
        connection.exec("BEIGN");
        return super.job(connection);
      }
    }).testNoResult(true, false, SQLiteException.class);
  }

  public void testAbnormalStop() {
    final Thread[] hijackThread = {null};
    myQueue.execute(new SQLiteJob<Object>() {
      @Override
      protected Object job(SQLiteConnection connection) throws Throwable {
        hijackThread[0] = Thread.currentThread();
        return null;
      }
    }).complete();
    BarrierJob job1 = myQueue.execute(new BarrierJob());
    BarrierJob job2 = myQueue.execute(new BarrierJob());
    hijackThread[0].interrupt();
    job1.await();
    job2.await();
    job2.testNoResult(false, true, null);
    assertTrue(myQueue.isStopped());
  }

  public void testReincarnation() throws InterruptedException {
    myQueue.stop(false).join();
    FileQueue q = new FileQueue().start();
    q.execute(new SQLiteJob<Object>() {
      @Override
      protected Object job(SQLiteConnection connection) throws Throwable {
        connection.exec("create table x (x)");
        return null;
      }
    }).complete();
    // jobs:
    // 1. barrier
    BarrierJob barrier = q.execute(new BarrierJob());
    // 2. break queue
    q.execute(new SQLiteJob<Object>() {
      @Override
      protected Object job(SQLiteConnection connection) throws Throwable {
        connection.exec("begin");
        connection.exec("insert into x values (1)");
        connection.exec("WHOA");
        return null;
      }
    });
    // 3. normal job - should be executed after reincarnation
    SQLiteJob<Integer> job = q.execute(new SQLiteJob<Integer>() {
      @Override
      protected Integer job(SQLiteConnection connection) throws Throwable {
        SQLiteStatement st = connection.prepare("select count(*) from x");
        st.step();
        return st.columnInt(0);
      }
    });
    barrier.await();
    Thread.sleep(100);
    assertFalse(q.isStopped());
    assertEquals((Integer)0, job.complete());
    q.stop(true).join();
  }


  public class TestQueue extends SQLiteQueue {
    public TestQueue() {
    }

    @Override
    public TestQueue start() {
      return (TestQueue) super.start();
    }

    public TestQueue(File databaseFile, ThreadFactory threadFactory) {
      super(databaseFile, threadFactory);
    }

    @Override
    protected SQLiteConnection openConnection() throws SQLiteException {
      return memDb().open();
    }
  }

  public class FileQueue extends SQLiteQueue {
    public FileQueue() {
    }

    @Override
    public FileQueue start() {
      return (FileQueue) super.start();
    }

    @Override
    protected SQLiteConnection openConnection() throws SQLiteException {
      return fileDb().open();
    }

    @Override
    protected void handleJobException(SQLiteJob job, Throwable e) throws SQLiteException {
      throw new RuntimeException("fail!", e); 
    }

    @Override
    protected boolean isReincarnationPossible() {
      return true;
    }

    @Override
    protected long getReincarnationTimeout() {
      return 200;
    }
  }

  private static abstract class TestJob<T> extends SQLiteJob<T> {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch finished = new CountDownLatch(1);
    final CountDownLatch errored = new CountDownLatch(1);
    final CountDownLatch cancelled = new CountDownLatch(1);

    volatile SQLiteConnection connection;
    volatile T result;
    volatile Throwable error;

    public void testState(boolean started, boolean finished, boolean error, boolean cancelled) {
      assertEquals(started, this.started.getCount() == 0);
      assertEquals(finished, this.finished.getCount() == 0);
      assertEquals(error, this.errored.getCount() == 0);
      assertEquals(cancelled, this.cancelled.getCount() == 0);
      assertEquals(cancelled, isCancelled());
      assertEquals(finished, isDone());
      assertEquals(error, getError() != null);
    }

    public void testResult(T result) {
      assert result != null;
      assertEquals(result, complete());
      // additional wait to let callbacks be called
      while (getQueue() != null) {}
      testState(true, true, false, false);
    }

    public void testNoResult(boolean started, boolean cancelled, Class errorClass) {
      assertNull(complete());
      // additional wait to let callbacks be called
      while (getQueue() != null) {
      }
      testState(started, true, errorClass != null, cancelled);
      if (errorClass != null) {
        assertEquals(errorClass, error == null ? null : error.getClass());
      }
    }

    @Override
    protected void jobStarted(SQLiteConnection connection) throws Throwable {
      started.countDown();
      this.connection = connection;
    }

    @Override
    protected void jobFinished(T result) throws Throwable {
      finished.countDown();
      this.result = result;
    }

    @Override
    protected void jobError(Throwable error) throws Throwable {
      errored.countDown();
      this.error = error;
    }

    @Override
    protected void jobCancelled() throws Throwable {
      cancelled.countDown();
    }
  }

  private static class SimpleJob extends TestJob<Boolean> {
    @Override
    protected Boolean job(SQLiteConnection connection) throws Throwable {
      return true;
    }
  }

  private static class BarrierJob extends TestJob<Boolean> {
    final CyclicBarrier barrier = new CyclicBarrier(2);

    public boolean await() {
      try {
        barrier.await(1000, TimeUnit.MILLISECONDS);
        return true;
      } catch (BrokenBarrierException e) {
        return false;
      } catch (TimeoutException e) {
        return false;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError();
      }
    }

    @Override
    protected Boolean job(SQLiteConnection connection) throws Throwable {
      if (!await()) return null;
      if (isCancelled()) return null;
      return true;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      boolean r = super.cancel(mayInterruptIfRunning);
      barrier.reset();
      return r;
    }
  }
}
