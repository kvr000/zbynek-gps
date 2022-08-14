package com.github.kvr000.zbynekgps.cmdutil.command;

import com.github.kvr000.zbynekgps.cmdutil.gpx.util.GpxCalculation;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Point;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.lang3.mutable.MutableObject;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


@Log4j2
public class RetrackCommand extends AbstractCommand
{
	private Options options;

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "-o":
			options.output = needArgsParam(options.output, args);
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		ImmutableList<String> remaining = ImmutableList.copyOf(args);
		if (remaining.size() != 1) {
			return usage(context, "Need one more parameter as source file");
		}
		options.mainInput = remaining.get(0);
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (options.output == null) {
			return usage(context, "-o output option is mandatory");
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();
		GPX main = GPX.read(Paths.get(options.mainInput));
		log.info("Read input file in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		watch.reset(); watch.start();
		GPX output = enrichLocations(main);
		log.info("Retracked locations in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		watch.reset(); watch.start();
		GPX.write(output, Paths.get(options.output));
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
			"-o output", "output filename"
		);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"source", "file to retrack"
		);
	}

	static GPX enrichLocations(GPX main)
	{
		MutableObject<WayPoint> previous = new MutableObject<>();
		TreeMap<Instant, WayPoint> locations = main.getTracks().stream()
			.flatMap(Track::segments)
			.flatMap(TrackSegment::points)
			.filter(p -> p.getTime().isPresent())
			.filter((WayPoint p) -> previous.getValue() == null || !(
				p.getLongitude().equals(previous.getValue().getLongitude()) &&
					p.getLatitude().equals(previous.getValue().getLatitude()))
			)
			.map(p -> { previous.setValue(p); return p; })
			.collect(Collectors.toMap(
				p -> p.getTime().get(),
				Function.identity(),
				(a, b) -> {throw new IllegalArgumentException("two points for the same time");},
				TreeMap::new
			));

		GPX result = main.toBuilder()
			.tracks(main.getTracks().stream()
				.map(s -> enrichLocations(s, locations))
				.collect(ImmutableList.toImmutableList())
			)
			.build();

		return result;
	}

	static Track enrichLocations(Track track, NavigableMap<Instant, WayPoint> locations)
	{
		return track.toBuilder()
			.segments(track.segments().parallel()
				.map(s -> enrichLocations(s, locations))
				.collect(ImmutableList.toImmutableList())
			)
			.build();
	}

	static TrackSegment enrichLocations(TrackSegment segment, NavigableMap<Instant, WayPoint> locations)
	{
		return segment.toBuilder()
				.points(segment.getPoints().stream()
						.map(p -> enrichLocation(p, locations))
						.collect(ImmutableList.toImmutableList())
				)
				.build();
	}

	static WayPoint enrichLocation(WayPoint p, NavigableMap<Instant, WayPoint> locations)
	{
		Instant time = p.getTime().get();
		if (locations.containsKey(time)) {
			return p;
		}
		WayPoint o = Optional.ofNullable(locations.floorEntry(time)).map(Map.Entry::getValue).orElse(null);
		WayPoint n = Optional.ofNullable(locations.ceilingEntry(p.getTime().get())).map(Map.Entry::getValue).orElse(null);
		if (o == null || n == null) {
			return p;
		}
		Point m = GpxCalculation.calculateMidpoint(o, n, time);
		return p.toBuilder()
				.lon(m.getLongitude())
				.lat(m.getLatitude())
				.ele(m.getElevation().orElse(null))
				.build();
	}

//	static GpxFile mergeFiles(GpxFile main, GpxFile add)
//	{
//		TreeMap<Instant, GpxPoint> addPoints = buildTree(add);
//
//		GpxFile result = main.toBuilder()
//				.segments(main.getSegments().stream()
//						.map(s -> mergeSegment(s, addPoints))
//						.collect(ImmutableList.toImmutableList())
//				)
//				.build();
//
//		return result;
//	}

//	static GpxSegment mergeSegment(GpxSegment segment, NavigableMap<Instant, GpxPoint> two)
//	{
//		return segment.toBuilder()
//				.points(Streams.stream(
//						TreeIterators.iterateEnriched(
//								segment.getPoints().stream()
//										.map(p -> new AbstractMap.SimpleImmutableEntry<>(p.getTime(), p))
//										.collect(ImmutableList.<Map.Entry<Instant, GpxPoint>>toImmutableList()).iterator(),
//								two
//						))
//						.map(p -> GpxMerger.mergePoints(p.getValue(), two.get(p.getKey())))
//						.collect(ImmutableList.toImmutableList())
//				)
//				.build();
//	}

//	static TreeMap<Instant, GpxPoint> buildTree(GpxFile file)
//	{
//		return file.getSegments().stream()
//				.flatMap(s -> s.getPoints().stream())
//				.collect(Collectors.toMap(
//						GpxPoint::getTime,
//						Function.identity(),
//						(a, b) -> { throw new IllegalArgumentException("two points for the same time"); },
//						TreeMap::new
//				));
//	}

	public static class Options
	{
		private String output;

		private String mainInput;

		private List<String> inputs;

		boolean completeLocation;
	}
}
