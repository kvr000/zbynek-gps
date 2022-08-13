package com.github.kvr000.zbynekgps.cmdutil.gpx.util;

import com.github.kvr000.zbynekgps.cmdutil.gpx.model.GpxPoint;

import java.math.BigDecimal;
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
	public static GpxPoint calculateMidpoint(GpxPoint o, GpxPoint n, Instant time)
	{
		long diff = o.getTime().until(n.getTime(), ChronoUnit.MILLIS);
		long current = o.getTime().until(time, ChronoUnit.MILLIS);
		if (diff == 0) {
			// this should not really happen, otherwise the p would already have the location
			diff = 1;
		}
		double ratio = (double) current / diff;

		double lngO = o.getLon().doubleValue(), lngN = n.getLon().doubleValue();
		double latO = o.getLat().doubleValue(), latN = n.getLat().doubleValue();

		double lngDiff = normalizeLng(lngN - lngO);

		GpxPoint.Builder b = GpxPoint.builder()
				.time(time)
				.lon(BigDecimal.valueOf(normalizeLng(lngO + lngDiff * ratio)))
				.lat(BigDecimal.valueOf(latO + (latN - latO) * ratio));

		if (o.getAlt() != null && n.getAlt() != null) {
			double altO = o.getAlt().doubleValue(), altN = n.getAlt().doubleValue();
			b.alt(BigDecimal.valueOf(altO+(altN-altO)*ratio));
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
