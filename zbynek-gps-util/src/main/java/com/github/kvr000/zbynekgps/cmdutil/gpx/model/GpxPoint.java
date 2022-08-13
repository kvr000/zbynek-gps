package com.github.kvr000.zbynekgps.cmdutil.gpx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;


@Value
@Builder(builderClassName = "Builder", toBuilder = true)
@JsonDeserialize(builder = GpxPoint.Builder.class)
public class GpxPoint
{
	private Instant time;

	private BigDecimal lon;

	private BigDecimal lat;

	@JsonProperty("ele")
	private BigDecimal alt;

	@JsonDeserialize(as = ImmutableMap.class)
	private Map<String, String> additional;

	@JsonPOJOBuilder(withPrefix = "")
	public static class Builder
	{
	}
}
