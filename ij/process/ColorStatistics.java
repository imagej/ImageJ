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
		if ((mOptions&CENTER_OF_MASS)!=0)
			getCenterOfMass(ip);
	}

	void getCenterOfMass(ImageProcessor ip) {
		byte[] mask = ip.getMaskArray();
		int i, mi;
		double v, dv, count=0.0, xsum=0.0, ysum=0.0;
		for (int y=ry,my=0; y<(ry+rh); y++,my++) {
			i = y*width + rx;
			mi = my*rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {
					v = ip.getPixelValue(x, y);
					count += v;
					xsum += x*v;
					ysum += y*v;
				}
				i++;
			}
		}
		xCenterOfMass = (xsum/count+0.5)*pw;
		yCenterOfMass = (ysum/count+0.5)*ph;
	}

}