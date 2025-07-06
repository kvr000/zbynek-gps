package com.github.kvr000.zbynekgps.gpstool.fit.io;

import com.garmin.fit.Decode;
import com.garmin.fit.DeviceInfoMesgListener;
import com.garmin.fit.FileIdMesgListener;
import com.garmin.fit.FitRuntimeException;
import com.garmin.fit.LapMesgListener;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.RecordMesgListener;
import com.garmin.fit.SportMesgListener;
import com.github.kvr000.zbynekgps.gpstool.compress.AutoDecompressInputStream;
import com.github.kvr000.zbynekgps.gpstool.fit.FitConstants;
import com.google.common.base.Stopwatch;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Metadata;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.extern.log4j.Log4j2;
import net.dryuf.base.function.ThrowingCallable;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;


@Log4j2
public class FitFiles
{
	private static final String TRACK_POINT_EXTENSIONS_ID = "gpxtpx"; // Although not correct, many tools rely on hardcoded prefix gpxtpx
	private static final String TRACK_POINT_EXTENSIONS_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1";
	private static final String TRACK_POINT_EXTENSIONS_EL = TRACK_POINT_EXTENSIONS_NS + " " + TRACK_POINT_EXTENSIONS_ID + ":TrackPointExtension";
	private static final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
	private static final DocumentBuilder docBuilder = ThrowingCallable.of(docFactory::newDocumentBuilder).sneakyCall();

	/**
	 * Reads the fit file.
	 *
	 * @param fitFile
	 * 		fit InputStream
	 *
	 * @return
	 * 		fit file in form of GPX object.
	 *
	 * @throws IOException
	 * 		if reading fit file fails.
	 */
	public GPX readFit(InputStream fitFile) throws IOException
	{
		try {
			Metadata.Builder metadata = Metadata.builder();
			GPX.Builder output = GPX.builder();
			output.version(GPX.Version.V11);

			List<Track> tracks = new ArrayList<>();

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
				if (mesg.getDeviceIndex() != null) {
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
				Optional.ofNullable(mesg.getSport()).map(FitConstants::lookupSport).ifPresent(track::type);
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
					Instant time = recordMesg.getTimestamp().getDate().toInstant();
					WayPoint.Builder wayPoint = null;
					if (!wayPoints.isEmpty()) {
						WayPoint last = wayPoints.getLast();
						if (last.getTime().isPresent() && last.getTime().get().equals(time)) {
							wayPoints.removeLast();
							wayPoint = last.toBuilder();
							extensions = wayPoint.extensions().orElse(extensions);
						}
					}
					if (wayPoint == null) {
						wayPoint = WayPoint.builder()
							.time(recordMesg.getTimestamp().getDate().toInstant());
					}
					wayPoint
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

			flush.run();
			output
				.metadata(metadata.build())
				.tracks(tracks);

			return output.build();
		}
		catch (FitRuntimeException ex) {
			throw new IOException("Failed to read fit file: " + ex.getMessage(), ex);
		}
	}

	public GPX readFitDecompressed(InputStream fitFile) throws IOException
	{
		return readFit(new AutoDecompressInputStream(fitFile));
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
}
