package ij.process;
import ij.measure.Calibration;

/** RGB image statistics, including histogram. */
public class ColorStatistics extends ImageStatistics {

	/** Construct an ImageStatistics object from a ColorProcessor
		using the standard measurement options (area, mean,
		mode, min and max). */
	public ColorStatistics(ImageProcessor ip) {
		this(ip, AREA+MEAN+MODE+MIN_MAX, null);
	}

	/** Constructs a ColorStatistics object from a ColorProcessor using
		the specified measurement options.
	*/
	public ColorStatistics(ImageProcessor ip, int mOptions, Calibration cal) {
		ColorProcessor cp = (ColorProcessor)ip;
		histogram = cp.getHistogram();
		setup(ip, cal);
		getRawStatistics(0,255);
		if ((mOptions&MIN_MAX)!=0)
			getRawMinAndMax(0,255);
		if ((mOptions&ELLIPSE)!=0)
			fitEllipse(ip);
		else if ((mOptions&CENTROID)!=0)
			getCentroid(ip);
	}

}