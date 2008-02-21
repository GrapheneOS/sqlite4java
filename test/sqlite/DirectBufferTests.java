package sqlite;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class DirectBufferTests extends SQLiteConnectionFixture {
  private static final int SIZE = 1024;

  public void testCreation() throws SQLiteException, IOException {
    SQLite.loadLibrary();
    _SQLiteManual sqlite = new _SQLiteManual();
    DirectBuffer buffer = sqlite.wrapper_alloc(SIZE);
    assertEquals(0, sqlite.getLastReturnCode());
    assertTrue(buffer.isValid());
    ByteBuffer buf = buffer.data();
    assertNotNull(buf);
    assertTrue(buf.isDirect());
    assertFalse(buf.isReadOnly());
    assertEquals(SIZE - 2, buf.capacity());

    _SQLiteManual.wrapper_free(buffer);
    assertFalse(buffer.isValid());
  }

  public void testBind() throws SQLiteException, IOException {
    SQLiteConnection db = fileDb().open(true);
    db.exec("drop table if exists T");
    db.exec("create table T (value)");
    SQLiteStatement st = db.prepare("insert into T values (?)");
    OutputStream out = st.bindStream(1);
    byte[] data = generate(SIZE * SIZE);
    for (int i = 0; i < SIZE * SIZE; i++)
      out.write(data[i]);
    out.close();
    try {
      out.write(0);
      fail("wrote after closing");
    } catch (IOException e) {
      // ok
    }
    st.step();
    st.reset();
    out = st.bindStream(1);
    st.step();
    try {
      out.write(0);
      fail("wrote after stepping");
    } catch (IOException e) {
      // ok
    }
    st.dispose();
/*

    st = db.prepare("select value from T");
    assertTrue(st.step());
    InputStream in = st.columnStream(0);
    for (int i = 0; i < data.length; i++)
      assertEquals("[" + i + "]", data[i], (byte)in.read());
    assertEquals(-1, in.read());
    in.close();
    try {
      in.read();
      fail("read after closing");
    } catch (IOException e) {
      // ok
    }

    assertTrue(st.step());
    in = st.columnStream(0);
    assertEquals(-1, in.read());
    st.step();
    try {
      in.read();
      fail("read after stepping");
    } catch (IOException e) {
      // ok
    }
*/

    db.dispose();
  }
}
