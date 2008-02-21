package sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tests to compare performance of byte[] and xxxStream operations on blobs
 */
public class BlobComparisonPerformance extends SQLiteConnectionFixture {
  private byte[] myBuffer;

  private InsertionMethod COPY_TO_PERSISTENT_BUFFER = new CopyToPersistentBufferMethod();
  private InsertionMethod ALLOCATE_EACH_TIME = new AllocateEachTimeMethod();
  private InsertionMethod DIRECT_BUFFER = new DirectBufferMethod();
  private InsertionMethod DIRECT_BUFFER_SIZE_KNOWN = new DirectBufferSizeKnownMethod();

  private SelectionMethod BYTEARRAY = new ByteArrayMethod();
  private SelectionMethod BUFFER_READ_BY_BYTE = new BufferReadByByte();
  private SelectionMethod BUFFER_READ_INTO_ARRAY = new BufferReadIntoArray();

  private InsertionMethod[] insertionMethods = {COPY_TO_PERSISTENT_BUFFER, ALLOCATE_EACH_TIME, DIRECT_BUFFER, DIRECT_BUFFER_SIZE_KNOWN};
  private SelectionMethod[] selectionMethods = {BYTEARRAY, BUFFER_READ_BY_BYTE, BUFFER_READ_INTO_ARRAY};

  protected void setUp() throws Exception {
    SQLite.setPreferDebugLibrary(false);
    super.setUp();
  }

  public void testInsertion() throws SQLiteException, IOException {
    Logger.getLogger("sqlite").setLevel(Level.INFO);

    SQLiteConnection db = fileDb().open(true);
    recreateTable(db);
    SQLiteStatement st = db.prepare("insert into T values (?)");

    // 16 kb
    byte[] data = generate(1 << 10);
    int transactions = 80;
    int inserts = 100;

    System.out.println("writing " + (data.length * transactions * inserts >> 20) + " mb of data");

    // make hot
    for (InsertionMethod method : insertionMethods)
      runInsertion(db, st, transactions, inserts, data, method);
    for (InsertionMethod method : insertionMethods)
      runInsertion(db, st, transactions, inserts, data, method);

    long times[] = new long[insertionMethods.length];
    for (int i = 0; i < insertionMethods.length; i++)
      times[i] = runInsertion(db, st, transactions, inserts, data, insertionMethods[i]);

    System.out.println();
    System.out.println("Result:");
    for (int i = 0; i < insertionMethods.length; i++) {
      System.out.println(insertionMethods[i] + ": " + times[i]);
    }

    db.dispose();
  }

  public void testRetrieval() throws SQLiteException, IOException {
    Logger.getLogger("sqlite").setLevel(Level.INFO);

    SQLiteConnection db = fileDb().open(true);
    recreateTable(db);
    SQLiteStatement st = db.prepare("insert into T values (?)");

    // 16 kb
    byte[] data = generate(1 << 20);
    int transactions = 10;
    int inserts = 20;

    System.out.println("writing " + (data.length * transactions * inserts >> 20) + " mb of data");
    runInsertion(db, st, transactions, inserts, data, DIRECT_BUFFER);
    st.dispose();

    st = db.prepare("select V from T");

    // make hot
    for (SelectionMethod method : selectionMethods)
      runSelection(st, method);
    for (SelectionMethod method : selectionMethods)
      runSelection(st, method);

    long times[] = new long[selectionMethods.length];
    for (int i = 0; i < selectionMethods.length; i++)
      times[i] = runSelection(st, selectionMethods[i]);

    System.out.println();
    System.out.println("Result:");
    for (int i = 0; i < selectionMethods.length; i++) {
      System.out.println(selectionMethods[i] + ": " + times[i]);
    }

    db.dispose();
  }


  private void recreateTable(SQLiteConnection db) throws SQLiteException {
    db.exec("drop table if exists T");
    db.exec("create table T (V)");
  }

  private long runInsertion(SQLiteConnection db, SQLiteStatement st, int transactionCount, int insertsPerTransaction, byte[] data,
    InsertionMethod method) throws SQLiteException, IOException
  {
    recreateTable(db);
    System.out.println("running " + method);
    long start = System.currentTimeMillis();
    for (int i = 0; i < transactionCount; i++) {
      db.exec("BEGIN IMMEDIATE");
      for (int j = 0; j < insertsPerTransaction; j++) {
        method.insert(st, data);
      }
      db.exec("COMMIT");
      System.out.print(".");
    }
    System.out.println();
    long end = System.currentTimeMillis();
    return end - start;
  }

  private long runSelection(SQLiteStatement st, SelectionMethod method) throws SQLiteException, IOException {
    st.reset();
    System.out.println("running " + method);
    long start = System.currentTimeMillis();
    while (st.step()) {
      method.select(st);
    }
    long end = System.currentTimeMillis();
    return end - start;
  }

  static abstract class InsertionMethod {
    private final String myName;

    public InsertionMethod(String name) {
      myName = name;
    }

    public String toString() {
      return myName;
    }

    public abstract void insert(SQLiteStatement st, byte[] source) throws SQLiteException, IOException;
  }

  private class CopyToPersistentBufferMethod extends InsertionMethod {
    public CopyToPersistentBufferMethod() {
      super("COPY_TO_PERSISTENT_BUFFER");
    }

    public void insert(SQLiteStatement st, byte[] source) throws SQLiteException {
      int length = source.length;
      if (myBuffer == null || myBuffer.length < length)
        myBuffer = new byte[length];
      System.arraycopy(source, 0, myBuffer, 0, length);
      st.bind(1, myBuffer, 0, length);
      st.step();
      st.reset();
    }
  }

  private static class AllocateEachTimeMethod extends InsertionMethod {
    public AllocateEachTimeMethod() {
      super("ALLOCATE_EACH_TIME");
    }

    public void insert(SQLiteStatement st, byte[] source) throws SQLiteException {
      int length = source.length;
      byte[] buffer = new byte[length];
      System.arraycopy(source, 0, buffer, 0, length);
      st.bind(1, buffer, 0, length);
      st.step();
      st.reset();
    }
  }

  private static class DirectBufferMethod extends InsertionMethod {
    public DirectBufferMethod() {
      super("DIRECT_BUFFER");
    }

    public void insert(SQLiteStatement st, byte[] source) throws SQLiteException, IOException {
      OutputStream out = st.bindStream(1);
      out.write(source);
      out.close();
      st.step();
      st.reset();
    }
  }

  private static class DirectBufferSizeKnownMethod extends InsertionMethod {
    public DirectBufferSizeKnownMethod() {
      super("DIRECT_BUFFER_SIZE_KNOWN");
    }

    public void insert(SQLiteStatement st, byte[] source) throws SQLiteException, IOException {
      OutputStream out = st.bindStream(1, source.length);
      out.write(source);
      out.close();
      st.step();
      st.reset();
    }
  }

  private static abstract class SelectionMethod {
    public String toString() {
      String className = getClass().getName();
      return className.substring(className.lastIndexOf('$') + 1);
    }

    public abstract void select(SQLiteStatement st) throws SQLiteException, IOException;
  }

  private static class ByteArrayMethod extends SelectionMethod {
    public void select(SQLiteStatement st) throws SQLiteException {
      byte[] bytes = st.columnBlob(0);
      if (bytes != null) {
        int length = bytes.length;
        for (int i = 0; i < length; i++) ;
      }
    }
  }

  private static class BufferReadByByte extends SelectionMethod {
    public void select(SQLiteStatement st) throws SQLiteException, IOException {
      InputStream in = st.columnStream(0);
      while (in.read() >= 0) ;
      in.close();
    }
  }

  private class BufferReadIntoArray extends SelectionMethod {
    public void select(SQLiteStatement st) throws SQLiteException, IOException {
      InputStream in = st.columnStream(0);
      if (myBuffer == null || myBuffer.length != 8192)
        myBuffer = new byte[8192];
      int len;
      while ((len = in.read(myBuffer)) > 0) {
        for (int i = 0; i < len; i++);
      }
    }
  }
}
