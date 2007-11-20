import sqlite.internal.*;
import static sqlite.internal.SQLiteConstants.*;

public class Test {
  public static void main(String[] args) {
    System.loadLibrary("sqlite");

    System.out.println("_SQLiteSwigged.sqlite3_libversion()=" + _SQLiteSwigged.sqlite3_libversion());
    System.out.println("_SQLiteSwigged.sqlite3_libversion_number()=" + _SQLiteSwigged.sqlite3_libversion_number());
    System.out.println("_SQLiteSwigged.sqlite3_threadsafe()=" + _SQLiteSwigged.sqlite3_threadsafe());
    System.out.println("_SQLiteSwigged.sqlite3_memory_used()=" + _SQLiteSwigged.sqlite3_memory_used());

    int[] rc = {0};
    SWIGTYPE_p_sqlite3 db = _SQLiteManual.sqlite3_open_v2("test.db", Open.SQLITE_OPEN_CREATE | Open.SQLITE_OPEN_READWRITE, rc);
    System.out.println("_SQLiteManual.sqlite3_open_v2()=" + rc[0] + "," + db);

    rc[0] = _SQLiteManual.sqlite3_exec(db, "create table if not exists xxx (xxx)", null);
    System.out.println("_SQLiteSwigged.exec()=" + rc[0]);

    String[] parseError = {null};
    rc[0] = _SQLiteManual.sqlite3_exec(db, "create table if not exists yyy (yyy)", parseError);
    System.out.println("_SQLiteSwigged.exec()=" + rc[0] + ", parseError=" + parseError[0]);

    rc[0] = _SQLiteManual.sqlite3_exec(db, "create blablabla; select * from xxx;", parseError);
    System.out.println("_SQLiteSwigged.exec()=" + rc[0] + ", parseError=" + parseError[0]);

    SWIGTYPE_p_sqlite3_stmt stmt = _SQLiteManual.sqlite3_prepare_v2(db, "insert into xxx (xxx) values(?)", rc);
    System.out.println("_SQLiteManual.sqlite3_prepare_v2()=" + rc[0] + ",stmt=" + stmt);

    rc[0] = _SQLiteSwigged.sqlite3_finalize(stmt);
    System.out.println("_SQLiteManual.sqlite3_prepare_v2()=" + rc[0] + ",stmt=" + stmt);

    rc[0] = _SQLiteSwigged.sqlite3_close(db);
    System.out.println("_SQLiteSwigged.sqlite3_close()=" + rc[0]);
  }
}