package com.github.kvr000.zbynekgps.gpstool.command;

import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.geo.GeoCalc;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpxUtil;
import com.github.kvr000.zbynekgps.gpstool.gpxlike.io.GpxLikeFiles;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.stream.Stream;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FindCommand extends AbstractCommand
{
	final GpxLikeFiles gpxLikeFiles;

	final ZbynekGpsTool.Options mainOptions;

	Options options;

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "--source-dir":
			options.sourceDir = needArgsParam(options.sourceDir, args);
			return true;

		case "--source-strava-csv":
			options.sourceStravaCsv = needArgsParam(options.sourceStravaCsv, args);
			return true;

		case "--since":
			Instant since = Instant.parse(needArgsParam(null, args));
			options.filters.add(new SinceFilter(since));
			return true;

		case "--till":
			Instant till = Instant.parse(needArgsParam(null, args));
			options.filters.add(new TillFilter(till));
			return true;

		case "--find-point":
			double[][] findPointDefs = Stream.of(needArgsParam(null, args).split(":"))
					.map(one -> {
						String[] findPointDefStr = one.split(",");
						if (findPointDefStr.length != 3) {
							throw new IllegalArgumentException("--find-point requires arguments lat,lon,radius , possibly multiple separated by :, got: " + one);
						}
						return Stream.of(findPointDefStr).mapToDouble(Double::parseDouble).toArray();
					})
					.toArray(double[][]::new);
			options.filters.add(new FindPointFilter(findPointDefs));
			return true;

		case "--dismiss-if-in-zone":
			double[][] dismissPointDefs = Stream.of(needArgsParam(null, args).split(":"))
				.map(one -> {
					String[] findPointDefStr = one.split(",");
					if (findPointDefStr.length != 3) {
						throw new IllegalArgumentException("--dismiss-if-in-zone requires arguments lat,lon,radius , possibly multiple separated by :, got: " + one);
					}
					return Stream.of(findPointDefStr).mapToDouble(Double::parseDouble).toArray();
				})
				.toArray(double[][]::new);
			options.filters.add(new DismissIfInZoneFilter(dismissPointDefs));
			return true;

		case "--decrease-density":
			long intervalSeconds = Long.parseLong(needArgsParam(null, args));
			options.filters.add(new DecreaseDensityFilter(intervalSeconds));
			return true;

		case "--remove-extensions":
			options.filters.add(new RemoveExtensionsFilter());
			return true;

		case "--remove-privacy-zone":
			String[] zoneStr = needArgsParam(null, args).split(",");
			if (zoneStr.length != 3) {
				throw new IllegalArgumentException("--exclude-privacy-zone requires argument lat,lon,radius");
			}
			double[] zoneDef = Stream.of(zoneStr).mapToDouble(Double::parseDouble).toArray();
			options.filters.add(new RemovePrivacyZoneFilter(zoneDef));
			return true;

		case "--export-gpx":
			Path directory = Paths.get(needArgsParam(null, args));
			options.commands.add(new ExportGpxCommand(directory));
			return true;

		case "--print-id-and-found-time":
			DateTimeFormatter idFoundTime = DateTimeFormatter.ofPattern(needArgsParam(null, args));
			options.commands.add(new PrintIdAndTimeCommand(idFoundTime));
			return true;

		case "--group-found-time":
			DateTimeFormatter groupFoundTime = DateTimeFormatter.ofPattern(needArgsParam(null, args));
			options.commands.add(new GroupFoundTimeCommand(groupFoundTime));
			return true;

		case "--skip-distance":
			options.skipDistance = Double.parseDouble(needArgsParam(options.skipDistance, args));
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if ((options.sourceDir == null) == (options.sourceStravaCsv == null)) {
			return usage(context, "One of --source-dir or --source-strava-csv must be specified");
		}
		return EXIT_CONTINUE;
	}

	@Override
	protected void createOptions(CommandContext context)
	{
		this.options = new Options();
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.<String, String>builder()
			.put("--source-dir directory", "read files from the directory")
			.put("--source-strava-csv file", "read files from Strava activities.csv file")
			.put("--since time", "filters by activity start time being higher inclusive (YYYY-MM-DDTHH:mm:ssZ)")
			.put("--till time", "filters by activity start time being lower exclusive (YYYY-MM-DDTHH:mm:ssZ)")
			.put("--find-point lat,lon,radius:...", "find one of the points with radius distance")
			.put("--dismiss-if-in-zone lat,lon,radius:...", "excludes activity completely if in zone (full privacy)")
			.put("--decrease-density interval-seconds", "decreases density of data to interval")
			.put("--remove-extensions", "removes all extensions")
			.put("--print-id-and-found-time time-format", "prints id and found local time")
			.put("--group-found-time time-format", "groups and prints found time")
			.put("--export-gpx directory", "exports found files to directory/id.gpx files")
			.put("--remove-privacy-zone lat,lon,radius", "removes privacy zone from output")
			.put("--skip-distance radius", "starts searching after leaving radius from start")
			.build();
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		LinkedHashMap<String, FileData> inputs = new LinkedHashMap<>();

		if (options.sourceStravaCsv != null) {
			try (Reader activitiesCsv = Files.newBufferedReader(Paths.get(options.sourceStravaCsv));
				 CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(activitiesCsv);
			) {
				for (CSVRecord record : parser) {
					String filename = record.get("Filename");
					if (filename.isEmpty()) {
						continue;
					}
					FileData fileData = new FileData();
					fileData.id = record.get("Activity ID");
					fileData.filename = Paths.get(options.sourceStravaCsv).resolveSibling(filename);
					fileData.metadata = ImmutableMap.of(
							"id", fileData.id,
							"name", record.get("Activity Name")
						);
					inputs.put(fileData.id, fileData);
				}
			}
		}
		else {
			for (Path filename: gpxLikeFiles.listFiles(Paths.get(options.sourceDir))) {
				FileData fileData = new FileData();
				fileData.filename = filename;
				fileData.id = FilenameUtils.removeExtension(FilenameUtils.removeExtension(filename.getFileName().toString()));
				fileData.metadata = ImmutableMap.of(
						"id", fileData.id,
						"name", fileData.id
				);
				inputs.put(fileData.filename.getFileName().toString(), fileData);
			}
		}

		AtomicLong count = new AtomicLong();
		AtomicLong found = new AtomicLong();
		inputs.values().parallelStream()
				.peek(fileData -> count.incrementAndGet())
				.map(fileData -> {
					try {
						return Map.entry(fileData, new MutableObject<>(readGpxLike(fileData.filename)));
					} catch (IOException ex) {
						log.error("Failed to read file: file={}", fileData.filename, ex);
						return null;
					}
				})
				.filter(Objects::nonNull)
				.filter(e -> options.filters.stream()
						.map(filter -> filter.test(e.getKey(), e.getValue()))
						.reduce(true, Boolean::logicalAnd)
				)
				.peek(e -> options.commands.forEach(command -> command.collectUnordered(e.getKey(), e.getValue().getValue())))
				.map(Map.Entry::getKey)
				.forEachOrdered(fileData -> {
					options.commands.forEach(command -> command.collectOrdered(fileData));
					found.incrementAndGet();
				});

		log.info("Analyzed files in: count={} found={} time={} ms", count, found, watch.elapsed(TimeUnit.MILLISECONDS));

		options.commands.forEach(Command::finish);

		return EXIT_SUCCESS;
	}


	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	private GPX readGpxLike(Path filePath) throws IOException
	{
		String filename = filePath.toString();
		if (filename.endsWith(".gz") && !Files.exists(Paths.get(filename)) && Files.exists(Paths.get(FilenameUtils.removeExtension(filename)))) {
			filename = FilenameUtils.removeExtension(filename);
		}
		try {
			return gpxLikeFiles.readGpxDecompressed(Paths.get(filename));
		}
		catch (IOException ex) {
			throw new IOException("Failed to read file: " + filename + " : " + ex.getMessage(), ex);
		}
	}

	static Optional<double[]> getFirstPoint(GPX gpx)
	{
		return gpx.tracks().flatMap(Track::segments).flatMap(TrackSegment::points)
			.map(wp -> new double[]{ wp.getLatitude().doubleValue(), wp.getLongitude().doubleValue() })
			.findFirst();
	}

	@RequiredArgsConstructor
	public static class SinceFilter implements BiPredicate<FileData, Mutable<GPX>>
	{
		final Instant since;

		@Override
		public boolean test(FileData fileData, Mutable<GPX> gpx)
		{
			List<WayPoint> waypoints = gpx.getValue().tracks()
					.flatMap(track -> track.segments().flatMap(segment -> segment.points()))
					.toList();

			for (WayPoint waypoint : waypoints) {
				if (waypoint.getTime().isPresent()) {
					return !waypoint.getTime().get().isBefore(since);
				}
			}
			return false;
		}
	}

	@RequiredArgsConstructor
	public static class TillFilter implements BiPredicate<FileData, Mutable<GPX>>
	{
		final Instant till;

		@Override
		public boolean test(FileData fileData, Mutable<GPX> gpx)
		{
			List<WayPoint> waypoints = gpx.getValue().tracks()
					.flatMap(track -> track.segments().flatMap(segment -> segment.points()))
					.toList();

			for (WayPoint waypoint : waypoints) {
				if (waypoint.getTime().isPresent()) {
					return waypoint.getTime().get().isBefore(till);
				}
			}
			return false;
		}
	}

	@RequiredArgsConstructor
	public class FindPointFilter implements BiPredicate<FileData, Mutable<GPX>>
	{
		final double[][] searchPoints;

		@Override
		public boolean test(FileData fileData, Mutable<GPX> gpx)
		{
			double[] skippingStart = options.skipDistance != null ? getFirstPoint(gpx.getValue()).orElse(null) : null;
			List<WayPoint> waypoints = GpxUtil.expandToWaypoints(gpx.getValue());

			for (WayPoint waypoint : waypoints) {
				double latitude = waypoint.getLatitude().doubleValue();
				double longitude = waypoint.getLongitude().doubleValue();
				if (skippingStart != null) {
					if (GeoCalc.isWithinRadius(latitude, longitude, skippingStart[0], skippingStart[1], options.skipDistance)) {
						continue;
					}
					else {
						skippingStart = null;
					}
				}
				if (waypoint.getTime().isPresent()) {
					for (double[] point: searchPoints) {
						if (GeoCalc.isWithinRadius(latitude, longitude, point[0], point[1], point[2])) {
							LocalDateTime timestamp = waypoint.getTime().get().atZone(ZoneId.systemDefault()).toLocalDateTime();
							fileData.attributes.put("foundPointLdt", timestamp);
							return true;
						}
					}
				}
			}
			return false;
		}
	}

	@RequiredArgsConstructor
	public class DismissIfInZoneFilter implements BiPredicate<FileData, Mutable<GPX>>
	{
		final double[][] searchPoints;

		@Override
		public boolean test(FileData fileData, Mutable<GPX> gpx)
		{
			List<WayPoint> waypoints = GpxUtil.expandToTimedWaypoints(gpx.getValue());

			for (WayPoint waypoint: waypoints) {
				double latitude = waypoint.getLatitude().doubleValue();
				double longitude = waypoint.getLongitude().doubleValue();
				if (waypoint.getTime().isPresent()) {
					for (double[] point : searchPoints) {
						if (GeoCalc.isWithinRadius(latitude, longitude, point[0], point[1], point[2])) {
							return false;
						}
					}
				}
			}
			return true;
		}
	}

	@RequiredArgsConstructor
	public static class RemovePrivacyZoneFilter implements BiPredicate<FileData, Mutable<GPX>>
	{
		final double[] privacyZone;

		@Override
		public boolean test(FileData fileData, Mutable<GPX> gpx)
		{
			GPX.Builder gpxBuilder = gpx.getValue().toBuilder();
			{
				boolean started = false;
				List<Track> tracks = new ArrayList<>();
				for (Track track : gpxBuilder.tracks()) {
					List<TrackSegment> segments = new ArrayList<>();
					for (TrackSegment segment : track.getSegments()) {
						List<WayPoint> wayPoints = new ArrayList<>();
						for (WayPoint wayPoint : segment.getPoints()) {
							if (!started) {
								double latitude = wayPoint.getLatitude().doubleValue();
								double longitude = wayPoint.getLongitude().doubleValue();
								if (GeoCalc.isWithinRadius(latitude, longitude, privacyZone[0], privacyZone[1], privacyZone[2])) {
									continue;
								}
								else {
									started = true;
								}
							}
							wayPoints.add(wayPoint);
						}
						if (!wayPoints.isEmpty()) {
							segments.add(segment.toBuilder().points(wayPoints).build());
						}
					}
					if (!segments.isEmpty()) {
						tracks.add(track.toBuilder().segments(segments).build());
					}
				}
				gpxBuilder.tracks(tracks);
			}
			{
				boolean started = false;
				List<Track> tracks = new ArrayList<>();
				tracks.clear();
				for (Track track : gpxBuilder.tracks().reversed()) {
					List<TrackSegment> segments = new ArrayList<>();
					for (TrackSegment segment : track.getSegments().reversed()) {
						List<WayPoint> wayPoints = new ArrayList<>();
						for (WayPoint wayPoint : segment.getPoints().reversed()) {
							if (!started) {
								double latitude = wayPoint.getLatitude().doubleValue();
								double longitude = wayPoint.getLongitude().doubleValue();
								if (GeoCalc.isWithinRadius(latitude, longitude, privacyZone[0], privacyZone[1], privacyZone[2])) {
									continue;
								}
								else {
									started = true;
								}
							}
							wayPoints.add(wayPoint);
						}
						if (!wayPoints.isEmpty()) {
							segments.add(segment.toBuilder().points(wayPoints.reversed()).build());
						}
					}
					if (!segments.isEmpty()) {
						tracks.add(track.toBuilder().segments(segments.reversed()).build());
					}
				}
				gpxBuilder.tracks(tracks.reversed());
			}
			gpx.setValue(gpxBuilder.build());
			return !gpx.getValue().getTracks().isEmpty();
		}
	}

	@RequiredArgsConstructor
	public static abstract class ModifyWaypointsFilter implements BiPredicate<FileData, Mutable<GPX>>
	{
		@Override
		public boolean test(FileData fileData, Mutable<GPX> gpx)
		{
			GPX.Builder gpxBuilder = gpx.getValue().toBuilder();
			{
				List<Track> tracks = new ArrayList<>();
				for (Track track : gpxBuilder.tracks()) {
					List<TrackSegment> segments = new ArrayList<>();
					for (TrackSegment segment : track.getSegments()) {
						List<WayPoint> wayPoints = modifyWayPoints(segment.getPoints());
						if (!wayPoints.isEmpty()) {
							segments.add(segment.toBuilder().points(wayPoints).build());
						}
					}
					if (!segments.isEmpty()) {
						tracks.add(track.toBuilder().segments(segments).build());
					}
				}
				gpxBuilder.tracks(tracks);
			}
			gpx.setValue(gpxBuilder.build());
			return !gpxBuilder.tracks().isEmpty();
		}

		public abstract List<WayPoint> modifyWayPoints(List<WayPoint> points);
	}

	@RequiredArgsConstructor
	public static class DecreaseDensityFilter extends ModifyWaypointsFilter
	{
		final long intervalSeconds;

		@Override
		public List<WayPoint> modifyWayPoints(List<WayPoint> points)
		{
			List<WayPoint> out = new ArrayList<>();
			Instant last = Instant.MIN;
			int i = 0, len = points.size();
			for (WayPoint wayPoint : points) {
				if (i++ == 0 || i == len) {
					out.add(wayPoint);
					if (wayPoint.getTime().isPresent()) {
						last = wayPoint.getTime().get();
					}
				}
				else if (wayPoint.getTime().isPresent() && last.until(wayPoint.getTime().get(), ChronoUnit.SECONDS) >= intervalSeconds) {
					out.add(wayPoint);
					last = wayPoint.getTime().get();
				}
			}
			return out;
		}
	}

	@RequiredArgsConstructor
	public static class RemoveExtensionsFilter extends ModifyWaypointsFilter
	{
		@Override
		public List<WayPoint> modifyWayPoints(List<WayPoint> points)
		{
			return points.stream()
				.map(p -> p.toBuilder().extensions(null).build())
				.toList();
		}
	}

	public static class Command
	{
		public void collectUnordered(FileData fileData, GPX gpx)
		{
		}

		public void collectOrdered(FileData fileData)
		{
		}

		public void finish()
		{
		}
	}

	@RequiredArgsConstructor
	public static class PrintIdAndTimeCommand extends Command
	{
		final DateTimeFormatter formatter;

		final ArrayList<Map.Entry<String, String>> fileAndTime = new ArrayList<>();

		@Override
		public void collectOrdered(FileData fileData)
		{
			String formatted = formatter.format(Optional.ofNullable((TemporalAccessor) fileData.attributes.get("foundPointLdt"))
					.orElseThrow(() -> new IllegalStateException("foundPointLdt not found, missing --find-point filter")));
			fileAndTime.add(Map.entry(fileData.id, formatted));
		}

		@Override
		public void finish()
		{
			fileAndTime.forEach(e ->
				System.out.printf("%s\t%s\n",
						e.getKey(),
						e.getValue())
			);
		}
	}

	@RequiredArgsConstructor
	public static class GroupFoundTimeCommand extends Command
	{
		final DateTimeFormatter formatter;

		final ConcurrentHashMap<String, List<String>> counts = new ConcurrentHashMap<>();

		@Override
		public void collectOrdered(FileData fileData)
		{
			String formatted = formatter.format(Optional.ofNullable((TemporalAccessor) fileData.attributes.get("foundPointLdt"))
					.orElseThrow(() -> new IllegalStateException("foundPointLdt not found, missing --find-point filter")));
			counts.computeIfAbsent(formatted, k -> new CopyOnWriteArrayList<>()).add(fileData.id);
		}

		@Override
		public void finish()
		{
			counts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
				System.out.printf("%s\t%d%s\n",
						e.getKey(),
						e.getValue().size(),
						e.getValue().size() <= 5 ? "\t" + String.join("\t", e.getValue()) : "");
			});

		}
	}

	@RequiredArgsConstructor
	public class ExportGpxCommand extends Command
	{
		final Path directory;

		@Override
		public void collectUnordered(FileData fileData, GPX gpx)
		{
			Path output = directory.resolve(fileData.id + ".gpx");
			try {
				gpxLikeFiles.writeGpx(output, gpx);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
	}

	public static class FileData
	{
		String id;

		Map<String, Object> metadata;

		Map<String, Object> attributes = new LinkedHashMap<>();

		Path filename;
	}

	public static class Options
	{
		String sourceDir;

		String sourceStravaCsv;

		Double skipDistance;

		List<BiPredicate<FileData, Mutable<GPX>>> filters = new ArrayList<>();

		List<Command> commands = new ArrayList<>();
	}
}
