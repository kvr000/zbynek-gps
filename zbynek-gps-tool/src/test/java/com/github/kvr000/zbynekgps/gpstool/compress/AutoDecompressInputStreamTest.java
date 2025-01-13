package com.github.kvr000.zbynekgps.gpstool.compress;

import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.testng.Assert.assertEquals;


public class AutoDecompressInputStreamTest
{
	@Test
	public void read_whenNotCompressed_readDirectly() throws IOException
	{
		ByteArrayInputStream input = new ByteArrayInputStream("hello\n".getBytes(StandardCharsets.UTF_8));
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new AutoDecompressInputStream(input)))) {
			String line = reader.readLine();
			assertEquals(line, "hello");
		}
	}

	@Test
	public void read_whenCompressed_readDecompressed() throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(output)) {
			out.write("hello".getBytes(StandardCharsets.UTF_8));
		}
		ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new AutoDecompressInputStream(input)))) {
			String line = reader.readLine();
			assertEquals(line, "hello");
		}
	}
}
