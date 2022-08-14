package com.github.kvr000.zbynekgps.cmdutil.command;

import com.github.kvr000.zbynekgps.cmdutil.gpx.util.GpxCalculation;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Point;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.lang3.mutable.MutableObject;

import javax.swing.text.Segment;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


@Log4j2
public class ConcatCommand extends AbstractCommand
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
		if (remaining.size() < 1) {
			return usage(context, "Need one or more parameters as source files");
		}
		options.inputs = remaining;
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (options.output == null) {
			return usage(context, "-o output option is mandatory");
		}
		if (options.inputs == null) {
			return usage(context, "input files required");
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		GPX.Builder output = null;

		NavigableMap<Instant, TrackDetail> tracks = new TreeMap<>(); // end : segment
		for (String input: options.inputs) {
			Stopwatch watch = Stopwatch.createStarted();
			GPX file = GPX.read(Paths.get(input));
			if (output == null) {
				output = file.toBuilder();
			}
			file.tracks()
				.forEach(t -> mergeTrack(tracks, t));
			log.info("Process file: file={} time={} ms", input, watch.elapsed(TimeUnit.MILLISECONDS));
		}
		output.tracks(tracks.values().stream()
			.map(td -> td.track)
			.collect(ImmutableList.toImmutableList())
		);
		Stopwatch watch = Stopwatch.createStarted();
		GPX.write(output.build(), Paths.get(options.output));
		log.info("Written output in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	static void mergeTrack(NavigableMap<Instant, TrackDetail> tracks, Track track)
	{
		Queue<Track> pending = new LinkedList<>();
		pending.add(track);
		TRACK: while (!pending.isEmpty()) {
			Track current = pending.remove();
			List<TrackSegment> segments = new ArrayList<>();
			SEGMENT: for (int i = 0; i < current.getSegments().size(); ++i) {
				TrackSegment segment = current.getSegments().get(i);
				Instant start = segment.points()
					.filter(p -> p.getTime().isPresent())
					.findFirst()
					.flatMap(WayPoint::getTime)
					.orElse(null);
				if (start == null) {
					continue SEGMENT;
				}
				Instant end = Streams.findLast(segment.points()
						.filter(p -> p.getTime().isPresent())
					)
					.flatMap(WayPoint::getTime)
					.get();
				for (TrackDetail conflicting: tracks.tailMap(start).values()) {
					if (conflicting.start.isAfter(end)) {
						break;
					}
					if (!start.isBefore(conflicting.start)) {
						// conflicting.start <= start
						if (!end.isAfter(conflicting.end)) {
							// conflicting.end >= end : ignore the segment
							continue SEGMENT;
						}
						// conflicting.end < end : retain the tail
						pending.add(current.toBuilder()
							.segments(ImmutableList.<TrackSegment>builder()
									.add(segment.toBuilder()
										.points(segment.points()
											.filter(p -> p.getTime().isPresent() &&
												p.getTime().get().isAfter(conflicting.end)
											)
											.collect(ImmutableList.toImmutableList())
										)
										.build()
									)
									.addAll(current.getSegments().subList(i+1, current.getSegments().size()))
								.build()
							)
							.build()
						);
						insertTrack(tracks, current.toBuilder().segments(segments).build());
						continue TRACK;
					}
					// conflicting.start > start
					else if (!end.isAfter(conflicting.end)) {
						// conflicting.end >= end : retain the beginning
						segment = segment.toBuilder()
							.points(segment.points()
								.filter(p -> p.getTime().isPresent() &&
									p.getTime().get().isBefore(conflicting.start)
								)
								.collect(ImmutableList.toImmutableList())
							)
							.build();
					}
					else {
						// conflicting.end < end : retain the beginning and end
						pending.add(current.toBuilder()
							.segments(ImmutableList.<TrackSegment>builder()
								.add(segment.toBuilder()
									.points(segment.points()
										.filter(p ->
											p.getTime().isPresent() &&
												p.getTime().get().isAfter(conflicting.end)
										)
										.collect(ImmutableList.toImmutableList())
									)
									.build()
								)
								.addAll(current.getSegments().subList(i+1, current.getSegments().size()))
								.build()
							)
							.build()
						);
						segment = segment.toBuilder()
							.points(segment.points()
								.filter(p -> p.getTime().isPresent() &&
									p.getTime().get().isBefore(conflicting.start)
								)
								.collect(ImmutableList.toImmutableList())
							)
							.build();
						segments.add(segment);
						insertTrack(tracks, current.toBuilder().segments(segments).build());
						continue TRACK;
					}
				}
				if (!segment.isEmpty()) {
					segments.add(segment);
				}
			}
			insertTrack(tracks, current.toBuilder().segments(segments).build());
		}
	}

	private static void insertTrack(NavigableMap<Instant, TrackDetail> tracks, Track track)
	{
		if (!track.isEmpty()) {
			TrackDetail detail = TrackDetail.from(track);
			if (detail != null) {
				log.debug("Adding track: start={} end={}", detail.start, detail.end);
				tracks.put(detail.end, detail);
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
			"-o output", "output filename"
		);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"source", "file to retrack"
		);
	}

	public static class Options
	{
		private String output;

		private List<String> inputs;
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
