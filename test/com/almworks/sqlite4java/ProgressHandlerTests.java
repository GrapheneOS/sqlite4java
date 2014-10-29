package com.almworks.sqlite4java;

public class ProgressHandlerTests extends SQLiteConnectionFixture {

  public void testCancel() throws SQLiteException {
    SQLiteConnection db = prepareDb();
    final SQLiteStatement st = longSelect(db);
    st.cancel();
    long start = System.currentTimeMillis();
    long time;
    try {
      st.step();
      fail("stepped");
    } catch (SQLiteInterruptedException e) {
      // normal
      time = System.currentTimeMillis() - start;
      if (time > 1000) {
        Internal.logWarn(this, "cancel took " + time + "ms");
      }
    }
    st.reset();


    interruptLater(st, 300);

    start = System.currentTimeMillis();
    try {
      st.step();
      fail("stepped");
    } catch (SQLiteInterruptedException e) {
      // normal
      time = System.currentTimeMillis() - start;
      assertTrue("[" + time + "]", 500 < time);
      if (time > 2000) {
        Internal.logWarn(this, "cancel took " + time + "ms");
      }
    }

    db.dispose();
  }

  private void interruptLater(final SQLiteStatement st, final long wait) {
    new Thread() {
      public void run() {
        try {
          Thread.sleep(wait);
          st.cancel();
        } catch (InterruptedException e) {
          // ignore
        }
      }
    }.start();
  }

  private SQLiteStatement longSelect(SQLiteConnection db) throws SQLiteException {
    return db.prepare("select (a.t - b.t) * (c.t - d.t) * (e.t - f.t) from t a,t b,t c,t d,t e,t f order by 1");
  }

  private SQLiteConnection prepareDb() throws SQLiteException {
    SQLiteConnection db = memDb().open(true);
    db.exec("create table t (t integer)");
    db.exec("begin");
    for (int i = 0; i < 10; i++)
      db.exec("insert into t values (" + i + ")");
    db.exec("commit");
    return db;
  }

  public void testCancelAndTransactions() throws SQLiteException {
    SQLiteConnection db = prepareDb();
    SQLiteStatement st = longSelect(db);
    db.exec("begin");
    db.exec("insert into t values (1000)");
    interruptLater(st, 1000);
    try {
      st.step();
    } catch (SQLiteInterruptedException e) {
      // normal
    }
    SQLiteStatement chk = db.prepare("select 1 from t where t = 1000");
    assertTrue(chk.step());
    st.dispose();
    chk.dispose();
    db.exec("commit");
    chk = db.prepare("select 1 from t where t = 1000");
    assertTrue(chk.step());
    chk.dispose();
  }
}
