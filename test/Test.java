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
    SWIGTYPE_p_sqlite3 ptr = SQLiteManual.sqlite3_open_v2("test.db", SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE, rc);
    System.out.println("SQLiteManual.sqlite3_open_v2()=" + rc[0] + "," + ptr);

    rc[0] = SQLiteSwigged.sqlite3_close(ptr);
    System.out.println("SQLiteSwigged.sqlite3_close()=" + rc[0]);
  }
}