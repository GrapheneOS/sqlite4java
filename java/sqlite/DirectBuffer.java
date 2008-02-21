package sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;

final class DirectBuffer {
  static final int CONTROL_BYTES = 2;

  private final int mySize;
  private SWIGTYPE_p_direct_buffer myHandle;
  private ByteBuffer myControlBuffer;
  private ByteBuffer myDataBuffer;

  DirectBuffer(SWIGTYPE_p_direct_buffer handle, ByteBuffer controlBuffer, ByteBuffer dataBuffer, int size) {
    assert size == controlBuffer.capacity() + dataBuffer.capacity() : size + " " + controlBuffer.capacity() + " " + dataBuffer.capacity();
    assert controlBuffer.capacity() == CONTROL_BYTES : controlBuffer.capacity();
    assert size > CONTROL_BYTES : size;
    myHandle = handle;
    myControlBuffer = controlBuffer;
    myDataBuffer = dataBuffer;
    mySize = size;
  }

  public ByteBuffer data() throws IOException {
    if (!isValid())
      throw new IOException("buffer disposed");
    return myDataBuffer;
  }

  public int getCapacity() {
    return mySize - CONTROL_BYTES;
  }

  public int getPosition() {
    ByteBuffer buffer = myDataBuffer;
    return buffer == null ? 0 : buffer.position();
  }

  public SWIGTYPE_p_direct_buffer getHandle() {
    return myHandle;
  }

  public boolean isValid() {
    ByteBuffer controlBuffer = myControlBuffer;
    if (controlBuffer == null || myDataBuffer == null || myHandle == null)
      return false;
    byte pendingRemove = controlBuffer.get(1);
    return pendingRemove == 0;
  }

  public boolean isUsed() {
    ByteBuffer controlBuffer = myControlBuffer;
    if (controlBuffer == null)
      return false;
    return controlBuffer.get(0) != 0;
  }

  public void incUsed() {
    ByteBuffer controlBuffer = myControlBuffer;
    if (controlBuffer != null) {
      controlBuffer.put(0, (byte) (controlBuffer.get(0) + 1));
    }
  }

  public void decUsed() {
    ByteBuffer controlBuffer = myControlBuffer;
    if (controlBuffer != null) {
      byte usage = controlBuffer.get(0);
      if (usage > 0)
        controlBuffer.put(0, (byte) (usage - 1));
    }
  }

  void invalidate() {
    myControlBuffer = null;
    myDataBuffer = null;
    myHandle = null;
  }
}
