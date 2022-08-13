package com.github.kvr000.zbynekgps.cmdutil.util;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Objects;


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

	/**
	 * Iterates over items from first array and enriches by sorted sequence from the second navigable map.  The
	 * result starts from the first item from the first stream and ends with the last item from the first stream,
	 * other items from alternative stream are ignored.
	 *
	 * @param one
	 * 	main stream
	 * @param two
	 * 	alternative stream
	 *
	 * @return
	 * 	original stream enriched by alternative stream if items were missing.
	 *
	 * @param <K>
	 *      type of key
	 * @param <V>
	 *      type of value
	 */
	public static <K extends Comparable<K>, V> Iterator<Map.Entry<K, V>> iterateEnriched(
			Iterator<? extends Map.Entry<K, V>> one, NavigableMap<K, V> two)
	{
		return new Iterator<>()
		{
			private K lastKey = null;
			private Map.Entry<K, V> pendingOne = getNextSafe(one);

			@Override
			public boolean hasNext()
			{
				return pendingOne != null;
			}

			@Override
			public Map.Entry<K, V> next()
			{
				Map.Entry<K, V> pendingTwo;
				if (pendingOne == null) {
					throw new NoSuchElementException("Iterator has no next");
				}
				if (lastKey != null && (pendingTwo = two.higherEntry(lastKey)) != null && pendingTwo.getKey().compareTo(pendingOne.getKey()) < 0) {
					lastKey = pendingTwo.getKey();
					return pendingTwo;
				}
				lastKey = pendingOne.getKey();
				Map.Entry<K, V> ret = pendingOne;
				pendingOne = getNextSafe(one);
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
