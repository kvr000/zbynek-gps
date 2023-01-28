package com.github.kvr000.zbynekgps.cmdutil.util;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;


public class TreeIterators
{
	/**
	 * Iterates over two iterators and return the next lowest element.  If there is the same value in both streams,
	 * the element is returned only once.
	 *
	 * @param one
	 * 	first stream
	 * @param two
	 * 	second stream
	 *
	 * @return
	 * 	iterator over all elements in ascending order
	 *
	 * @param <T>
	 *      type of value
	 */
	public static <T extends Comparable<T>> Iterator<T> iterateLowest(Iterator<T> one, Iterator<T> two)
	{
		return new Iterator<T>()
		{
			private T pendingOne = getNextSafe(one);
			private T pendingTwo = getNextSafe(two);

			@Override
			public boolean hasNext()
			{
				return pendingOne != null || pendingTwo != null;
			}

			@Override
			public T next()
			{
				T ret;
				int cmp;
				if (pendingOne == null && pendingTwo == null) {
					throw new NoSuchElementException("Iterator has no next");
				}
				else if (pendingOne != null && (cmp = (pendingTwo == null ? -1 : pendingOne.compareTo(pendingTwo))) <= 0) {
					ret = pendingOne;
				}
				else {
					cmp = 1;
					ret = pendingTwo;
				}
				if (cmp <= 0)
					pendingOne = getNextSafe(one);
				if (cmp >= 0)
					pendingTwo = getNextSafe(two);
				return ret;
			}

		};
	}

	public static <K extends Comparable<K>, V> NavigableMap<K, V> mergeMapsPrioritized(List<NavigableMap<K, V>> maps, List<Integer> priorities)
	{
		List<NavigableMap<K, V>> prioritized = priorities.stream()
			.map(prio -> maps.get(prio))
			.collect(Collectors.toList());
		List<MutablePair<Map.Entry<K, V>, Iterator<Map.Entry<K, V>>>> currents = prioritized.stream()
			.map(map -> map.entrySet().iterator())
			.filter(Iterator::hasNext)
			.map(i -> MutablePair.of(i.next(), i))
			.collect(Collectors.toCollection(ArrayList::new));

		TreeMap<K, V> result = new TreeMap<>();
		for (;;) {
			Map.Entry<K, V> lowest = currents.stream()
				.sorted(Comparator.comparing(e -> e.getLeft().getKey()))
				.findFirst()
				.map(p -> p.getLeft())
				.orElse(null);
			if (lowest == null) {
				break;
			}
			result.put(lowest.getKey(), lowest.getValue());
			for (Iterator<MutablePair<Map.Entry<K, V>, Iterator<Map.Entry<K, V>>>> it = currents.iterator(); it.hasNext(); ) {
				MutablePair<Map.Entry<K, V>, Iterator<Map.Entry<K, V>>> t = it.next();
				if (t.getLeft().getKey().equals(lowest.getKey())) {
					if (t.getRight().hasNext()) {
						t.setLeft(t.getRight().next());
					}
					else {
						it.remove();
					}
				}
			}
		}
		return result;
	}

	/**
	 * Iterates over items from first array and enriches with sorted sequence from the second navigable map.  The
	 * result starts from the first item from the first stream and ends with the last item from the first stream,
	 * other items from alternative stream are ignored.
	 *
	 * @param one
	 * 	main stream
	 * @param two
	 * 	alternative stream
	 *
	 * @return
	 * 	original stream enriched with alternative stream if items were missing.
	 *
	 * @param <K>
	 *      type of key
	 * @param <V>
	 *      type of value
	 */
	public static <K extends Comparable<K>, V> Iterator<Map.Entry<K, V>> iterateEntryEnriched(
			Iterator<? extends Map.Entry<K, V>> one, NavigableMap<K, V> two)
	{
		return new Iterator<>()
		{
			private Map.Entry<K, V> pendingOne = getNextSafe(one);

			private Iterator<Map.Entry<K, V>> iteratorTwo = pendingOne == null ? null :
				two.tailMap(pendingOne.getKey()).entrySet().iterator();
			private Map.Entry<K, V> pendingTwo = iteratorTwo == null ? null : getNextSafe(iteratorTwo);

			@Override
			public boolean hasNext()
			{
				return pendingOne != null;
			}

			@Override
			public Map.Entry<K, V> next()
			{
				Map.Entry<K, V> ret;
				int cmp;
				if (pendingOne == null) {
					throw new NoSuchElementException("Iterator has no next");
				}
				else if ((cmp = (pendingTwo == null ? -1 : pendingOne.getKey().compareTo(pendingTwo.getKey()))) <= 0) {
					ret = pendingOne;
				}
				else {
					ret = pendingTwo;
				}
				if (cmp <= 0)
					pendingOne = getNextSafe(one);
				if (cmp >= 0)
					pendingTwo = getNextSafe(iteratorTwo);
				return ret;

			}
		};
	}

	private static <T> T getNextSafe(Iterator<T> it)
	{
		return it.hasNext() ?
			Objects.requireNonNull(it.next(), "iterator must not return null") :
			null;
	}
}
