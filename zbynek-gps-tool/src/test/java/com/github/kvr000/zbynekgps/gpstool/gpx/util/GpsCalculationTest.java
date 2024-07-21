package com.github.kvr000.zbynekgps.gpstool.gpx.util;

import io.jenetics.jpx.Latitude;
import io.jenetics.jpx.Length;
import io.jenetics.jpx.Longitude;
import io.jenetics.jpx.Point;
import io.jenetics.jpx.WayPoint;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.Optional;

import static org.testng.Assert.assertEquals;


public class GpsCalculationTest
{
	@Test
	public void normalizeLng_normal_untouched()
	{
		double result = GpsCalculation.normalizeLng(0);

		assertEquals(result, 0.0);
	}

	@Test
	public void normalizeLng_high_decreased()
	{
		double result = GpsCalculation.normalizeLng(180);

		assertEquals(result, -180);
	}

	@Test
	public void normalizeLng_low_increased()
	{
		double result = GpsCalculation.normalizeLng(-190);

		assertEquals(result, 170);
	}

	@Test
	public void calculateMidPoint_positive_middle()
	{
		Point result = GpsCalculation.calculateMidpoint(
			WayPoint.builder()
				.time(Instant.ofEpochSecond(0))
				.lon(10)
				.lat(60)
				.ele(1000)
				.build(),
			WayPoint.builder()
				.time(Instant.ofEpochSecond(8))
				.lon(30)
				.lat(72)
				.ele(2000)
				.build(),
			Instant.ofEpochSecond(2)
		);

		assertEquals(result.getTime(), Optional.of(Instant.ofEpochSecond(2)));
		assertEquals(result.getLongitude(), Longitude.ofDegrees(15.0));
		assertEquals(result.getLatitude(), Latitude.ofDegrees(63.0));
		assertEquals(result.getElevation(), Optional.of(Length.of(1250.0, Length.Unit.METER)));
	}

	@Test
	public void calculateMidPoint_negative_middle()
	{
		Point result = GpsCalculation.calculateMidpoint(
			WayPoint.builder()
				.time(Instant.ofEpochSecond(0))
				.lon(30)
				.lat(72)
				.ele(2000)
				.build(),
			WayPoint.builder()
				.time(Instant.ofEpochSecond(8))
				.lon(10)
				.lat(60)
				.ele(1000)
				.build(),
			Instant.ofEpochSecond(2)
		);

		assertEquals(result.getTime(), Optional.of(Instant.ofEpochSecond(2)));
		assertEquals(result.getLongitude(), Longitude.ofDegrees(25));
		assertEquals(result.getLatitude(), Latitude.ofDegrees(69));
		assertEquals(result.getElevation(), Optional.of(Length.of(1750, Length.Unit.METER)));
	}

	@Test
	public void calculateMidPoint_pacific_middle()
	{
		Point result = GpsCalculation.calculateMidpoint(
			WayPoint.builder()
				.time(Instant.ofEpochSecond(0))
				.lon(170)
				.lat(60)
				.ele(1000)
				.build(),
			WayPoint.builder()
				.time(Instant.ofEpochSecond(8))
				.lon(-170)
				.lat(72)
				.ele(2000)
				.build(),
			Instant.ofEpochSecond(2)
		);

		assertEquals(result.getTime(), Optional.of(Instant.ofEpochSecond(2)));
		assertEquals(result.getLongitude(), Longitude.ofDegrees(175));
		assertEquals(result.getLatitude(), Latitude.ofDegrees(63));
		assertEquals(result.getElevation(), Optional.of(Length.of(1250, Length.Unit.METER)));
	}
}
