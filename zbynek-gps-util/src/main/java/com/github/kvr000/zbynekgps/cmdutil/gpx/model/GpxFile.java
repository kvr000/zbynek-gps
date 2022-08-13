package com.github.kvr000.zbynekgps.cmdutil.gpx.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

import java.util.List;


@Value
@Builder(builderClassName = "Builder", toBuilder = true)
@JsonDeserialize(builder = GpxFile.Builder.class)
public class GpxFile
{
	private String version;

	private String creator;

	private List<GpxSegment> segments;

	@JsonPOJOBuilder(withPrefix = "")
	public static class Builder
	{
	}
}
