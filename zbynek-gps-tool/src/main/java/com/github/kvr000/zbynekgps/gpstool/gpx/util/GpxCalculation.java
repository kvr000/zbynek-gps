package com.github.kvr000.zbynekgps.gpstool.gpx.util;

import io.jenetics.jpx.Point;
import io.jenetics.jpx.WayPoint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;


/**
 * Gps calculation utilities.
 */
public class GpxCalculation
{
	/**
	 * Calculates middle point between the start and end point, based on the time.  Longitude and latitude are
	 * mandatory, altitude is optional and not calculated if for any of points is missing.
	 *
	 * This is (as of now) linear interpolation, which is good enough for small distances but can become very wrong
	 * for long distances.
	 *
	 * @param o
	 * 	start point
	 * @param n
	 * 	end point
	 * @param time
	 * 	time of calculated point
	 *
	 * @return
	 * 	calculated point at time.
	 */
	public static Point calculateMidpoint(Point o, Point n, Instant time)
	{
		long diff = o.getTime().get().until(n.getTime().get(), ChronoUnit.MILLIS);
		long current = o.getTime().get().until(time, ChronoUnit.MILLIS);
		if (diff == 0) {
			// this should not really happen, otherwise the p would already have the location
			diff = 1;
		}
		double ratio = (double) current / diff;

		double lngO = o.getLongitude().doubleValue(), lngN = n.getLongitude().doubleValue();
		double latO = o.getLatitude().doubleValue(), latN = n.getLatitude().doubleValue();

		double lngDiff = normalizeLng(lngN - lngO);

		WayPoint.Builder b = WayPoint.builder()
				.time(time)
				.lon(normalizeLng(lngO + lngDiff * ratio))
				.lat(latO + (latN - latO) * ratio);

		if (o.getElevation().isPresent() && n.getElevation().isPresent()) {
			double altO = o.getElevation().get().doubleValue(), altN = n.getElevation().get().doubleValue();
			b.ele(altO + (altN -altO) * ratio);
		}

		return b.build();
	}

	/**
	 * Normalizes the longitude, so it's in interval -180 to +180 (closed-open).
	 *
	 * @param lng
	 * 	original longitude, in range of -480 to +480 (closed-open).
	 *
	 * @return
	 * 	normalized longitude in interval -180 to +180 (closed-open).
	 */
	public static double normalizeLng(double lng)
	{
		if (lng < -180) {
			return lng + 360;
		}
		else if (lng >= 180) {
			return lng - 360;
		}
		return lng;
	}
}
