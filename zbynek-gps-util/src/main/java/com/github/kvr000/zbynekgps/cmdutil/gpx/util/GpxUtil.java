package com.github.kvr000.zbynekgps.cmdutil.gpx.util;

import com.google.common.collect.Range;
import com.google.common.collect.Streams;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;

import java.time.Instant;


public class GpxUtil
{
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
}
