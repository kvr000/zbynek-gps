package com.github.kvr000.zbynekgps.gpstool.gpx.io;

import com.github.kvr000.zbynekgps.gpstool.gpxlike.io.GpxLikeFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;


@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Log4j2
public class GpxRepoFactory
{
	private final GpxLikeFiles gpxLikeFiles; // TODO: make it a factory

	public GpxRepo openRepo(Collection<Path> files)
	{
		return new GpxRepo(gpxLikeFiles, files);
	}

	public GpxRepo fromDir(Path dir) throws IOException
	{
		Collection<File> files = FileUtils.listFiles(dir.toFile(), new String[]{".gpx", ".gpx.gz", ".fit", ".fit.gz"}, false);
		return openRepo(files.stream().map(File::toPath).toList());
	}
}
