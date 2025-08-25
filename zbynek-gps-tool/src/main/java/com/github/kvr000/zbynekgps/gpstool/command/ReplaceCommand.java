package com.github.kvr000.zbynekgps.gpstool.command;

import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.geo.GeoCalc;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpsCalculation;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpxUtil;
import com.github.kvr000.zbynekgps.gpstool.gpxlike.io.GpxLikeFiles;
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
import org.apache.commons.lang3.BooleanUtils;

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
public class ReplaceCommand extends AbstractCommand
{
	final GpxLikeFiles gpxLikeFiles;

	final ZbynekGpsTool.Options mainOptions;

	Options options;

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "--detect-position":
			options.detectPosition =true;
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		options.mainSource = needArgsParam(null, args);
		options.inputs = ImmutableList.copyOf(args);
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}
		if (options.inputs.isEmpty()) {
			return usage(context, "no additional input files");
		}
		if (BooleanUtils.isFalse(options.detectPosition)) {
			return usage(context, "--detect-position is mandatory");
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
					return gpxLikeFiles.readGpxDecompressed(Paths.get(name));
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
				GpxUtil.buildGpx(toWayPoints(pointSources.positions.values())),
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

	Instant findClosestPoint(GPX main, WayPoint search)
	{
		WayPoint last = null;
		WayPoint bestPoint = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (WayPoint wayPoint: GpxUtil.expandToTimedWaypoints(main)) {
			double distance = wayPoint.distance(search).doubleValue();
			if (distance < bestDistance) {
				bestPoint = wayPoint;
			}
		}
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
			"--detect-position", "automatically detects position from other sources",
		);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"main-file", "main source file",
			"source-files...", "files to replace the segments in the main file"
		);
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
		String mainSource;

		List<String> inputs;

		Boolean detectPosition;
	}
}
