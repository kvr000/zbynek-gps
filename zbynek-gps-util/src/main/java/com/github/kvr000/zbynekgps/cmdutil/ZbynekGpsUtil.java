package com.github.kvr000.zbynekgps.cmdutil;

import com.github.kvr000.zbynekgps.cmdutil.command.ConcatCommand;
import com.github.kvr000.zbynekgps.cmdutil.command.CutCommand;
import com.github.kvr000.zbynekgps.cmdutil.command.RetrackCommand;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.app.AppContext;
import net.dryuf.cmdline.app.BeanFactory;
import net.dryuf.cmdline.app.CommonAppContext;
import net.dryuf.cmdline.app.guice.GuiceBeanFactory;
import net.dryuf.cmdline.command.AbstractParentCommand;
import net.dryuf.cmdline.command.Command;
import net.dryuf.cmdline.command.CommandContext;
import net.dryuf.cmdline.command.RootCommandContext;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Map;


/**
 * ZbynekGpsUtil entry point.  This class only executes subcommands.
 */
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Log4j2
public class ZbynekGpsUtil extends AbstractParentCommand
{
	public static void main(String[] args)
	{
		runMain(args, (args0) -> {
			AppContext appContext = new CommonAppContext(Guice.createInjector(new GuiceModule()).getInstance(BeanFactory.class));
			return appContext.getBeanFactory().getBean(ZbynekGpsUtil.class).run(
				new RootCommandContext(appContext).createChild(null, "ZbynekGpsUtil", null),
				Arrays.asList(args0)
			);
		});
	}

	@Override
	protected String configHelpTitle(CommandContext context)
	{
		return "ZbynekGpsUtil - various GPX conversion tools";
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	@Override
	protected Map<String, Class<? extends Command>> configSubCommands(CommandContext context)
	{
		return ImmutableMap.of(
			"concat", ConcatCommand.class,
			"cut", CutCommand.class,
			"retrack", RetrackCommand.class
		);
	}

	protected Map<String, String> configCommandsDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"concat", "Concatenates multiple files into single one",
			"cut", "Cuts the period from track",
			"retrack", "Recalculates location for time when the device did not have GPS signal"
		);
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
