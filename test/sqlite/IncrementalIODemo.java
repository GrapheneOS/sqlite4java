package sqlite;

import java.io.File;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IncrementalIODemo {
  public static void main(String[] args) throws SQLiteException {
    Logger.getLogger("sqlite").setLevel(Level.FINE);
    ConsoleHandler h = new ConsoleHandler();
    h.setLevel(Level.FINE);
    Logger.getLogger("sqlite").addHandler(h);
    SQLiteConnection db = new SQLiteConnection(new File("c:\\temp\\test.db")).open();
    db.exec("drop table if exists T");
    db.exec("create table T (id integer not null primary key autoincrement, value blob)");

    SQLiteStatement st = db.prepare("insert into T (value) values (?)");
    st.bindZeroBlob(1, 100000);
    st.step();
    st.dispose();

    long id = db.getLastInsertId();

    assert id == 0;

    SQLiteBlob blob = db.blob(null, "T", "value", id, true);
    blob.dispose();


    db.dispose();
  }
}
