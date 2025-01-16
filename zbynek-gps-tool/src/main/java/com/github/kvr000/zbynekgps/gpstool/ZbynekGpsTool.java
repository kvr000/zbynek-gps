package com.github.kvr000.zbynekgps.gpstool;

import com.github.kvr000.zbynekgps.gpstool.command.ConcatCommand;
import com.github.kvr000.zbynekgps.gpstool.command.CutCommand;
import com.github.kvr000.zbynekgps.gpstool.command.FindCommand;
import com.github.kvr000.zbynekgps.gpstool.command.FitToGpxCommand;
import com.github.kvr000.zbynekgps.gpstool.command.RetrackCommand;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.app.AppContext;
import net.dryuf.cmdline.app.BeanFactory;
import net.dryuf.cmdline.app.CommonAppContext;
import net.dryuf.cmdline.app.guice.GuiceBeanFactory;
import net.dryuf.cmdline.command.AbstractParentCommand;
import net.dryuf.cmdline.command.Command;
import net.dryuf.cmdline.command.CommandContext;
import net.dryuf.cmdline.command.HelpOfHelpCommand;
import net.dryuf.cmdline.command.RootCommandContext;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.Map;


/**
 * ZbynekGpsTool entry point.  This class only executes subcommands.
 */
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Log4j2
public class ZbynekGpsTool extends AbstractParentCommand
{
	private Options options;

	public static void main(String[] args)
	{
		runMain(args, (args0) -> {
			AppContext appContext = new CommonAppContext(Guice.createInjector(new GuiceModule()).getInstance(BeanFactory.class));
			return appContext.getBeanFactory().getBean(ZbynekGpsTool.class).run(
				new RootCommandContext(appContext).createChild(null, "zbynek-gps-tool", null),
				Arrays.asList(args0)
			);
		});
	}

	protected CommandContext createChildContext(CommandContext commandContext, String name, boolean isHelp)
	{
		return commandContext.createChild(this, name, Map.of(Options.class, options));
	}

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "-o":
			options.output = needArgsParam(options.output, args);
			return true;

		case "--debug":
			options.debug = true;
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	public void createOptions(CommandContext context)
	{
		this.options = new Options();
	}

	@Override
	protected String configHelpTitle(CommandContext context)
	{
		return "zbynek-gps-tool - various GPX conversion tools";
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"-o output", "output filename",
			"--debug", "enables debug mode and dumps temporary data"
		);
	}

	@Override
	protected Map<String, Class<? extends Command>> configSubCommands(CommandContext context)
	{
		return ImmutableMap.of(
			"concat", ConcatCommand.class,
			"cut", CutCommand.class,
			"retrack", RetrackCommand.class,
			"find", FindCommand.class,
			"fit-to-gpx", FitToGpxCommand.class,
			"help", HelpOfHelpCommand.class
		);
	}

	protected Map<String, String> configCommandsDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"concat", "Concatenates multiple files into single one",
			"cut", "Cuts the period from track",
			"retrack", "Recalculates location for time when the device did not have GPS signal",
			"find", "Finds locations in set of files",
			"fit-to-gpx", "Converts fit file to gpx",
			"help [command]", "Prints help"
		);
	}

	@Data
	public static class Options
	{
		String output;

		boolean debug;
	}

	public static class GuiceModule extends AbstractModule
	{
		@Override
		protected void configure()
		{
		}

		@Provides
		public BeanFactory beanFactory(Injector injector)
		{
			return new GuiceBeanFactory(injector);
		}
	}
}
