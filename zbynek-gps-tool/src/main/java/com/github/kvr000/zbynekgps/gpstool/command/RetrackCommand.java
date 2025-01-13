package com.github.kvr000.zbynekgps.gpstool.command;

import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpsCalculation;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpxFiles;
import com.github.kvr000.zbynekgps.gpstool.util.TreeIterators;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Streams;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Point;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class RetrackCommand extends AbstractCommand
{
	final GpxFiles gpxFiles;

	final ZbynekGpsTool.Options mainOptions;

	Options options;

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "--position-prio":
			options.positionPriority = Stream.of(needArgsParam(options.positionPriority, args).split(","))
				.map(Integer::parseInt)
				.collect(Collectors.toList());
			return true;

		case "--elevation-prio":
			options.positionPriority = Stream.of(needArgsParam(options.elevationPriority, args).split(","))
				.map(Integer::parseInt)
				.collect(Collectors.toList());
			return true;

		case "--calc-missing":
			if (true) {
				throw new IllegalArgumentException("option unsupported");
			}
			if (options.calculateMissing) {
				throw new IllegalArgumentException("option specified twice");
			}
			options.calculateMissing = true;
			return true;

		case "--merge-timeout":
			options.mergeTimeout = Integer.parseInt(needArgsParam(options.mergeTimeout, args));
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		ImmutableList<String> remaining = ImmutableList.copyOf(args);
		if (remaining.size() < 1) {
			return usage(context, "Need at least one parameter as source file");
		}
		options.inputs = remaining;
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}
		if (options.positionPriority == null) {
			options.positionPriority = IntStream.range(0, options.inputs.size())
				.boxed()
				.collect(Collectors.toList());
		}
		else {
			for (int i: options.positionPriority) {
				if (i < 0 || i >= options.inputs.size()) {
					return usage(context, "--position-prio requires list within range of 0 and less than number of sources");
				}
			}
		}
		if (options.elevationPriority == null) {
			options.elevationPriority = IntStream.range(0, options.inputs.size())
				.boxed()
				.collect(Collectors.toList());
		}
		else {
			for (int i: options.elevationPriority) {
				if (i < 0 || i >= options.inputs.size()) {
					return usage(context, "--elevation-prio requires list within range of 0 and less than number of sources");
				}
			}
		}
		if (options.mergeTimeout == null) {
			options.mergeTimeout = 2;
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();
		List<GPX> gpxs = options.inputs.parallelStream()
			.map(name -> {
				try {
					return gpxFiles.readGpxDecompressed(Paths.get(name));
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			})
			.collect(Collectors.toList());
		log.info("Read input files in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		List<NavigableMap<Instant, PointSource>> pointData = buildPointData(gpxs);
		PointSources pointSources = buildPointSources(pointData);

		if (mainOptions.isDebug()) {
			GPX.write(
				gpxFiles.buildGpx(toWayPoints(pointSources.positions.values())),
				Paths.get(mainOptions.getOutput() + ".debug.gpx")
			);
		}

		watch.reset(); watch.start();
		GPX output = enrichLocations(gpxs.get(0), pointSources);
		log.info("Retracked locations in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));

		watch.reset(); watch.start();
		GPX.write(output, Paths.get(mainOptions.getOutput()));
		log.info("Written output in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
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
			"--position-prio", "comma separated source file indexes to obtain position from, 0 refers to main file - example 0,2,1",
			"--elevation-prio", "comma separated source file indexes to obtain elevation from, 0 refers to main file - example 0,2,1",
			"--calc-missing", "do not calculate missing points",
			"--merge-timeout", "minimum timeout to include point from different source"
		);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"main-file", "main source file",
			"source-files...", "additional files to use as source, the first priority is main-file"
		);
	}

	GPX enrichLocations(GPX main, PointSources pointSources)
	{
		GPX result = main.toBuilder()
			.tracks(main.getTracks().stream()
				.map(s -> enrichLocations(s, pointSources))
				.collect(ImmutableList.toImmutableList())
			)
			.build();

		return result;
	}

	Track enrichLocations(Track track, PointSources pointSources)
	{
		return track.toBuilder()
			.segments(track.segments().parallel()
				.map(s -> enrichLocations(s, pointSources))
				.collect(ImmutableList.toImmutableList())
			)
			.build();
	}

	TrackSegment enrichLocations(TrackSegment segment, PointSources pointSources)
	{
		return segment.toBuilder()
				.points(segment.getPoints().stream()
						.map(p -> calculateLocation(p, pointSources))
						.collect(ImmutableList.toImmutableList())
				)
				.build();
	}

	WayPoint calculateLocation(WayPoint p, PointSources pointSources)
	{
		final Instant time = p.getTime().get();
		final WayPoint.Builder b = p.toBuilder();
		PointSource position;
		if ((position = pointSources.getPositions().get(time)) != null) {
			b
				.lon(position.getPoint().getLongitude())
				.lat(position.getPoint().getLatitude());
		}
		else {
			WayPoint o = Optional.ofNullable(pointSources.getPositions().floorEntry(time)).map(Map.Entry::getValue).map(PointSource::getPoint).orElse(null);
			WayPoint n = Optional.ofNullable(pointSources.getPositions().ceilingEntry(p.getTime().get())).map(Map.Entry::getValue).map(PointSource::getPoint).orElse(null);
			if (o != null && n != null) {
				Point m = GpsCalculation.calculateMidpoint(o, n, time);
				b
					.lon(m.getLongitude())
					.lat(m.getLatitude());
			}
		}
		PointSource elevation;
		if ((elevation = pointSources.getPositions().get(time)) != null) {
			b.ele(elevation.getPoint().getElevation().orElse(null));
		}
		else {
			WayPoint o = Optional.ofNullable(pointSources.getElevations().floorEntry(time)).map(Map.Entry::getValue).map(PointSource::getPoint).orElse(null);
			WayPoint n = Optional.ofNullable(pointSources.getElevations().ceilingEntry(p.getTime().get())).map(Map.Entry::getValue).map(PointSource::getPoint).orElse(null);
			if (o != null && n != null) {
				Point m = GpsCalculation.calculateMidpoint(o, n, time);
				b
					.ele(m.getElevation().orElse(null));
			}
		}
		return b.build();
	}

	PointSources buildPointSources(List<NavigableMap<Instant, PointSource>> pointData)
	{
		return PointSources.builder()
			.positions(TreeIterators.mergeMapsPrioritized(pointData, options.positionPriority))
			.elevations(TreeIterators.mergeMapsPrioritized(
				pointData.stream()
					.map(map -> map.entrySet().stream()
						.filter(ps -> ps.getValue().getPoint().getElevation().isPresent())
						.collect(ImmutableSortedMap.toImmutableSortedMap(Comparator.naturalOrder(), Map.Entry::getKey, Map.Entry::getValue))
					)
					.collect(Collectors.toList()),
				options.elevationPriority))
			.build();
	}

	/**
	 * Builds Map<Instant, PointSource> from sources, filtering out points without updated position.
	 *
	 * @param sources
	 *      list of GPX sources
	 *
	 * @return
	 *      sources mapped into Map<Instant, PointSource> only with points with valid position.
	 */
	List<NavigableMap<Instant, PointSource>> buildPointData(List<GPX> sources)
	{
		List<NavigableMap<Instant, WayPoint>> sourcesPoints = sources.stream()
			.map(source -> source.tracks()
				.flatMap(track -> track.segments())
				.flatMap(segment -> segment.points())
				.filter(point -> point.getTime().isPresent())
				.collect(Collectors.toMap(
					p -> p.getTime().get(),
					p ->p,
					(a, b) -> { throw new IllegalArgumentException("time appeared twice"); },
					TreeMap::new
				))
			)
			.collect(Collectors.toList());
		List<NavigableMap<Instant, PointSource>> result =
			Streams.zip(IntStream.rangeClosed(0, sourcesPoints.size()).boxed(), sourcesPoints.stream(), (id, map) -> {
				TreeMap<Instant, PointSource> tree = new TreeMap<>();
				WayPoint last = null;
				for (WayPoint n : map.values()) {
					if (last == null || (!n.getLongitude().equals(last.getLongitude()) || !n.getLatitude().equals(last.getLatitude()))) {
						tree.put(n.getTime().get(), new PointSource(n, id, map));
						last = n;
					}
				}
				return tree;
			})
			.collect(Collectors.toList());
		return result;
	}

	public static List<WayPoint> toWayPoints(Collection<PointSource> pointSources)
	{
		return pointSources.stream()
			.map(ps -> ps.point)
			.toList();
	}

	                                         @Value
	                                         @Builder
	public static class PointSources
	{
		/** Positions combined from all sources */
		private final NavigableMap<Instant, PointSource> positions;

		/** Elevations combined from all sources */
		private final NavigableMap<Instant, PointSource> elevations;
	}

	@Value
	public static class PointSource
	{
		/** The particular point */
		private final WayPoint point;

		/** Id of source data */
		private final int sourceId;

		/** Full original Map of points of this source */
		private final NavigableMap<Instant, WayPoint> source;
	}

	public static class Options
	{
		List<String> inputs;

		List<Integer> positionPriority;

		List<Integer> elevationPriority;

		boolean calculateMissing = false;

		Integer mergeTimeout;
	}
}
