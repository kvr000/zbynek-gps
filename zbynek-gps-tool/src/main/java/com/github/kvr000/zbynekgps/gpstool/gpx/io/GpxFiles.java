package com.github.kvr000.zbynekgps.gpstool.gpx.io;

import com.github.kvr000.zbynekgps.gpstool.compress.AutoDecompressInputStream;
import io.jenetics.jpx.GPX;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


@Singleton
public class GpxFiles
{
	public static final GPX.Writer GPX_WRITER = GPX.Writer.of(GPX.Writer.Indent.TAB1);

	public GPX readGpx(InputStream input) throws IOException
	{
		return GPX.Reader.DEFAULT.read(input);
	}

	public GPX readGpxDecompressed(InputStream input) throws IOException
	{
		return GPX.Reader.DEFAULT.read(new AutoDecompressInputStream(input));
	}

	public GPX readGpxDecompressed(Path input) throws IOException
	{
		try (InputStream stream = Files.newInputStream(input)) {
			return readGpxDecompressed(stream);
		}
	}

	/**
	 * Writes the given GPX object to {@code output}.
	 * <p>
	 * Jpx re-declares namespaces like Garmin's {@code TrackPointExtension} on every single
	 * point's extensions element instead of once on the document root, which can bloat a
	 * multi-hour track by 20-25% with purely redundant declarations.
	 * <p>
	 * Dom4j's {@link XMLWriter} already keeps track of which namespaces are currently in scope
	 * while it writes a tree, and skips re-declaring one that an already-open ancestor declared.
	 * So all this needs to do is parse jpx's output into a dom4j {@link Document} and declare the
	 * repeated namespace(s) once on the root element - dom4j suppresses the (still present)
	 * repeated declarations on every descendant by itself, without needing to touch any of them.
	 */
	public void writeGpx(Path output, GPX gpx) throws IOException
	{
		Path absoluteOutput = output.toAbsolutePath();
		Path tempFile = Files.createTempFile(absoluteOutput.getParent(), absoluteOutput.getFileName().toString(), ".tmp");
		try {
			GPX_WRITER.write(gpx, tempFile);

			Document document = readDocument(tempFile);
			hoistRepeatedNamespaces(document.getRootElement());
			writeDocument(document, output);
		}
		finally {
			Files.deleteIfExists(tempFile);
		}
	}

	private static Document readDocument(Path file) throws IOException
	{
		try (InputStream input = Files.newInputStream(file)) {
			return new SAXReader().read(input);
		}
		catch (DocumentException e) {
			throw new IOException(e);
		}
	}

	private static void writeDocument(Document document, Path output) throws IOException
	{
		OutputFormat format = OutputFormat.createPrettyPrint();
		format.setIndent("\t");
		format.setLineSeparator("\n");
		format.setEncoding("UTF-8");

		try (OutputStream out = Files.newOutputStream(output)) {
			XMLWriter writer = new XMLWriter(out, format);
			writer.write(document);
			writer.flush();
		}
	}

	/**
	 * Adds a single declaration, on the root element, for every prefixed namespace that occurs
	 * more than once in the document with the same URI. A prefix that gets rebound to a different
	 * URI somewhere is left alone, since hoisting it could change what the elements in between
	 * resolve to; dom4j is left to write those (still present) declarations exactly where they are.
	 */
	static void hoistRepeatedNamespaces(Element root)
	{
		Map<String, String> canonical = new LinkedHashMap<>();
		Set<String> conflicting = new HashSet<>();
		collectNamespaces(root, canonical, conflicting);
		canonical.keySet().removeAll(conflicting);

		for (Map.Entry<String, String> entry : canonical.entrySet()) {
			root.add(Namespace.get(entry.getKey(), entry.getValue()));
		}
	}

	private static void collectNamespaces(Element element, Map<String, String> canonical, Set<String> conflicting)
	{
		for (Namespace namespace : element.declaredNamespaces()) {
			String prefix = namespace.getPrefix();
			if (prefix == null || prefix.isEmpty()) {
				continue;
			}

			String existing = canonical.putIfAbsent(prefix, namespace.getURI());
			if (existing != null && !existing.equals(namespace.getURI())) {
				conflicting.add(prefix);
			}
		}

		for (Iterator<Element> it = element.elementIterator(); it.hasNext(); ) {
			collectNamespaces(it.next(), canonical, conflicting);
		}
	}
}
