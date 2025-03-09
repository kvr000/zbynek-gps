package com.github.kvr000.zbynekgps.gpstool.gpxlike.io;

import com.github.kvr000.zbynekgps.gpstool.compress.AutoDecompressInputStream;
import com.github.kvr000.zbynekgps.gpstool.fit.io.FitFiles;
import com.github.kvr000.zbynekgps.gpstool.gpx.io.GpxFiles;
import com.google.common.base.Stopwatch;
import io.jenetics.jpx.GPX;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class GpxLikeFiles
{
	private final GpxFiles gpxFiles;

	private final FitFiles fitFiles;

	public GPX readGpx(InputStream input) throws IOException
	{
		InputStream buffered = new BufferedInputStream(input, 1024);
		buffered.mark(12);
		byte[] header = buffered.readNBytes(12);
		if (header.length == 12 && header[8] == '.' && header[9] == 'F' && header[10] == 'I' && header[11] == 'T') {
			buffered.reset();
			return fitFiles.readFit(buffered);
		}
		else {
			buffered.reset();
			return gpxFiles.readGpx(buffered);
		}
	}

	public GPX readGpxDecompressed(InputStream input) throws IOException
	{
		return readGpx(new AutoDecompressInputStream(input));
	}

	public GPX readGpxDecompressed(Path input) throws IOException
	{
		Stopwatch stopwatch = Stopwatch.createStarted();
		try (InputStream stream = Files.newInputStream(input)) {
			InputStream real = stream;
			String filename = input.getFileName().toString();
			String ext = FilenameUtils.getExtension(filename);
			if (ext.equals("gz")) {
				real = new GZIPInputStream(stream);
				filename = FilenameUtils.removeExtension(filename);
				ext = FilenameUtils.getExtension(filename);
			}
			if (ext.equals("fit")) {
				return fitFiles.readFit(real);
			}
			else if (ext.equals("gpx")) {
				return gpxFiles.readGpx(real);
			}
			else {
				try {
					// let try autodetect, sometimes it has weird extension:
					return readGpx(real);
				}
				catch (IOException ex) {
					throw new IOException("Unsupported extension: " + ext);
				}
			}
		}
		finally {
			log.debug("Read GPX like file: file={} time={}us", input, stopwatch.elapsed(TimeUnit.MICROSECONDS));
		}
	}

	public GPX readGpxLikeSafe(Path filePath)
	{
		try {
			return readGpxDecompressed(filePath);
		}
		catch (IOException ex) {
			log.error("Failed to read file: " + filePath + " : " + ex.getMessage(), ex);
			return null;
		}
	}

	public void writeGpx(Path output, GPX gpx) throws IOException
	{
		gpxFiles.writeGpx(output, gpx);
	}

	public List<Path> listFiles(Path dir) throws IOException
	{
		return FileUtils.listFiles(dir.toFile(), new String[]{ "gpx", "gpx.gz", "fit", "fit.gz" }, false)
			.stream().map(File::toPath).toList();
	}
}
