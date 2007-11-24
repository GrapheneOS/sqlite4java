package sqlite;

import sqlite.internal.SQLiteConstants;

/**
 * This interface is used as a strategy for SQLiteStatement lifecycle. Initially it is set by {@link sqlite.SQLiteConnection#prepare}
 * method, and when statement is disposed the strategy is reset to the dummy implementation.
 */
interface StatementController {
  /**
   * @throws SQLiteException if connection or statement cannot be used at this moment by the calling thread.
   */
  void validate() throws SQLiteException;

  /**
   * If result code (from sqlite operation) is not zero (SQLITE_OK), then retrieves additional error info
   * and throws verbose exception.
   */
  void throwResult(int resultCode, String message, Object additionalMessage) throws SQLiteException;

  /**
   * Performs statement life-keeping on disposal. If the statement is cached, its handle is returned to the
   * connection's cache. If it is not cached, the statement handle is finalized.
   * <p>
   * Implementation may call {@link SQLiteStatement#clear()} during execution.
   *
   * @param statement statement that is about to be disposed
   */
  void dispose(SQLiteStatement statement);

  /**
   * A stub implementation that replaces connection-based implementation when statement is disposed.
   */
  class DisposedStatementController implements StatementController {
    private final String myName;

    DisposedStatementController(String predecessorName) {
      myName = predecessorName + "[D]";
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
  }
}
