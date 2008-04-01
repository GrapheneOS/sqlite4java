package sqlite;

public class SQLiteCancelledException extends SQLiteException {
  public SQLiteCancelledException() {
    this(SQLiteConstants.Result.SQLITE_INTERRUPT, "");
  }

  public SQLiteCancelledException(int resultCode, String message) {
    super(resultCode, message);
  }
}
