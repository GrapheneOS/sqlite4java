package sqlite.internal;

public interface SQLiteConstants {
  interface Open {
    public static final int SQLITE_OPEN_READONLY = 0x00000001;
    public static final int SQLITE_OPEN_READWRITE = 0x00000002;
    public static final int SQLITE_OPEN_CREATE = 0x00000004;
    public static final int SQLITE_OPEN_DELETEONCLOSE = 0x00000008;
    public static final int SQLITE_OPEN_EXCLUSIVE = 0x00000010;
    public static final int SQLITE_OPEN_MAIN_DB = 0x00000100;
    public static final int SQLITE_OPEN_TEMP_DB = 0x00000200;
    public static final int SQLITE_OPEN_TRANSIENT_DB = 0x00000400;
    public static final int SQLITE_OPEN_MAIN_JOURNAL = 0x00000800;
    public static final int SQLITE_OPEN_TEMP_JOURNAL = 0x00001000;
    public static final int SQLITE_OPEN_SUBJOURNAL = 0x00002000;
    public static final int SQLITE_OPEN_MASTER_JOURNAL = 0x00004000;
  }

  interface Result {
    public static final int SQLITE_OK = 0;   /* Successful result */
    public static final int SQLITE_ERROR = 1;   /* SQL error or missing database */
    public static final int SQLITE_INTERNAL = 2;   /* NOT USED. Internal logic error in SQLite */
    public static final int SQLITE_PERM = 3;   /* Access permission denied */
    public static final int SQLITE_ABORT = 4;   /* Callback routine requested an abort */
    public static final int SQLITE_BUSY = 5;   /* The database file is locked */
    public static final int SQLITE_LOCKED = 6;   /* A table in the database is locked */
    public static final int SQLITE_NOMEM = 7;   /* A malloc() failed */
    public static final int SQLITE_READONLY = 8;   /* Attempt to write a readonly database */
    public static final int SQLITE_INTERRUPT = 9;   /* Operation terminated by sqlite=3;_interrupt()*/
    public static final int SQLITE_IOERR = 10;   /* Some kind of disk I/O error occurred */
    public static final int SQLITE_CORRUPT = 11;   /* The database disk image is malformed */
    public static final int SQLITE_NOTFOUND = 12;   /* NOT USED. Table or record not found */
    public static final int SQLITE_FULL = 13;   /* Insertion failed because database is full */
    public static final int SQLITE_CANTOPEN = 14;   /* Unable to open the database file */
    public static final int SQLITE_PROTOCOL = 15;   /* NOT USED. Database lock protocol error */
    public static final int SQLITE_EMPTY = 16;   /* Database is empty */
    public static final int SQLITE_SCHEMA = 17;   /* The database schema changed */
    public static final int SQLITE_TOOBIG = 18;   /* String or BLOB exceeds size limit */
    public static final int SQLITE_CONSTRAINT = 19;   /* Abort due to constraint violation */
    public static final int SQLITE_MISMATCH = 20;   /* Data type mismatch */
    public static final int SQLITE_MISUSE = 21;   /* Library used incorrectly */
    public static final int SQLITE_NOLFS = 22;   /* Uses OS features not supported on host */
    public static final int SQLITE_AUTH = 23;   /* Authorization denied */
    public static final int SQLITE_FORMAT = 24;   /* Auxiliary database format error */
    public static final int SQLITE_RANGE = 25;   /* =2;nd parameter to sqlite=3;_bind out of range */
    public static final int SQLITE_NOTADB = 26;   /* File opened that is not a database file */
    public static final int SQLITE_ROW = 100;  /* sqlite=3;_step() has another row ready */
    public static final int SQLITE_DONE = 101;  /* sqlite=3;_step() has finished executing */
  }
}
