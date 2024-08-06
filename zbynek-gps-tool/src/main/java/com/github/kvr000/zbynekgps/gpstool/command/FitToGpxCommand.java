package com.github.kvr000.zbynekgps.gpstool.command;

import com.garmin.fit.Decode;
import com.garmin.fit.DeviceInfoMesgListener;
import com.garmin.fit.FileIdMesgListener;
import com.garmin.fit.LapMesgListener;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.RecordMesgListener;
import com.garmin.fit.SportMesgListener;
import com.github.kvr000.zbynekgps.gpstool.ZbynekGpsTool;
import com.github.kvr000.zbynekgps.gpstool.fit.FitConstants;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Metadata;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.dryuf.base.function.ThrowingCallable;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.lang.ref.Reference;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FitToGpxCommand extends AbstractCommand
{
	private static final String TRACK_POINT_EXTENSIONS_ID = "gpxtpx"; // Although not correct, many tools rely on hardcoded prefix gpxtpx
	private static final String TRACK_POINT_EXTENSIONS_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1";
	private static final String TRACK_POINT_EXTENSIONS_EL = TRACK_POINT_EXTENSIONS_NS + " " + TRACK_POINT_EXTENSIONS_ID + ":TrackPointExtension";
	private static final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
	private static final DocumentBuilder docBuilder = ThrowingCallable.of(docFactory::newDocumentBuilder).sneakyCall();

	private final ZbynekGpsTool.Options mainOptions;

	private Options options = new Options();

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
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}
		if (options.inputs == null) {
			return usage(context, "input files required");
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Metadata.Builder metadata = Metadata.builder();
		GPX.Builder output = GPX.builder();
		output.version(GPX.Version.V11);

		List<Track> tracks = new ArrayList<>();

		for (String input: options.inputs) {
			Track.Builder track = Track.builder();
			List<WayPoint> wayPoints = new ArrayList<>();

			MutableDouble lastLon = new MutableDouble(Double.NaN);
			MutableDouble lastLat = new MutableDouble(Double.NaN);

			Runnable flush = () -> {
				if (wayPoints.isEmpty()) {
					return;
				}
				track.segments(List.of(TrackSegment.builder().points(wayPoints).build()));
				tracks.add(track.build());
				wayPoints.clear();

			};
			Stopwatch watch = Stopwatch.createStarted();
			try (FileInputStream fitFile = new FileInputStream(input)) {
				Decode decode = new Decode();
				MesgBroadcaster mesgBroadcaster = new MesgBroadcaster(decode);

				mesgBroadcaster.addListener((FileIdMesgListener) (mesg) -> {
					Optional.ofNullable(mesg.getProductName()).ifPresentOrElse(
						output::creator,
						() -> Optional.ofNullable(mesg.getManufacturer())
							.flatMap(manufacturer -> Optional.ofNullable(mesg.getProduct())
								.flatMap(product -> Optional.ofNullable(FitConstants.lookupDevice(manufacturer, product))))
							.ifPresent(output::creator)
					);
					Optional.ofNullable(mesg.getTimeCreated()).ifPresent(dt -> metadata.time(dt.getDate().toInstant()));
				});
				mesgBroadcaster.addListener((DeviceInfoMesgListener) (mesg) -> {
					if (mesg.getDeviceIndex() != 0) {
						return;
					}
					Optional.ofNullable(mesg.getProductName()).ifPresentOrElse(
						output::creator,
						() -> Optional.ofNullable(mesg.getManufacturer())
							.flatMap(manufacturer -> Optional.ofNullable(mesg.getProduct())
								.flatMap(product -> Optional.ofNullable(FitConstants.lookupDevice(manufacturer, product))))
							.ifPresent(output::creator)
					);
				});
				mesgBroadcaster.addListener((SportMesgListener) (mesg) -> {
					Optional.ofNullable(mesg.getSport()).map(FitConstants::lookupSport).ifPresent(sport -> {
						track.type(sport);
					});
				});

				mesgBroadcaster.addListener((LapMesgListener) (mesg) -> {
					flush.run();
				});

				mesgBroadcaster.addListener((RecordMesgListener) (recordMesg) -> {
					if (recordMesg.getPositionLat() != null && recordMesg.getPositionLong() != null) {
						lastLon.setValue(recordMesg.getPositionLong() * (180.0 / Math.pow(2, 31)));
						lastLat.setValue(recordMesg.getPositionLat() * (180.0 / Math.pow(2, 31)));
					}
					if (!lastLon.isNaN() && !lastLat.isNaN()) {
						Document extensions = docBuilder.newDocument();
						extensions.createAttribute("xmlns:" + TRACK_POINT_EXTENSIONS_ID).setValue(TRACK_POINT_EXTENSIONS_NS);
						extensions.appendChild(extensions.createElement("extensions"));
						WayPoint.Builder wayPoint = WayPoint.builder()
							.time(recordMesg.getTimestamp().getDate().toInstant())
							.lon(lastLon.getValue())
							.lat(lastLat.getValue());
						Optional<Float> altitude = Optional.ofNullable(recordMesg.getAltitude());
						altitude.ifPresent(wayPoint::ele);
						copyExtension(extensions, null, "power", recordMesg, RecordMesg::getPower);
						copyExtension(extensions, TRACK_POINT_EXTENSIONS_EL, TRACK_POINT_EXTENSIONS_ID + ":hr", recordMesg, RecordMesg::getHeartRate);
						copyExtension(extensions, TRACK_POINT_EXTENSIONS_EL, TRACK_POINT_EXTENSIONS_ID + ":cad", recordMesg, RecordMesg::getCadence);
						copyExtension(extensions, TRACK_POINT_EXTENSIONS_EL, TRACK_POINT_EXTENSIONS_ID + ":speed", recordMesg, RecordMesg::getSpeed);
						copyExtension(extensions, TRACK_POINT_EXTENSIONS_EL, TRACK_POINT_EXTENSIONS_ID + ":atemp", recordMesg, RecordMesg::getTemperature);
						wayPoint.extensions(extensions);
						wayPoints.add(wayPoint.build());
					}
				});

				decode.read(fitFile, mesgBroadcaster);
			}
			flush.run();
			log.info("Process file: file={} time={} ms", input, watch.elapsed(TimeUnit.MILLISECONDS));
		}
		output
			.metadata(metadata.build())
			.tracks(tracks);

		Stopwatch watch = Stopwatch.createStarted();
		GPX.write(output.build(), Paths.get(mainOptions.getOutput()));
		log.info("Written output in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	private static void copyExtension(Document extensions, String sub, String name, RecordMesg mesg, Function<RecordMesg, Object> extractor)
	{
		Object value = extractor.apply(mesg);
		if (value != null) {
			Element parent = extensions.getDocumentElement();
			if (sub != null) {
				parent = defineElement(parent, sub);
			}
			Element element = defineElement(parent, name);
			element.appendChild(extensions.createTextNode(value.toString()));
		}
	}

	private static Element defineElement(Element parent, String name)
	{
		Document doc = parent.getOwnerDocument();;
		Element element;
		int p;
		if ((p = name.lastIndexOf(" ")) >= 0) {
			int c = name.indexOf(':', p);
			if (c < 0) {
				throw new IllegalArgumentException("Namespace specified but no prefix provided");
			}
			NodeList elements = parent.getElementsByTagNameNS(name.substring(0, p), name.substring(c + 1));
			if (elements.getLength() > 0) {
				return (Element) elements.item(0);
			}
			element = parent.getOwnerDocument().createElementNS(name.substring(0, p), name.substring(p + 1));
		}
		else {
			NodeList elements = doc.getElementsByTagName(name);
			if (elements.getLength() > 0) {
				return (Element) elements.item(0);
			}
			element = parent.getOwnerDocument().createElement(name);
		}
		parent.appendChild(element);
		return element;
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"source", "files to convert"
		);
	}

	public static class Options
	{
		private List<String> inputs;
	}

	@EqualsAndHashCode
	@ToString
	static class TrackDetail
	{
		private Instant start;
		private Instant end;
		private Track track;

		public static TrackDetail from(Track track)
		{
			TrackDetail that = new TrackDetail();
			that.start = track.segments()
				.flatMap(TrackSegment::points)
				.filter(p -> p.getTime().isPresent())
				.findFirst()
				.flatMap(p -> p.getTime())
				.orElse(null);
			if (that.start == null)
				return null;
			that.end = Streams.findLast(track.segments()
					.flatMap(TrackSegment::points)
					.filter(p -> p.getTime().isPresent())
				)
				.flatMap(p -> p.getTime())
				.orElse(null);
			that.track = track;
			return that;
		}
	}
}
