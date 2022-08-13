package com.github.kvr000.zbynekgps.cmdutil.gpx;

import com.github.kvr000.zbynekgps.cmdutil.gpx.model.GpxPoint;
import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;


public class GpxMerger
{
	public static GpxPoint mergePoints(GpxPoint one, GpxPoint two)
	{
		if (one == two)
			return one;

		return GpxPoint.builder()
				.time(mergeField(one, two, GpxPoint::getTime))
				.lon(mergeField(one, two, GpxPoint::getLon))
				.lat(mergeField(one, two, GpxPoint::getLat))
				.alt(mergeField(one, two, GpxPoint::getAlt))
				.additional(mergeMaps(
						Optional.ofNullable(one).map(GpxPoint::getAdditional).orElse(null),
						Optional.ofNullable(two).map(GpxPoint::getAdditional).orElse(null)
				))
				.build();
	}

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
