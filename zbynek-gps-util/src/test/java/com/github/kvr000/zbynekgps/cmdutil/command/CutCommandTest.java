package com.github.kvr000.zbynekgps.cmdutil.command;

import com.google.common.collect.ImmutableList;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.stream.LongStream;

import static org.testng.Assert.assertEquals;


public class CutCommandTest
{
	@Test
	public void cutTrack_outside_ignore()
	{
		Track result = CutCommand.cutTrack(
			createTrack(2, 3, 4),
			new CutCommand.Options("", Instant.ofEpochSecond(0), Instant.ofEpochSecond(1))
		);

		assertEquals(
			result,
			createTrack(2, 3, 4)
		);
	}

	@Test
	public void cutTrack_left_remainRight()
	{
		Track result = CutCommand.cutTrack(
			createTrack(0, 1, 2, 3, 4),
			new CutCommand.Options("", Instant.ofEpochSecond(0), Instant.ofEpochSecond(1))
		);

		assertEquals(
			result,
			createTrack(2, 3, 4)
		);
	}

	@Test
	public void cutTrack_right_remainLeft()
	{
		Track result = CutCommand.cutTrack(
			createTrack(0, 1, 2, 3, 4),
			new CutCommand.Options("", Instant.ofEpochSecond(3), Instant.ofEpochSecond(4))
		);

		assertEquals(
			result,
			createTrack(0, 1, 2)
		);
	}

	@Test
	public void cutTrack_cover_remove()
	{
		Track result = CutCommand.cutTrack(
			createTrack(0, 1, 2, 3, 4),
			new CutCommand.Options("", Instant.ofEpochSecond(0), Instant.ofEpochSecond(4))
		);

		assertEquals(
			result,
			Track.builder()
				.build()
		);
	}

	@Test
	public void cutTrack_inside_split()
	{
		Track result = CutCommand.cutTrack(
			createTrack(0, 1, 2, 3, 4),
			new CutCommand.Options("", Instant.ofEpochSecond(2), Instant.ofEpochSecond(3))
		);

		assertEquals(
			result,
			Track.builder()
				.segments(ImmutableList.of(
					createSegment(0, 1),
					createSegment(4)
				))
				.build()
		);
	}

	private static TrackSegment createSegment(long... times)
	{
		return
			TrackSegment.builder()
				.points(LongStream.of(times)
						.mapToObj(t -> WayPoint.of(0, 0, t*1000L))
						.collect(ImmutableList.toImmutableList())
				)
				.build();
	}

	private static Track createTrack(long... times)
	{
		return Track.builder()
			.segments(ImmutableList.of(
				createSegment(times)
			))
			.build();
	}
}
