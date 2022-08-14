package com.github.kvr000.zbynekgps.cmdutil.command;

import com.google.common.collect.ImmutableList;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.LongStream;

import static org.testng.Assert.assertEquals;


public class ConcatCommandTest
{
	@Test
	public void mergeTrack_sequential_insertBoth()
	{
		NavigableMap<Instant, ConcatCommand.TrackDetail> tracks = new TreeMap<>();

		ConcatCommand.mergeTrack(tracks, createTrack(0, 2));
		ConcatCommand.mergeTrack(tracks, createTrack(3, 4));

		assertEquals(tracks,
			new TreeMap<>() {{
				put(Instant.ofEpochSecond(2), ConcatCommand.TrackDetail.from(createTrack(0, 2)));
				put(Instant.ofEpochSecond(4), ConcatCommand.TrackDetail.from(createTrack(3, 4)));
			}});
	}

	@Test
	public void mergeTrack_reverse_insertBoth()
	{
		NavigableMap<Instant, ConcatCommand.TrackDetail> tracks = new TreeMap<>();

		ConcatCommand.mergeTrack(tracks, createTrack(3, 4, 5));
		ConcatCommand.mergeTrack(tracks, createTrack(0, 2));

		assertEquals(tracks,
			new TreeMap<>() {{
				put(Instant.ofEpochSecond(2), ConcatCommand.TrackDetail.from(createTrack(0, 2)));
				put(Instant.ofEpochSecond(5), ConcatCommand.TrackDetail.from(createTrack(3, 4, 5)));
			}});
	}

	@Test
	public void mergeTrack_overlapBeginning_cut()
	{
		NavigableMap<Instant, ConcatCommand.TrackDetail> tracks = new TreeMap<>();

		ConcatCommand.mergeTrack(tracks, createTrack(5, 15));
		ConcatCommand.mergeTrack(tracks, createTrack(0, 4, 5, 6));

		assertEquals(tracks,
			new TreeMap<>() {{
				put(Instant.ofEpochSecond(4), ConcatCommand.TrackDetail.from(createTrack(0, 4)));
				put(Instant.ofEpochSecond(15), ConcatCommand.TrackDetail.from(createTrack(5, 15)));
			}});
	}

	@Test
	public void mergeTrack_overlapEnd_cut()
	{
		NavigableMap<Instant, ConcatCommand.TrackDetail> tracks = new TreeMap<>();

		ConcatCommand.mergeTrack(tracks, createTrack(5, 15));
		ConcatCommand.mergeTrack(tracks, createTrack(14, 15, 16, 17, 20));

		assertEquals(tracks,
			new TreeMap<>() {{
				put(Instant.ofEpochSecond(15), ConcatCommand.TrackDetail.from(createTrack(5, 15)));
				put(Instant.ofEpochSecond(20), ConcatCommand.TrackDetail.from(createTrack(16, 17, 20)));
			}});
	}

	@Test
	public void mergeTrack_inside_ignore()
	{
		NavigableMap<Instant, ConcatCommand.TrackDetail> tracks = new TreeMap<>();

		ConcatCommand.mergeTrack(tracks, createTrack(5, 15));
		ConcatCommand.mergeTrack(tracks, createTrack(6, 14));

		assertEquals(tracks,
			new TreeMap<>() {{
				put(Instant.ofEpochSecond(15), ConcatCommand.TrackDetail.from(createTrack(5, 15)));
			}});
	}

	@Test
	public void mergeTrack_overlapBoth_split()
	{
		NavigableMap<Instant, ConcatCommand.TrackDetail> tracks = new TreeMap<>();

		ConcatCommand.mergeTrack(tracks, createTrack(5, 15));
		ConcatCommand.mergeTrack(tracks, createTrack(3, 4, 5, 13, 14, 15, 16, 17));

		assertEquals(tracks,
			new TreeMap<>() {{
				put(Instant.ofEpochSecond(4), ConcatCommand.TrackDetail.from(createTrack(3, 4)));
				put(Instant.ofEpochSecond(15), ConcatCommand.TrackDetail.from(createTrack(5, 15)));
				put(Instant.ofEpochSecond(17), ConcatCommand.TrackDetail.from(createTrack(16, 17)));
			}});
	}

	private Track createTrack(long... times)
	{
		return Track.builder()
			.segments(ImmutableList.of(
				TrackSegment.builder()
					.points(LongStream.of(times)
						.mapToObj(t -> WayPoint.of(0, 0, t*1000L))
						.collect(ImmutableList.toImmutableList())
					)
					.build()
			))
			.build();
	}
}
