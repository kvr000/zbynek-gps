package com.github.kvr000.zbynekgps.gpstool.command;

import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.fit.io.FitFiles;
import com.github.kvr000.zbynekgps.gpstool.gpx.io.GpxFiles;
import com.github.kvr000.zbynekgps.gpstool.gpxlike.io.GpxLikeFiles;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.testng.Assert.assertEquals;


public class RetrackCommandTest
{
	RetrackCommand retrackCommand = new RetrackCommand(new GpxLikeFiles(new GpxFiles(), new FitFiles()), new ZbynekGpsTool.Options());

	@BeforeMethod
	public void setup()
	{
		retrackCommand.createOptions(null);
	}

	public void finalizeSetup()
	{
		if (retrackCommand.options.inputs == null) {
			retrackCommand.options.inputs = List.of("");
		}
		if (retrackCommand.options.positionPriority == null) {
			retrackCommand.options.positionPriority = IntStream.range(0, retrackCommand.options.inputs.size()).boxed().collect(Collectors.toList());
		}
		if (retrackCommand.options.elevationPriority == null) {
			retrackCommand.options.elevationPriority = IntStream.range(0, retrackCommand.options.inputs.size()).boxed().collect(Collectors.toList());
		}
	}

	@Test
	public void enrich_missing_recalculated()
	{
		finalizeSetup();

		GPX main = createGpx(
			createWaypoint(0, 0, 0, 0),
			createWaypoint(1, 0, 0, 0),
			createWaypoint(2, 2, 4, 8)
		);

		GPX expected = createGpx(
			createWaypoint(0, 0, 0, 0),
			createWaypoint(1, 1, 2, 4),
			createWaypoint(2, 2, 4, 8)
		);

		GPX result = retrackCommand.enrichLocations(main, retrackCommand.buildPointSources(retrackCommand.buildPointData(List.of(main))));

		assertEquals(result, expected);
	}

	@Test
	public void enrich_alternative_enriched()
	{
		retrackCommand.options.inputs = List.of("", "");
		finalizeSetup();

		GPX main = createGpx(
			createWaypoint(0, 0, 0, 0),
			createWaypoint(1, 0, 0, 0),
			createWaypoint(2, 2, 4, 8)
		);
		GPX secondary = createGpx(
			createWaypoint(1, 3, 6, 7)
		);

		GPX expected = createGpx(
			createWaypoint(0, 0, 0, 0),
			createWaypoint(1, 3, 6, 7),
			createWaypoint(2, 2, 4, 8)
		);

		GPX result = retrackCommand.enrichLocations(main, retrackCommand.buildPointSources(retrackCommand.buildPointData(List.of(main, secondary))));

		assertEquals(result, expected);
	}

	@Test
	public void enrich_alternativeInvalid_calculated()
	{
		retrackCommand.options.inputs = List.of("", "");
		finalizeSetup();

		GPX main = createGpx(
			createWaypoint(0, 0, 0, 0),
			createWaypoint(1, 0, 0, 0),
			createWaypoint(2, 0, 0, 0),
			createWaypoint(3, 5, 8, 9)
		);
		GPX secondary = createGpx(
			createWaypoint(1, 3, 6, 7),
			createWaypoint(2, 3, 6, 7)
		);

		GPX expected = createGpx(
			createWaypoint(0, 0, 0, 0),
			createWaypoint(1, 3, 6, 7),
			createWaypoint(2, 4, 7, 8),
			createWaypoint(3, 5, 8, 9)
		);

		GPX result = retrackCommand.enrichLocations(main, retrackCommand.buildPointSources(retrackCommand.buildPointData(List.of(main, secondary))));

		assertEquals(result, expected);
	}

	private GPX createGpx(WayPoint... waypoints)
	{
		return GPX.builder()
			.addTrack(Track.builder()
				.segments(List.of(TrackSegment.builder()
					.points(Arrays.asList(waypoints))
					.build()
				))
				.build()
			)
			.build();
	}

	private WayPoint createWaypoint(long time, double lon, double lat, double ele)
	{
		return WayPoint.builder()
			.time(time)
			.lon(lon)
			.lat(lat)
			.ele(ele)
			.build();
	}
}
