package com.github.kvr000.zbynekgps.gpstool.compress;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;


public class AutoDecompressInputStream extends FilterInputStream
{
	private volatile InputStream delegate;

	public AutoDecompressInputStream(InputStream inputStream)
	{
		super(inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream));
	}

	@Override
	@SuppressWarnings("resource")
	public int read() throws IOException
	{
		return checkDelegate().read();
	}

	@Override
	@SuppressWarnings("resource")
	public int read(byte[] b, int off, int len) throws IOException
	{
		return checkDelegate().read(b, off, len);
	}

	@Override
	public synchronized void close() throws IOException
	{
		if (delegate != null) {
			delegate.close();
		}
		else {
			in.close();
		}
	}

	private InputStream checkDelegate() throws IOException
	{
		InputStream delegate = this.delegate;
		if (delegate != null) {
			return delegate;
		}
		synchronized (this) {
			in.mark(4);
			if (in.read() == 0x1f && in.read() == 0x8b) {
				in.reset();
				delegate = new GZIPInputStream(in);
			} else {
				in.reset();
				delegate = in;
			}
			this.delegate = delegate;
			return delegate;
		}
	}
}
