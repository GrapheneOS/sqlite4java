VERSION := 1.0.392

CFLAGS := -O2 -fPIC -fno-plt -fstack-clash-protection -fstack-protector-strong -D_FORTIFY_SOURCE=2 -fno-strict-aliasing -fwrapv -fno-omit-frame-pointer -D_GNU_SOURCE -DNDEBUG -Wall -Wextra -Isqlite \
	-DSQLITE_ENABLE_COLUMN_METADATA \
	-DSQLITE_ENABLE_MEMORY_MANAGEMENT \
	-DSQLITE_THREADSAFE=1 \
	-DTEMP_STORE=1 \
	-DSQLITE_DQS=0 \
	-DSQLITE_OMIT_DEPRECATED \
	-DSQLITE_TRUSTED_SCHEMA=0 \
	-DSQLITE_DEFAULT_DEFENSIVE \
	-DSQLITE_ENABLE_RTREE=1

LDFLAGS := -shared -fPIC -fno-plt -Wl,-O1,--sort-common,--as-needed,-z,relro,-z,now
JAVA_HOME := /usr/lib/jvm/java-19-openjdk
JAVA_CFLAGS := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux

PLATFORM := linux-amd64
OUT := out/$(PLATFORM)

$(OUT)/libsqlite4java-$(PLATFORM)-$(VERSION).so: $(OUT)/sqlite3.o $(OUT)/sqlite_wrap.o $(OUT)/sqlite3_wrap_manual.o $(OUT)/intarray.o
	$(CC) -o $@ $^ $(LDFLAGS)
	strip --strip-unneeded $@

$(OUT)/sqlite_wrap.c: swig/sqlite.i $(OUT)
	swig -java -package com.almworks.sqlite4java -o $@ $<

$(OUT)/sqlite3.o: sqlite/sqlite3.c $(OUT)
	$(CC) -c -o $@ $< $(CFLAGS)

$(OUT)/sqlite_wrap.o: $(OUT)/sqlite_wrap.c
	$(CC) -c -o $@ $< $(CFLAGS) $(JAVA_CFLAGS)

$(OUT)/sqlite3_wrap_manual.o: native/sqlite3_wrap_manual.c $(OUT)
	$(CC) -c -o $@ $< $(CFLAGS) $(JAVA_CFLAGS)

$(OUT)/intarray.o: native/intarray.c $(OUT)
	$(CC) -c -o $@ $< $(CFLAGS)

$(OUT):
	mkdir -p $(OUT)

clean:
	rm -rf $(OUT)
