package com.github.kvr000.zbynekgps.cmdutil.gpx.util;

import com.github.kvr000.zbynekgps.cmdutil.gpx.model.GpxPoint;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.testng.Assert.assertEquals;


public class GpxCalculationTest
{
	@Test
	public void normalizeLng_normal_untouched()
	{
		double result = GpxCalculation.normalizeLng(0);

		assertEquals(result, 0.0);
	}

	@Test
	public void normalizeLng_high_decreased()
	{
		double result = GpxCalculation.normalizeLng(180);

		assertEquals(result, -180);
	}

	@Test
	public void normalizeLng_low_increased()
	{
		double result = GpxCalculation.normalizeLng(-190);

		assertEquals(result, 170);
	}

	@Test
	public void calculateMidPoint_positive_middle()
	{
		GpxPoint result = GpxCalculation.calculateMidpoint(
			GpxPoint.builder()
				.time(Instant.ofEpochSecond(0))
				.lon(BigDecimal.valueOf(10))
				.lat(BigDecimal.valueOf(60))
				.alt(BigDecimal.valueOf(1000))
				.build(),
			GpxPoint.builder()
				.time(Instant.ofEpochSecond(8))
				.lon(BigDecimal.valueOf(30))
				.lat(BigDecimal.valueOf(72))
				.alt(BigDecimal.valueOf(2000))
				.build(),
			Instant.ofEpochSecond(2)
		);

		assertEquals(result.getTime(), Instant.ofEpochSecond(2));
		assertEquals(result.getLon(), BigDecimal.valueOf(15.0));
		assertEquals(result.getLat(), BigDecimal.valueOf(63.0));
		assertEquals(result.getAlt(), BigDecimal.valueOf(1250.0));
	}

	@Test
	public void calculateMidPoint_negative_middle()
	{
		GpxPoint result = GpxCalculation.calculateMidpoint(
			GpxPoint.builder()
				.time(Instant.ofEpochSecond(0))
				.lon(BigDecimal.valueOf(30))
				.lat(BigDecimal.valueOf(72))
				.alt(BigDecimal.valueOf(2000))
				.build(),
			GpxPoint.builder()
				.time(Instant.ofEpochSecond(8))
				.lon(BigDecimal.valueOf(10))
				.lat(BigDecimal.valueOf(60))
				.alt(BigDecimal.valueOf(1000))
				.build(),
			Instant.ofEpochSecond(2)
		);

		assertEquals(result.getTime(), Instant.ofEpochSecond(2));
		assertEquals(result.getLon(), BigDecimal.valueOf(25.0));
		assertEquals(result.getLat(), BigDecimal.valueOf(69.0));
		assertEquals(result.getAlt(), BigDecimal.valueOf(1750.0));
	}

	@Test
	public void calculateMidPoint_pacific_middle()
	{
		GpxPoint result = GpxCalculation.calculateMidpoint(
			GpxPoint.builder()
				.time(Instant.ofEpochSecond(0))
				.lon(BigDecimal.valueOf(170))
				.lat(BigDecimal.valueOf(60))
				.alt(BigDecimal.valueOf(1000))
				.build(),
			GpxPoint.builder()
				.time(Instant.ofEpochSecond(8))
				.lon(BigDecimal.valueOf(-170))
				.lat(BigDecimal.valueOf(72))
				.alt(BigDecimal.valueOf(2000))
				.build(),
			Instant.ofEpochSecond(2)
		);

		assertEquals(result.getTime(), Instant.ofEpochSecond(2));
		assertEquals(result.getLon(), BigDecimal.valueOf(175.0));
		assertEquals(result.getLat(), BigDecimal.valueOf(63.0));
		assertEquals(result.getAlt(), BigDecimal.valueOf(1250.0));
	}
}
