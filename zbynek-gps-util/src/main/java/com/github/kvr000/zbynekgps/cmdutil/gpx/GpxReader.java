package com.github.kvr000.zbynekgps.cmdutil.gpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kvr000.zbynekgps.cmdutil.gpx.model.GpxFile;

import java.io.IOException;
import java.io.InputStream;


public class GpxReader
{
	private static ObjectMapper mapper = new ObjectMapper();

	public static GpxFile readGpx(InputStream file) throws IOException
	{
		GpxFile gpx = mapper.readValue(file, GpxFile.class);
		return gpx;
	}
}
