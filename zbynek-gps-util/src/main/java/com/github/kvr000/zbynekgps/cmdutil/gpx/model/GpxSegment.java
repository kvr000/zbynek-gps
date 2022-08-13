package com.github.kvr000.zbynekgps.cmdutil.gpx.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

import java.util.List;


@Value
@Builder(builderClassName = "Builder", toBuilder = true)
@JsonDeserialize(builder = GpxSegment.Builder.class)
public class GpxSegment
{
	private List<GpxPoint> points;

	@JsonPOJOBuilder(withPrefix = "")
	public static class Builder
	{
	}
}
