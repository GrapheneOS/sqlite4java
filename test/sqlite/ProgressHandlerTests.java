package sqlite;

public class ProgressHandlerTests extends SQLiteConnectionFixture {

  public void testCancel() throws SQLiteException {
    SQLiteConnection db = memDb().open(true);
    db.exec("create table t (t integer)");
    db.exec("begin");
    for (int i = 0; i < 10; i++)
      db.exec("insert into t values (" + i + ")");
    db.exec("commit");

    final SQLiteStatement st = db.prepare("select (a.t - b.t) * (c.t - d.t) * (e.t - f.t) from t a,t b,t c,t d,t e,t f order by 1");
    st.cancel();
    long start = System.currentTimeMillis();
    long time;
    try {
      st.step();
      fail("stepped");
    } catch (SQLiteCancelledException e) {
      // normal
      time = System.currentTimeMillis() - start;
      assertTrue("[" + time + "]", time < 1000);
    }
    st.reset();


    new Thread() {
      public void run() {
        try {
          Thread.sleep(1000);
          st.cancel();
        } catch (InterruptedException e) {
          // ignore
        }
      }
    }.start();
    
    start = System.currentTimeMillis();
    try {
      st.step();
      fail("stepped");
    } catch (SQLiteCancelledException e) {
      // normal
      time = System.currentTimeMillis() - start;
      assertTrue("[" + time + "]", 500 < time && time < 2000);
    }

    db.dispose();
  }
}
