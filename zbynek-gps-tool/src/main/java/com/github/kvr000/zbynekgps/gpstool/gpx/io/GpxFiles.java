package com.github.kvr000.zbynekgps.gpstool.gpx.io;

import com.github.kvr000.zbynekgps.gpstool.compress.AutoDecompressInputStream;
import io.jenetics.jpx.GPX;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;


@Singleton
public class GpxFiles
{
	public GPX readGpx(InputStream input) throws IOException
	{
		return GPX.Reader.DEFAULT.read(input);
	}

	public GPX readGpxDecompressed(InputStream input) throws IOException
	{
		return GPX.Reader.DEFAULT.read(new AutoDecompressInputStream(input));
	}

	public GPX readGpxDecompressed(Path input) throws IOException
	{
		try (InputStream stream = Files.newInputStream(input)) {
			return readGpxDecompressed(stream);
		}
	}

	public void writeGpx(Path output, GPX gpx) throws IOException
	{
		GPX.write(gpx, output);
	}
}
