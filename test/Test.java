import sqlite.*;

public class Test {
	public static void main(String[] args) {
		System.loadLibrary("sqlite");
		System.out.println("SQLite.sqlite3_libversion()=" + SQLite.sqlite3_libversion());
		System.out.println("SQLite.sqlite3_libversion_number()=" + SQLite.sqlite3_libversion_number());
		System.out.println("SQLite.sqlite3_threadsafe()=" + SQLite.sqlite3_threadsafe());
		System.out.println("SQLite.sqlite3_memory_used()=" + SQLite.sqlite3_memory_used());

		SWIGTYPE_p_p_sqlite3 h = new SWIGTYPE_p_p_sqlite3();
		int err  = SQLite.sqlite3_open_v2("test.db", h, SQLiteConstants.SQLITE_OPEN_READWRITE | SQLiteConstants.SQLITE_OPEN_CREATE, null);
		System.out.println("open=" + err + ", " + h);
		
	}
}