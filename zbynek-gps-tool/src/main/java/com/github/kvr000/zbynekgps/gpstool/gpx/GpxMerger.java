package com.github.kvr000.zbynekgps.gpstool.gpx;

import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;


public class GpxMerger
{
//	public static WayPoint mergePoints(WayPoint one, WayPoint two)
//	{
//		if (one == two)
//			return one;
//
//		return WayPoint.builder()
//				.time(mergeField(one, two, WayPoint::getTime))
//				.lon(mergeField(one, two, WayPoint::getLon))
//				.lat(mergeField(one, two, WayPoint::getLat))
//				.alt(mergeField(one, two, WayPoint::getAlt))
//				.additional(mergeMaps(
//						Optional.ofNullable(one).map(GpxPoint::getAdditional).orElse(null),
//						Optional.ofNullable(two).map(GpxPoint::getAdditional).orElse(null)
//				))
//				.build();
//	}

	private static Map<String, String> mergeMaps(Map<String, String> one, Map<String, String> two)
	{
		if (one == two) {
			return one;
		}
		return Stream.concat(
						Optional.ofNullable(one).orElse(Collections.emptyMap()).entrySet().stream(),
						Optional.ofNullable(two).orElse(Collections.emptyMap()).entrySet().stream()
				)
				.collect(ImmutableMap.toImmutableMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(a, b) -> a
				));
	}

	private static <T, F> F mergeField(T one, T two, Function<T, F> getter)
	{
		return Optional.ofNullable(Optional.ofNullable(one).map(getter).orElse(null))
				.orElseGet(() -> Optional.ofNullable(two).map(getter).orElse(null));
	}
}
