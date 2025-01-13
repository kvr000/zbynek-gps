package com.github.kvr000.zbynekgps.gpstool.gpx.util;

import com.github.kvr000.zbynekgps.gpstool.compress.AutoDecompressInputStream;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.WayPoint;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


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

	public GPX buildGpx(List<WayPoint> points)
	{
		return GPX.builder()
			.wayPoints(points)
			.build();
	}
}
