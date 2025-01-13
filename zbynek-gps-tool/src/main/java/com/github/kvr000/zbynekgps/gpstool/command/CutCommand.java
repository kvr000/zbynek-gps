package com.github.kvr000.zbynekgps.gpstool.command;

import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpxFiles;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpxUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.collect.Streams;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;

import javax.inject.Inject;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class CutCommand extends AbstractCommand
{
	private final GpxFiles gpxFiles;

	private final ZbynekGpsTool.Options mainOptions;

	private Options options;

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "-s":
			options.start = Instant.parse(needArgsParam(options.start, args));
			return true;

		case "-e":
			options.end = Instant.parse(needArgsParam(options.end, args));
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}
		if (options.start == null) {
			return usage(context, "-s start option is mandatory");
		}
		if (options.end == null) {
			return usage(context, "-e end option is mandatory");
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		GPX.Builder output;

		NavigableMap<Instant, TrackDetail> tracks = new TreeMap<>(); // end : segment

		GPX main = gpxFiles.readGpxDecompressed(Paths.get(mainOptions.getOutput()));
		output = main.toBuilder();
		output.tracks(main.tracks()
			.map(t -> cutTrack(t, options))
			.filter(Objects::nonNull)
			.filter(t -> !t.isEmpty())
			.collect(ImmutableList.toImmutableList())
		);
		GPX.write(output.build(), Paths.get(mainOptions.getOutput()));
		return EXIT_SUCCESS;
	}

	static Track cutTrack(Track track, Options options)
	{
		return track.toBuilder()
			.segments(track.segments()
				.flatMap(s -> cutSegment(s, options).stream())
				.collect(ImmutableList.toImmutableList())
			)
			.build();
	}

	static List<TrackSegment> cutSegment(TrackSegment segment, Options options)
	{
		Range<Instant> boundaries = GpxUtil.findBoundaries(segment);
		if (boundaries == null) {
			return ImmutableList.of(segment);
		}
		if (boundaries.upperEndpoint().isBefore(options.start) || boundaries.lowerEndpoint().isAfter(options.end)) {
			return ImmutableList.of(segment);
		}
		if (!options.start.isAfter(boundaries.lowerEndpoint())) {
			if (!options.end.isBefore(boundaries.upperEndpoint())) {
				return ImmutableList.of();
			}
			return ImmutableList.of(segment.toBuilder()
				.points(segment.points()
					.filter(p -> p.getTime().isPresent() &&
						p.getTime().get().isAfter(options.end))
					.collect(ImmutableList.toImmutableList())
				)
				.build()
			);
		}
		else if (!options.end.isBefore(boundaries.upperEndpoint())) {
			return ImmutableList.of(segment.toBuilder()
				.points(segment.points()
					.filter(p -> p.getTime().isPresent() &&
						p.getTime().get().isBefore(options.start))
					.collect(ImmutableList.toImmutableList())
				)
				.build());
		}
		else {
			return ImmutableList.of(
				segment.toBuilder()
					.points(segment.points()
						.filter(p -> p.getTime().isPresent() &&
							p.getTime().get().isBefore(options.start)
						)
						.collect(ImmutableList.toImmutableList())
					)
					.build(),
				segment.toBuilder()
					.points(segment.points()
						.filter(p -> p.getTime().isPresent() &&
							p.getTime().get().isAfter(options.end)
						)
						.collect(ImmutableList.toImmutableList())
					)
					.build()
			);
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
			"-o output", "output and input filename",
			"-s start", "start time to remove",
			"-e end", "end time until to remove (exclusive)"
		);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"source", "file to retrack"
		);
	}

	@AllArgsConstructor
	@NoArgsConstructor
	public static class Options
	{
		private Instant start;

		private Instant end;
	}

	@EqualsAndHashCode
	@ToString
	static class TrackDetail
	{
		private Instant start;
		private Instant end;
		private Track track;

		public static TrackDetail from(Track track)
		{
			TrackDetail that = new TrackDetail();
			that.start = track.segments()
				.flatMap(TrackSegment::points)
				.filter(p -> p.getTime().isPresent())
				.findFirst()
				.flatMap(p -> p.getTime())
				.orElse(null);
			if (that.start == null)
				return null;
			that.end = Streams.findLast(track.segments()
					.flatMap(TrackSegment::points)
					.filter(p -> p.getTime().isPresent())
				)
				.flatMap(p -> p.getTime())
				.orElse(null);
			that.track = track;
			return that;
		}
	}
}
