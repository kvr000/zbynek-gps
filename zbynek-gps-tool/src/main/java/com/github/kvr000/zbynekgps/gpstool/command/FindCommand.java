package com.github.kvr000.zbynekgps.gpstool.command;

import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.fit.FitFiles;
import com.github.kvr000.zbynekgps.gpstool.geo.GeoCalc;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpxFiles;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
	final GpxFiles gpxFiles;

	final FitFiles fitFiles;

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
		return ImmutableMap.of(
				"--source-dir directory", "read files from the directory",
				"--source-strava-csv file", "read files from Strava activities.csv file",
				"--since time", "filters by activity start time being higher inclusive (YYYY-MM-DDTHH:mm:ssZ)",
				"--till time", "filters by activity start time being lower exclusive (YYYY-MM-DDTHH:mm:ssZ)",
				"--find-point lat,lon,radius:...", "find one of the points with radius distance",
				"--print-id-and-found-time time-format", "prints id and found local time",
				"--group-found-time time-format", "groups and prints found time",
				"--export-gpx directory", "exports found files to directory/id.gpx files",
				"--remove-privacy-zone lat,lon,radius", "removes privacy zone from output"
		);
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
			for (File filename: FileUtils.listFiles(new File(options.sourceDir), new String[]{ "gpx", "gpx.gz", "fit", "fit.gz" }, false)) {
				FileData fileData = new FileData();
				fileData.filename = filename.toPath();
				fileData.id = FilenameUtils.removeExtension(FilenameUtils.removeExtension(filename.getName()));
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
		try (InputStream core = new FileInputStream(filename)) {
			InputStream input = core;
			if (filename.endsWith(".gpx") || filename.endsWith(".gpx.gz")) {
				return gpxFiles.readGpxDecompressed(input);
			}
			else if (filename.endsWith(".fit") || filename.endsWith(".fit.gz")) {
				return fitFiles.readFitDecompressed(input);
			}
			else {
				throw new IOException("Unsupported file extension, only gpx or fit (optionally gzip-ped) are supported: " + filename);
			}
		}
		catch (IOException ex) {
			throw new IOException("Failed to read file: " + filename + " : " + ex.getMessage(), ex);
		}
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
	public static class FindPointFilter implements BiPredicate<FileData, Mutable<GPX>>
	{
		final double[][] searchPoints;

		@Override
		public boolean test(FileData fileData, Mutable<GPX> gpx)
		{
			List<WayPoint> waypoints = gpx.getValue().tracks()
					.flatMap(track -> track.segments().flatMap(segment -> segment.points()))
					.toList();

			for (WayPoint waypoint : waypoints) {
				if (waypoint.getTime().isPresent()) {
					LocalDateTime timestamp = waypoint.getTime().get().atZone(ZoneId.systemDefault()).toLocalDateTime();

					double latitude = waypoint.getLatitude().doubleValue();
					double longitude = waypoint.getLongitude().doubleValue();

					for (double[] point: searchPoints) {
						if (GeoCalc.isWithinRadius(latitude, longitude, point[0], point[1], point[2])) {
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
				gpxFiles.writeGpx(output, gpx);
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

		List<BiPredicate<FileData, Mutable<GPX>>> filters = new ArrayList<>();

		List<Command> commands = new ArrayList<>();
	}
}
