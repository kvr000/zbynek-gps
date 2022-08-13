package com.github.kvr000.zbynekgps.cmdutil.command;

import com.github.kvr000.zbynekgps.cmdutil.gpx.GpxMerger;
import com.github.kvr000.zbynekgps.cmdutil.gpx.GpxReader;
import com.github.kvr000.zbynekgps.cmdutil.gpx.model.GpxFile;
import com.github.kvr000.zbynekgps.cmdutil.gpx.model.GpxPoint;
import com.github.kvr000.zbynekgps.cmdutil.gpx.model.GpxSegment;
import com.github.kvr000.zbynekgps.cmdutil.gpx.util.GpxCalculation;
import com.github.kvr000.zbynekgps.cmdutil.util.TreeIterators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;


public class MergeCommand extends AbstractCommand
{
	private Options options = new Options();

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

	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		ImmutableList<String> remaining = ImmutableList.copyOf(args);
		if (remaining.size() < 1) {
			return usage(context, "Need at least one more parameter as merge files");
		}
		options.mainInput = remaining.get(0);
		options.inputs = remaining.subList(1, remaining.size());
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
		GpxFile main;
		try (InputStream input = new FileInputStream(options.mainInput)) {
			main = GpxReader.readGpx(input);
		}
		return EXIT_SUCCESS;
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
			"input", "main input filename",
			"additional...", "more files to merge"
		);
	}

	static GpxFile mergeFiles(GpxFile main, GpxFile add)
	{
		TreeMap<Instant, GpxPoint> addPoints = buildTree(add);

		GpxFile result = main.toBuilder()
				.segments(main.getSegments().stream()
						.map(s -> mergeSegment(s, addPoints))
						.collect(ImmutableList.toImmutableList())
				)
				.build();

		return result;
	}

	static GpxFile enrichLocations(GpxFile main)
	{
		TreeMap<Instant, GpxPoint> locations = main.getSegments().stream()
				.flatMap(s -> s.getPoints().stream())
				.filter(p -> p.getLon() != null && p.getLat() != null)
				.collect(Collectors.toMap(
						GpxPoint::getTime,
						Function.identity(),
						(a, b) -> { throw new IllegalArgumentException("two points for the same time"); },
						TreeMap::new
				));

		GpxFile result = main.toBuilder()
				.segments(main.getSegments().stream()
						.map(s -> enrichLocations(s, locations))
						.collect(ImmutableList.toImmutableList())
				)
				.build();

		return result;
	}

	static GpxSegment enrichLocations(GpxSegment segment, NavigableMap<Instant, GpxPoint> locations)
	{
		return segment.toBuilder()
				.points(segment.getPoints().stream()
						.map(p -> enrichLocation(p, locations))
						.collect(ImmutableList.toImmutableList())
				)
				.build();
	}

	static GpxPoint enrichLocation(GpxPoint p, NavigableMap<Instant, GpxPoint> locations)
	{
		if (p.getLon() != null && p.getLat() != null) {
			return p;
		}
		GpxPoint o = Optional.ofNullable(locations.floorEntry(p.getTime())).map(Map.Entry::getValue).orElse(null);
		GpxPoint n = Optional.ofNullable(locations.ceilingEntry(p.getTime())).map(Map.Entry::getValue).orElse(null);
		if (o == null || n == null) {
			return p;
		}
		GpxPoint m = GpxCalculation.calculateMidpoint(o, n, p.getTime());
		return p.toBuilder()
				.lon(m.getLon())
				.lat(m.getLat())
				.alt(m.getAlt())
				.build();
	}

	static GpxSegment mergeSegment(GpxSegment segment, NavigableMap<Instant, GpxPoint> two)
	{
		return segment.toBuilder()
				.points(Streams.stream(
						TreeIterators.iterateEnriched(
								segment.getPoints().stream()
										.map(p -> new AbstractMap.SimpleImmutableEntry<>(p.getTime(), p))
										.collect(ImmutableList.<Map.Entry<Instant, GpxPoint>>toImmutableList()).iterator(),
								two
						))
						.map(p -> GpxMerger.mergePoints(p.getValue(), two.get(p.getKey())))
						.collect(ImmutableList.toImmutableList())
				)
				.build();
	}

	static TreeMap<Instant, GpxPoint> buildTree(GpxFile file)
	{
		return file.getSegments().stream()
				.flatMap(s -> s.getPoints().stream())
				.collect(Collectors.toMap(
						GpxPoint::getTime,
						Function.identity(),
						(a, b) -> { throw new IllegalArgumentException("two points for the same time"); },
						TreeMap::new
				));
	}

	public static class Options
	{
		private String output;

		private String mainInput;

		private List<String> inputs;

		boolean completeLocation;
	}
}
