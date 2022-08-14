package com.github.kvr000.zbynekgps.cmdutil.command;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.testng.Assert.assertEquals;


public class RetrackCommandTest
{
//	@Test
//	public void mergeFiles_outside_untouched()
//	{
//		GPX one = GPX.builder()
//			.tracks(ImmutableList.of(
//				Track.builder()
//					.segments(ImmutableList.of(
//						TrackSegment.builder()
//							.points(ImmutableList.of(
//								WayPoint.builder()
//									.time(Instant.ofEpochSecond(10))
//									.lon(50)
//									.lat(50)
//									.build(),
//								WayPoint.builder()
//									.time(Instant.ofEpochSecond(20))
//									.lon(60)
//									.lat(60)
//									.build()
//							))
//							.build()
//					))
//					.build()
//			))
//			.build();
//		GPX two = GPX.builder()
//			.tracks(ImmutableList.of(
//				Track.builder()
//					.segments(ImmutableList.of(
//						TrackSegment.builder()
//							.points(ImmutableList.of(
//								WayPoint.builder()
//									.time(Instant.ofEpochSecond(5))
//									.lon(50)
//									.lat(50)
//									.build(),
//								WayPoint.builder()
//									.time(Instant.ofEpochSecond(25))
//									.lon(60)
//									.lat(60)
//									.build()
//							))
//							.build()
//					))
//					.build()
//			))
//			.build();
//
//		GPX result = RetrackCommand.mergeFiles(one, two);
//
//		assertEquals(result, one);
//	}
//
//	@Test
//	public void mergeFiles_inside_adding()
//	{
//		GpxFile one = GpxFile.builder()
//			.segments(ImmutableList.of(
//				GpxSegment.builder()
//					.points(ImmutableList.of(
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(10))
//							.lon(BigDecimal.valueOf(50))
//							.lat(BigDecimal.valueOf(50))
//							.build(),
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(20))
//							.lon(BigDecimal.valueOf(60))
//							.lat(BigDecimal.valueOf(60))
//							.build()
//					))
//					.build()
//			))
//			.build();
//		GpxFile two = GpxFile.builder()
//			.segments(ImmutableList.of(
//				GpxSegment.builder()
//					.points(ImmutableList.of(
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(12))
//							.lon(BigDecimal.valueOf(50))
//							.lat(BigDecimal.valueOf(50))
//							.build(),
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(18))
//							.lon(BigDecimal.valueOf(60))
//							.lat(BigDecimal.valueOf(60))
//							.build()
//					))
//					.build()
//			))
//			.build();
//
//		GpxFile expected = GpxFile.builder()
//			.segments(ImmutableList.of(
//				GpxSegment.builder()
//					.points(ImmutableList.of(
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(10))
//							.lon(BigDecimal.valueOf(50))
//							.lat(BigDecimal.valueOf(50))
//							.build(),
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(12))
//							.lon(BigDecimal.valueOf(50))
//							.lat(BigDecimal.valueOf(50))
//							.build(),
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(18))
//							.lon(BigDecimal.valueOf(60))
//							.lat(BigDecimal.valueOf(60))
//							.build(),
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(20))
//							.lon(BigDecimal.valueOf(60))
//							.lat(BigDecimal.valueOf(60))
//							.build()
//					))
//					.build()
//			))
//			.build();
//
//		GpxFile result = RetrackCommand.mergeFiles(one, two);
//
//		assertEquals(result, expected);
//	}
//
//
//	@Test
//	public void mergeFiles_duplicate_enriches()
//	{
//		GpxFile one = GpxFile.builder()
//			.segments(ImmutableList.of(
//				GpxSegment.builder()
//					.points(ImmutableList.of(
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(10))
//							.lon(BigDecimal.valueOf(50))
//							.lat(BigDecimal.valueOf(50))
//							.additional(ImmutableMap.of(
//								"first", "o1",
//								"dup", "o5"
//							))
//							.build(),
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(20))
//							.build()
//					))
//					.build()
//			))
//			.build();
//		GpxFile two = GpxFile.builder()
//			.segments(ImmutableList.of(
//				GpxSegment.builder()
//					.points(ImmutableList.of(
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(10))
//							.lon(BigDecimal.valueOf(50))
//							.lat(BigDecimal.valueOf(50))
//							.additional(ImmutableMap.of(
//								"second", "n2",
//								"dup", "n5"
//							))
//							.build(),
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(20))
//							.lon(BigDecimal.valueOf(60))
//							.lat(BigDecimal.valueOf(60))
//							.build()
//					))
//					.build()
//			))
//			.build();
//
//		GpxFile expected = GpxFile.builder()
//			.segments(ImmutableList.of(
//				GpxSegment.builder()
//					.points(ImmutableList.of(
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(10))
//							.lon(BigDecimal.valueOf(50))
//							.lat(BigDecimal.valueOf(50))
//							.additional(ImmutableMap.of(
//								"first", "o1",
//								"dup", "o5",
//								"second", "n2"
//							))
//							.build(),
//						GpxPoint.builder()
//							.time(Instant.ofEpochSecond(20))
//							.lon(BigDecimal.valueOf(60))
//							.lat(BigDecimal.valueOf(60))
//							.build()
//					))
//					.build()
//			))
//			.build();
//
//		GpxFile result = RetrackCommand.mergeFiles(one, two);
//
//		assertEquals(result, expected);
//	}
}
