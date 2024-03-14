package com.github.kvr000.zbynekgps.gpstool.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import org.testng.annotations.Test;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.testng.Assert.assertEquals;


public class TreeIteratorsTest
{
	@Test
	public void iterateEnriched_empty_empty()
	{
		Map<Integer, Integer> list = ImmutableMap.of();
		NavigableMap<Integer, Integer> added = new TreeMap<>(ImmutableMap.of(
			0, -10,
			10, -20
		));

		List<Map.Entry<Integer, Integer>> result =
			Streams.stream(TreeIterators.iterateEntryEnriched(list.entrySet().iterator(), added))
			.collect(ImmutableList.toImmutableList());

		assertEquals(
			result,
			ImmutableList.of(
			)
		);
	}

	@Test
	public void iterateEnriched_outsideElements_ignore()
	{
		Map<Integer, Integer> list = ImmutableMap.of(1, -11, 9, -19);
		NavigableMap<Integer, Integer> added = new TreeMap<>(ImmutableMap.of(
			0, -10,
			10, -20
		));

		List<Map.Entry<Integer, Integer>> result =
			Streams.stream(TreeIterators.iterateEntryEnriched(list.entrySet().iterator(), added))
			.collect(ImmutableList.toImmutableList());

		assertEquals(
			result,
			ImmutableList.of(
				new AbstractMap.SimpleImmutableEntry<>(1, -11),
				new AbstractMap.SimpleImmutableEntry<>(9, -19)
			)
		);
	}

	@Test
	public void iterateEnriched_insideElements_include()
	{
		Map<Integer, Integer> list = ImmutableMap.of(1, -11, 9, -19);
		NavigableMap<Integer, Integer> added = new TreeMap<>(ImmutableMap.of(
			2, -12,
			5, -15
		));

		List<Map.Entry<Integer, Integer>> result =
			Streams.stream(TreeIterators.iterateEntryEnriched(list.entrySet().iterator(), added))
			.collect(ImmutableList.toImmutableList());

		assertEquals(
			result,
			ImmutableList.of(
				new AbstractMap.SimpleImmutableEntry<>(1, -11),
				new AbstractMap.SimpleImmutableEntry<>(2, -12),
				new AbstractMap.SimpleImmutableEntry<>(5, -15),
				new AbstractMap.SimpleImmutableEntry<>(9, -19)
			)
		);
	}

	@Test
	public void iterateEnriched_duplicate_ignore()
	{
		Map<Integer, Integer> list = ImmutableMap.of(1, -11, 9, -19);
		NavigableMap<Integer, Integer> added = new TreeMap<>(ImmutableMap.of(
			1, -10,
			2, -12,
			10, -20
		));

		List<Map.Entry<Integer, Integer>> result =
			Streams.stream(TreeIterators.iterateEntryEnriched(list.entrySet().iterator(), added))
			.collect(ImmutableList.toImmutableList());

		assertEquals(
			result,
			ImmutableList.of(
				new AbstractMap.SimpleImmutableEntry<>(1, -11),
				new AbstractMap.SimpleImmutableEntry<>(2, -12),
				new AbstractMap.SimpleImmutableEntry<>(9, -19)
			)
		);
	}

}
