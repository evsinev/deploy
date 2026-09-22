package io.pne.deploy.agent.commands;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads at most a given number of bytes and then fails, so an endless answer cannot be buffered without limit. */
public final class BoundedInputStream extends FilterInputStream {

    private final long limit;

    private long read;

    public BoundedInputStream(InputStream aInput, long aLimit) {
        super(aInput);
        limit = aLimit;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) {
            count(1);
        }
        return value;
    }

    @Override
    public int read(byte[] aBuffer, int aOffset, int aLength) throws IOException {
        int count = super.read(aBuffer, aOffset, aLength);
        if (count > 0) {
            count(count);
        }
        return count;
    }

    private void count(int aCount) throws IOException {
        read += aCount;
        if (read > limit) {
            throw new IOException("Refusing to read more than " + limit + " bytes");
        }
    }
}
