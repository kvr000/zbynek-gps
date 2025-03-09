package com.github.kvr000.zbynekgps.gpstool.command;

import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.geo.GeoCalc;
import com.github.kvr000.zbynekgps.gpstool.gpx.io.GpxRepo;
import com.github.kvr000.zbynekgps.gpstool.gpx.io.GpxRepoFactory;
import com.github.kvr000.zbynekgps.gpstool.gpx.util.GpxUtil;
import com.github.kvr000.zbynekgps.gpstool.gpxlike.io.GpxLikeFiles;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class MatchCommand extends AbstractCommand
{
	final GpxLikeFiles gpxLikeFiles;
	final GpxRepoFactory gpxRepoFactory;

	Options options;

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "--source-dir-1":
			options.sourceDir1 = needArgsParam(options.sourceDir1, args);
			return true;

		case "--source-dir-2":
			options.sourceDir2 = needArgsParam(options.sourceDir2, args);
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if ((options.sourceDir1 == null) || (options.sourceDir2 == null)) {
			return usage(context, "--source-dir-1 and --source-dir-2 must be specified");
		}
		return EXIT_CONTINUE;
	}

	@Override
	protected void createOptions(CommandContext context)
	{
		this.options = new Options();
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
				"--source-dir-1 directory", "read files from the directory",
				"--source-dir-2 directory", "read files from the directory"
		);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		final GpxRepo two = gpxRepoFactory.fromDir(Paths.get(options.sourceDir2));

		List<Path> oneFiles = FileUtils.listFiles(Paths.get(options.sourceDir1).toFile(), new String[]{".gpx", ".gpx.gz", ".fit", ".fit.gz"}, false).stream()
			.map(File::toPath)
			.toList();

		oneFiles.parallelStream()
			.map(file -> Pair.of(file, gpxLikeFiles.readGpxLikeSafe(file)))
			.filter(p -> p.getRight() != null)
			.map(p -> GpxUtil.expandToTimedWaypoints(p.getRight()).stream()
				.map(wp -> Pair.of(wp, two.getEpochMilli(wp.getTime().get().toEpochMilli())))
				.filter(pp -> pp.getRight() != null)
				.filter(pp -> GeoCalc.isWithinRadius(pp.getLeft(), pp.getRight().getLeft(), 50))
				.findFirst()
				.map(pp -> Pair.of(pp.getLeft().getTime(), Pair.of(p.getLeft(), pp.getRight().getRight())))
			)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.forEachOrdered(found -> System.out.println(String.format("Found matching activity: time=%s source1=%s source2=%s",
					found.getLeft().get(), found.getRight().getLeft(), found.getRight().getRight()))
			);

		log.info("Analyzed files in: count={} time={} ms", oneFiles.size(), watch.elapsed(TimeUnit.MILLISECONDS));

		return EXIT_SUCCESS;
	}

	public static class Options
	{
		String sourceDir1;
		String sourceDir2;
	}
}
