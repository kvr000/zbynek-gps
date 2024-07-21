package com.github.kvr000.zbynekgps.gpstool.gpx.util;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.WayPoint;

import javax.inject.Singleton;
import java.util.List;


@Singleton
public class GpxFiles
{
	public GPX buildGpx(List<WayPoint> points)
	{
		return GPX.builder()
			.wayPoints(points)
			.build();
	}
}
