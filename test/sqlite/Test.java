package sqlite;

import static sqlite.SQLiteConstants.Open;

public class Test {
  public static void main(String[] args) {




    System.loadLibrary("sqlite");

    System.out.println("_SQLiteSwigged.sqlite3_libversion()=" + _SQLiteSwigged.sqlite3_libversion());
    System.out.println("_SQLiteSwigged.sqlite3_libversion_number()=" + _SQLiteSwigged.sqlite3_libversion_number());
    System.out.println("_SQLiteSwigged.sqlite3_threadsafe()=" + _SQLiteSwigged.sqlite3_threadsafe());
    System.out.println("_SQLiteSwigged.sqlite3_memory_used()=" + _SQLiteSwigged.sqlite3_memory_used());

    _SQLiteManual sqLiteManual = new _SQLiteManual();
    SWIGTYPE_p_sqlite3 db = sqLiteManual.sqlite3_open_v2("test.db", Open.SQLITE_OPEN_CREATE | Open.SQLITE_OPEN_READWRITE);
    System.out.println("_SQLiteManual.sqlite3_open_v2()=" + sqLiteManual.getLastReturnCode() + "," + db);

    int rc = _SQLiteManual.sqlite3_exec(db, "create table if not exists xxx (xxx)", null);
    System.out.println("_SQLiteSwigged.exec()=" + rc);

    String[] parseError = {null};
    rc = _SQLiteManual.sqlite3_exec(db, "create table if not exists yyy (yyy)", parseError);
    System.out.println("_SQLiteSwigged.exec()=" + rc + ", parseError=" + parseError[0]);

    rc = _SQLiteManual.sqlite3_exec(db, "create blablabla; select * from xxx;", parseError);
    System.out.println("_SQLiteSwigged.exec()=" + rc + ", parseError=" + parseError[0]);

    SWIGTYPE_p_sqlite3_stmt stmt = sqLiteManual.sqlite3_prepare_v2(db, "insert into xxx (xxx) values(?)");
    System.out.println("_SQLiteManual.sqlite3_prepare_v2()=" + sqLiteManual.getLastReturnCode() + ",stmt=" + stmt);

    rc = _SQLiteSwigged.sqlite3_finalize(stmt);
    System.out.println("_SQLiteManual.sqlite3_prepare_v2()=" + rc + ",stmt=" + stmt);

    rc = _SQLiteSwigged.sqlite3_close(db);
    System.out.println("_SQLiteSwigged.sqlite3_close()=" + rc);
  }
}