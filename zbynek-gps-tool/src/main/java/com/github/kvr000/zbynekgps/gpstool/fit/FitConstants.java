package com.github.kvr000.zbynekgps.gpstool.fit;

import com.garmin.fit.Sport;
import com.google.common.collect.ImmutableMap;

import java.util.Map;


public class FitConstants
{
	private static final Map<String, String> FIT_TO_PRODUCT = ImmutableMap.<String, String>builder()
		.put("32 43", "Wahoo Elemnt Bolt V2")
		.put("1 1735", "Garmin VIRB")
		.build();

	private static final Map<Sport, String> SPORT_TO_NAME = ImmutableMap.<Sport, String>builder()
		.put(Sport.CYCLING, "Cycling")
		.build();

	public static String lookupDevice(int manufacturer, int product)
	{
		String code = manufacturer + " " + product;
		return FIT_TO_PRODUCT.get(code);
	}

	public static String lookupSport(Sport sport)
	{
		return SPORT_TO_NAME.get(sport);
	}
}
