package sqlite;

import sqlite.internal.SWIGTYPE_p_sqlite3_stmt;

interface StatementController {
  void validate() throws SQLiteException;

  void throwResult(int rc, String message, Object additionalMessage) throws SQLiteException;

  void disposed(SWIGTYPE_p_sqlite3_stmt handle, String sql, boolean hasBindings, boolean hasStepped);

  class DisposedStatementController implements StatementController {
    private final String myName;

    DisposedStatementController(String predecessorName) {
      myName = predecessorName + "[D]";
    }

    public String toString() {
      return myName;
    }
  }
}
