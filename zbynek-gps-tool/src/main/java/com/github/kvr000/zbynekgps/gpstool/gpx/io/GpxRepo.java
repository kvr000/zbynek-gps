package com.github.kvr000.zbynekgps.gpstool.gpx.io;

import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpxUtil;
import com.github.kvr000.zbynekgps.gpstool.gpxlike.io.GpxLikeFiles;
import com.google.common.base.Stopwatch;
import io.jenetics.jpx.WayPoint;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Log4j2
public class GpxRepo
{
	private final GpxLikeFiles gpxLikeFiles;
	private final TreeMap<Long, Map.Entry<Long, Path>> timeToFiles;

	private Path lastPath;
	private TreeMap<Long, WayPoint> content;

	public GpxRepo(GpxLikeFiles gpxLikeFiles, Collection<Path> files)
	{
		this.gpxLikeFiles = gpxLikeFiles;
		Stopwatch watch = Stopwatch.createStarted();
		log.info("Indexing files: count={}", files.size());
		try {
			timeToFiles = files.parallelStream()
				.map(file -> Pair.of(file, gpxLikeFiles.readGpxLikeSafe(file)))
				.filter(p -> p.getRight() != null)
				.map(p -> Pair.of(p.getLeft(), GpxUtil.expandToTimedWaypoints(p.getRight())))
				.filter(p -> !p.getRight().isEmpty())
				.map(p -> Map.entry(p.getRight().getFirst().getTime().get().toEpochMilli(),
					Map.entry(p.getRight().getLast().getTime().get().toEpochMilli(), p.getLeft())
				))
				.collect(Collectors.toMap(
					e -> e.getKey(),
					e -> e.getValue(),
					(a, b) -> a,
					TreeMap::new
				));
			log.debug("timeToFiles: {}", timeToFiles);
		}
		finally {
			log.info("Indexed repo: files={} time={} ms", files.size(), watch.elapsed(TimeUnit.MILLISECONDS));
		}

	}

	public synchronized Pair<WayPoint, Path> getEpochMilli(long milli)
	{
		Map.Entry<Long, Map.Entry<Long, Path>> found = timeToFiles.floorEntry(milli);
		if (found == null) {
			return null;
		}
		final Map.Entry<Long, Path> mapping = found.getValue();
		if (!mapping.getValue().equals(lastPath)) {
			try {
				content =
					GpxUtil.expandToTimedWaypoints(gpxLikeFiles.readGpxDecompressed(mapping.getValue())).stream()
					.collect(Collectors.toMap(
						p -> p.getTime().get().toEpochMilli(),
						p -> p,
						(a, b) -> a,
						TreeMap::new
					));
				lastPath = mapping.getValue();
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		Map.Entry<Long, WayPoint> entry = content.floorEntry(milli);
		if (entry == null) {
			return null;
		}
		if (milli - entry.getKey() >= 10_000) {
			return null;
		}
		return Pair.of(entry.getValue(), lastPath);
	}
}
