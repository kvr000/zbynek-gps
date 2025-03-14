package com.github.kvr000.zbynekgps.gpstool.gpx.util;

import com.google.common.collect.Range;
import com.google.common.collect.Streams;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;

import java.time.Instant;
import java.util.List;


public class GpxUtil
{
	/**
	 * Finds boundaries for {@link TrackSegment}, ignoring any points without time.
	 *
	 * @param segment
	 * 	track segment to explore
	 *
	 * @return
	 * 	a pair of start and end time for the segment.
	 */
	public static Range<Instant> findBoundaries(TrackSegment segment)
	{
		Instant start = segment.points()
			.filter(p -> p.getTime().isPresent())
			.findFirst()
			.flatMap(WayPoint::getTime)
			.orElse(null);
		if (start == null)
			return null;
		Instant end = Streams.findLast(segment.points()
				.filter(p -> p.getTime().isPresent())
			)
			.flatMap(WayPoint::getTime)
			.get();
		return Range.closed(start, end);
	}

	public static GPX buildGpx(List<WayPoint> points)
	{
		return GPX.builder()
			.wayPoints(points)
			.build();
	}

	public static List<WayPoint> expandToWaypoints(GPX gpx)
	{
		return gpx.tracks()
			.flatMap(Track::segments)
			.flatMap(TrackSegment::points)
			.toList();
	}

	public static List<WayPoint> expandToTimedWaypoints(GPX gpx)
	{
		return gpx.tracks()
			.flatMap(Track::segments)
			.flatMap(TrackSegment::points)
			.filter(point -> point.getTime().isPresent())
			.toList();
	}
}
