package sqlite;

import java.util.ArrayList;
import java.util.List;

public final class SQLParts {
  private static final String[] PARAMS_STRINGS = new String[101];

  private final List<String> myParts;

  private int myHash;
  private String mySql;
  private boolean myFixed;

  public SQLParts() {
    myParts = new ArrayList<String>(5);
  }

  public SQLParts(SQLParts copyFrom) {
    myParts = new ArrayList<String>(copyFrom.myParts.size());
    myParts.addAll(copyFrom.myParts);
  }

  public SQLParts(String sql) {
    myParts = new ArrayList<String>(1);
    append(sql);
  }

  /**
   * Makes instance immutable
   */
  public SQLParts fix() {
    myFixed = true;
    return this;
  }

  public SQLParts getFixedParts() {
    return myFixed ? this : new SQLParts(this).fix();
  }

  public int hashCode() {
    if (myHash == 0)
      myHash = calcHash();
    return myHash;
  }

  private int calcHash() {
    int r = 0;
    for (int i = 0; i < myParts.size(); i++)
      r = 31 * r + myParts.get(i).hashCode();
    return r;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    List<String> other = ((SQLParts) o).myParts;
    if (myParts.size() != other.size())
      return false;
    for (int i = 0; i < myParts.size(); i++)
      if (!myParts.get(i).equals(other.get(i)))
        return false;
    return true;
  }

  public void clear() {
    if (myFixed) {
      throw new IllegalStateException(String.valueOf(this));
    }
    myParts.clear();
    dropCachedValues();
  }

  public SQLParts append(String part) {
    if (myFixed) {
      throw new IllegalStateException(String.valueOf(this));
    }
    if (part != null && part.length() > 0) {
      myParts.add(part);
      dropCachedValues();
    }
    return this;
  }

  public SQLParts appendParams(int count) {
    return append(getParamsString(count));
  }

  private String getParamsString(int count) {
    if (count < 1)
      return null;
    if (count >= PARAMS_STRINGS.length)
      return createParamsString(count);
    String s = PARAMS_STRINGS[count];
    if (s == null)
      s = PARAMS_STRINGS[count] = createParamsString(count);
    return s;
  }

  private String createParamsString(int count) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0)
        b.append(',');
      b.append('?');
    }
    return b.toString();
  }

  private void dropCachedValues() {
    myHash = 0;
    mySql = null;
  }

  public String toString() {
    if (mySql == null) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < myParts.size(); i++) {
        builder.append(myParts.get(i));
      }
      mySql = builder.toString();
    }
    return mySql;
  }

  boolean isFixed() {
    return myFixed;
  }
}
