import sqlite.internal.*;
import static sqlite.internal.SQLiteConstants.*;

public class Test {
  public static void main(String[] args) {
    System.loadLibrary("sqlite");
    System.out.println("SQLiteSwigged.sqlite3_libversion()=" + SQLiteSwigged.sqlite3_libversion());
    System.out.println("SQLiteSwigged.sqlite3_libversion_number()=" + SQLiteSwigged.sqlite3_libversion_number());
    System.out.println("SQLiteSwigged.sqlite3_threadsafe()=" + SQLiteSwigged.sqlite3_threadsafe());
    System.out.println("SQLiteSwigged.sqlite3_memory_used()=" + SQLiteSwigged.sqlite3_memory_used());

    int[] rc = {0};
    SWIGTYPE_p_sqlite3 db = SQLiteManual.sqlite3_open_v2("test.db", Open.SQLITE_OPEN_CREATE | Open.SQLITE_OPEN_READWRITE, rc);
    System.out.println("SQLiteManual.sqlite3_open_v2()=" + rc[0] + "," + db);

    rc[0] = SQLiteManual.sqlite3_exec(db, "create table if not exists xxx (xxx)", null);
    System.out.println("SQLiteSwigged.exec()=" + rc[0]);

    String[] parseError = {null};
    rc[0] = SQLiteManual.sqlite3_exec(db, "create table if not exists yyy (yyy)", parseError);
    System.out.println("SQLiteSwigged.exec()=" + rc[0] + ", parseError=" + parseError[0]);

    rc[0] = SQLiteManual.sqlite3_exec(db, "create blablabla; select * from xxx;", parseError);
    System.out.println("SQLiteSwigged.exec()=" + rc[0] + ", parseError=" + parseError[0]);

    rc[0] = SQLiteSwigged.sqlite3_close(db);
    System.out.println("SQLiteSwigged.sqlite3_close()=" + rc[0]);
  }
}