package com.github.kvr000.zbynekgps.gpstool.command;

import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.compress.AutoDecompressInputStream;
import com.github.kvr000.zbynekgps.gpstool.fit.FitFiles;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.jenetics.jpx.GPX;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.base.concurrent.executor.CloseableExecutor;
import net.dryuf.base.concurrent.executor.CommonPoolExecutor;
import net.dryuf.base.function.ThrowingCallable;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FitToGpxCommand extends AbstractCommand
{
	private static final String TRACK_POINT_EXTENSIONS_ID = "gpxtpx"; // Although not correct, many tools rely on hardcoded prefix gpxtpx
	private static final String TRACK_POINT_EXTENSIONS_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1";
	private static final String TRACK_POINT_EXTENSIONS_EL = TRACK_POINT_EXTENSIONS_NS + " " + TRACK_POINT_EXTENSIONS_ID + ":TrackPointExtension";
	private static final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
	private static final DocumentBuilder docBuilder = ThrowingCallable.of(docFactory::newDocumentBuilder).sneakyCall();

	private final FitFiles fitFiles;

	private final ZbynekGpsTool.Options mainOptions;

	private Options options = new Options();

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "--batch":
			options.batch = true;
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		ImmutableList<String> remaining = ImmutableList.copyOf(args);
		if (remaining.size() < 1) {
			return usage(context, "Need one or more parameters as source files");
		}
		options.inputs = remaining;
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (options.batch == (mainOptions.getOutput() != null)) {
			return usage(context, "-o output option or --batch option must be provided but not both");
		}
		if (options.inputs == null) {
			return usage(context, "input files required");
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		try (CloseableExecutor executor = new CommonPoolExecutor()) {
			for (String input : options.inputs) {
				String output;
				if (options.batch) {
					if (input.endsWith(".fit")) {
						output = input.substring(0, input.length() - 4) + ".gpx";
					}
					else if (input.endsWith(".fit.gz")) {
						output = input.substring(0, input.length() - 7) + ".gpx";
					}
					else {
						throw new IOException("File must end with .fit or .fit.gz extension: " + input);
					}
				} else {
					output = mainOptions.getOutput();
				}
				futures.add(executor.submit(() -> {
					processFile(output, input);
					return null;
				}));
			}
		}
		int ret = EXIT_SUCCESS;
		for (CompletableFuture<Void> future: futures) {
			try {
				future.join();
			}
			catch (CompletionException ex) {
				System.err.print("Failed to process: " + ex.getMessage() + ex);
				ret = EXIT_FAILURE;
			}
		}
		return ret;
	}

	private void processFile(String outputName, String inputName) throws IOException
	{
		Stopwatch watch = Stopwatch.createStarted();
		try (FileInputStream fitFile = new FileInputStream(inputName)) {
			GPX gpx = fitFiles.readFitDecompressed(fitFile);
			log.info("Process file: file={} time={} ms", inputName, watch.elapsed(TimeUnit.MILLISECONDS));

			Stopwatch watchWrite = Stopwatch.createStarted();
			GPX.write(gpx, Paths.get(outputName));
			log.info("Written output: file={} time={} ms", outputName, watchWrite.elapsed(TimeUnit.MILLISECONDS));
		}
		catch (Exception ex) {
			throw new IOException("Failed to process file: file=" + inputName + " : " + ex.getMessage(), ex);
		}
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"source", "files to convert"
		);
	}

	public static class Options
	{
		private boolean batch;

		private List<String> inputs;
	}
}
