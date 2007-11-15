package sqlite.internal;

public interface SQLiteConstants {
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
