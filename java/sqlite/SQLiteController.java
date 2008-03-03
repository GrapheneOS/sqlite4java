package sqlite;

import java.io.IOException;

/**
 * This interface is used as a strategy for SQLiteStatement lifecycle. Initially it is set by {@link sqlite.SQLiteConnection#prepare}
 * method, and when statement is disposed the strategy is reset to the dummy implementation.
 */
abstract class SQLiteController {
  /**
   * @throws SQLiteException if connection or statement cannot be used at this moment by the calling thread.
   */
  public abstract void validate() throws SQLiteException;

  /**
   * If result code (from sqlite operation) is not zero (SQLITE_OK), then retrieves additional error info
   * and throws verbose exception.
   */
  public abstract void throwResult(int resultCode, String message, Object additionalMessage) throws SQLiteException;

  /**
   * Performs statement life-keeping on disposal. If the statement is cached, its handle is returned to the
   * connection's cache. If it is not cached, the statement handle is finalized.
   * <p>
   * Implementation may call {@link SQLiteStatement#clear()} during execution.
   *
   * @param statement statement that is about to be disposed
   */
  public abstract void dispose(SQLiteStatement statement);
  
  public abstract void dispose(SQLiteBlob blob);

  public abstract _SQLiteManual getSQLiteManual();

  public abstract DirectBuffer allocateBuffer(int sizeEstimate) throws IOException, SQLiteException;

  public abstract void freeBuffer(DirectBuffer buffer);

  public static SQLiteController getDisposed(SQLiteController controller) {
    if (controller instanceof Disposed) {
      return controller;
    }
    boolean debug = false;
    assert debug = true;
    if (!debug) {
      return Disposed.INSTANCE;
    } else {
      return new Disposed(controller == null ? "" : controller.toString());
    }
  }

  /**
   * A stub implementation that replaces connection-based implementation when statement is disposed.
   */
  private static class Disposed extends SQLiteController {
    public static final Disposed INSTANCE = new Disposed("");

    private final String myName;

    private Disposed(String namePrefix) {
      myName = namePrefix + "[D]";
    }

    public String toString() {
      return myName;
    }

    public void validate() throws SQLiteException {
      throw new SQLiteException(SQLiteConstants.Wrapper.WRAPPER_MISUSE, "statement is disposed");
    }

    public void throwResult(int resultCode, String message, Object additionalMessage) throws SQLiteException {
    }

    public void dispose(SQLiteStatement statement) {
    }

    public void dispose(SQLiteBlob blob) {
    }

    public _SQLiteManual getSQLiteManual() {
      // must not come here anyway
      return new _SQLiteManual();
    }

    public DirectBuffer allocateBuffer(int sizeEstimate) throws IOException, SQLiteException {
      throw new IOException();
    }

    public void freeBuffer(DirectBuffer buffer) {
    }
  }
}
