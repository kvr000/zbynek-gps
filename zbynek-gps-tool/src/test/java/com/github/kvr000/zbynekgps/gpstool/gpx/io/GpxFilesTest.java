package com.github.kvr000.zbynekgps.gpstool.gpx.io;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Namespace;
import org.dom4j.io.SAXReader;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


public class GpxFilesTest
{
	@Test
	public void hoistRepeatedNamespaces_repeatedPerPoint_hoistedOnceAndSuppressedElsewhere() throws DocumentException
	{
		Document document = parse(
			"<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">" +
				"<trk><trkseg>" +
				"<trkpt lat=\"1.0\" lon=\"2.0\"><extensions>" +
				"<gpxtpx:TrackPointExtension xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">" +
				"<gpxtpx:hr>80</gpxtpx:hr>" +
				"</gpxtpx:TrackPointExtension>" +
				"</extensions></trkpt>" +
				"<trkpt lat=\"1.1\" lon=\"2.1\"><extensions>" +
				"<gpxtpx:TrackPointExtension xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">" +
				"<gpxtpx:hr>81</gpxtpx:hr>" +
				"</gpxtpx:TrackPointExtension>" +
				"</extensions></trkpt>" +
				"</trkseg></trk>" +
				"</gpx>"
		);

		GpxFiles.hoistRepeatedNamespaces(document.getRootElement());
		String result = document.asXML();

		assertEquals(countOccurrences(result, "xmlns:gpxtpx"), 1);
		assertTrue(result.indexOf("xmlns:gpxtpx") < result.indexOf("<trkpt"), "namespace must be declared before the first trkpt");
		assertTrue(result.contains("<gpxtpx:hr>80</gpxtpx:hr>"));
		assertTrue(result.contains("<gpxtpx:hr>81</gpxtpx:hr>"));
	}

	@Test
	public void hoistRepeatedNamespaces_noDuplicates_rootUnchanged() throws DocumentException
	{
		Document document = parse("<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk/></gpx>");

		GpxFiles.hoistRepeatedNamespaces(document.getRootElement());

		assertEquals(1, document.getRootElement().declaredNamespaces().size());
	}

	@Test
	public void hoistRepeatedNamespaces_singleOccurrence_stillHoistedToRoot() throws DocumentException
	{
		Document document = parse(
			"<gpx version=\"1.1\"><trkpt><extensions>" +
				"<gpxtpx:TrackPointExtension xmlns:gpxtpx=\"URI\"><gpxtpx:hr>5</gpxtpx:hr></gpxtpx:TrackPointExtension>" +
				"</extensions></trkpt></gpx>"
		);

		GpxFiles.hoistRepeatedNamespaces(document.getRootElement());

		List<Namespace> rootNamespaces = document.getRootElement().declaredNamespaces();
		assertEquals(rootNamespaces.size(), 1);
		assertEquals(rootNamespaces.get(0).getPrefix(), "gpxtpx");
		assertEquals(rootNamespaces.get(0).getURI(), "URI");

		String result = document.asXML();
		assertEquals(countOccurrences(result, "xmlns:gpxtpx"), 1);
		assertTrue(result.contains("<gpxtpx:hr>5</gpxtpx:hr>"));
	}

	@Test
	public void hoistRepeatedNamespaces_samePrefixDifferentUri_conflictingOnesLeftInPlace() throws DocumentException
	{
		Document document = parse(
			"<gpx version=\"1.1\">" +
				"<a><b xmlns:ext=\"URI_A\"><c/></b></a>" +
				"<d><e xmlns:ext=\"URI_B\"><f/></e></d>" +
				"</gpx>"
		);

		GpxFiles.hoistRepeatedNamespaces(document.getRootElement());

		assertTrue(document.getRootElement().declaredNamespaces().isEmpty(), "conflicting prefix must not be hoisted to root");

		String result = document.asXML();
		assertTrue(result.contains("xmlns:ext=\"URI_A\""));
		assertTrue(result.contains("xmlns:ext=\"URI_B\""));
	}

	@Test
	public void hoistRepeatedNamespaces_multipleDistinctPrefixes_bothHoisted() throws DocumentException
	{
		Document document = parse(
			"<gpx version=\"1.1\">" +
				"<trkpt><extensions><a:x xmlns:a=\"NSA\"><b:y xmlns:b=\"NSB\">1</b:y></a:x></extensions></trkpt>" +
				"<trkpt><extensions><a:x xmlns:a=\"NSA\"><b:y xmlns:b=\"NSB\">2</b:y></a:x></extensions></trkpt>" +
				"</gpx>"
		);

		GpxFiles.hoistRepeatedNamespaces(document.getRootElement());
		String result = document.asXML();

		assertEquals(countOccurrences(result, "xmlns:a="), 1);
		assertEquals(countOccurrences(result, "xmlns:b="), 1);
		assertTrue(result.indexOf("xmlns:a=") < result.indexOf("<trkpt"));
		assertTrue(result.indexOf("xmlns:b=") < result.indexOf("<trkpt"));
		assertTrue(result.contains(">1<"));
		assertTrue(result.contains(">2<"));
	}

	@Test
	public void writeGpx_endToEnd_cleansUpTempFileAndProducesValidXml() throws IOException
	{
		GpxFiles gpxFiles = new GpxFiles();
		Path output = Files.createTempFile("GpxFilesTest", ".gpx");
		Files.deleteIfExists(output);
		try {
			gpxFiles.writeGpx(output, io.jenetics.jpx.GPX.builder().build());

			assertTrue(Files.exists(output));
			String content = Files.readString(output, StandardCharsets.UTF_8);
			assertTrue(content.startsWith("<?xml"));
			assertTrue(content.contains("<gpx"));

			long leftoverTempFiles = Files.list(output.getParent())
				.filter(p -> p.getFileName().toString().startsWith(output.getFileName().toString()) && !p.equals(output))
				.count();
			assertEquals(leftoverTempFiles, 0);
		}
		finally {
			Files.deleteIfExists(output);
		}
	}

	private static Document parse(String xml) throws DocumentException
	{
		return new SAXReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	private static int countOccurrences(String haystack, String needle)
	{
		return haystack.split(Pattern.quote(needle), -1).length - 1;
	}
}
