package com.github.kvr000.zbynekgps.gpstool.geo;


public class GeoCalc
{
	public static boolean isWithinRadius(double lon1, double lat1, double lon2, double lat2, double radiusMeters)
	{
		final double EARTH_RADIUS = 6371000; // Radius of Earth in meters
		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
			Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
				Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distance = EARTH_RADIUS * c;
		return distance <= radiusMeters;
	}

}
