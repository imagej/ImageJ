package ij.plugin.twain;
import java.awt.*;
import java.awt.image.*;
import java.util.Hashtable;
import SK.gnome.twain.TwainEx;
import ij.*;
import ij.process.*;
import ij.plugin.PlugIn;
import ij.measure.*;

/** Uses the Java Twain Package from http://www.gnome.sk/
	to acquire images from TWAIN sources. */
public class Twain implements PlugIn {

	String unit;
	double xResolution, yResolution;

		/** Set 'arg' to "select" to select the source, to "scan"
		to scan, and to "" to both select and scan. */
		public void run(String arg) {

		if (arg.equals("select"))
			{selectSource(); return;}

		if (arg.equals(""))
			if (!selectSource()) {
				return;
		}

		try {
 			check(TwainEx.register(1, 2, "Gnome", "Twain", "JavaTwain32"), "register");
			check(TwainEx.openSource(), "openSource");
			TwainEx.setTransferCount(1);
			check(TwainEx.prepareTransfer(), "prepareTransfer");
			TwainConsumer tc = new TwainConsumer();
			check(TwainEx.acquireImage(tc), "acquireImage");
			check(TwainEx.stopTransfer(), "stopTransfer");
			getUnits();
			check(TwainEx.closeSource(), "closeSource");
			check(TwainEx.unregister(), "unregister");
			ImagePlus imp =  new ImagePlus("Scan", tc.getProcessor());
			if (xResolution!=0.0 && yResolution!=0.0) {
				Calibration cal = new Calibration(imp);
				cal.pixelWidth = 1/xResolution;
				cal.pixelHeight = 1/yResolution;
				cal.setUnit(unit);			
				imp.setCalibration(cal);
			}
			imp.show();
		}
		catch (NoClassDefFoundError e) {
			needsGnome(e);
		}
		catch (Exception e) {
			TwainEx.unload();
			String s = e.toString();
			if (s.indexOf("acquireImage")<0)
				IJ.write(s);
		}
	}
		
	void check(boolean result, String name) throws Exception {
		int state = TwainEx.getState();
		int code = TwainEx.getResultCode();
		int  condition = TwainEx.getConditionCode();
		String codes = "";
		if (IJ.debugMode || !result)
			codes = "state="+state+", result="+code+", condition="+condition;
		if (result) {
			if (IJ.debugMode) IJ.write("Twain: "+name+" ("+codes+")");
		} else
				throw new Exception("TWAIN \""+name+ "\" failed\n    "+codes);
	}

	void getUnits() {
		int code = TwainEx.getCurrentUnits();
		if (code==0)
			unit = "inch";
		else if (code==1)
			unit = "cm";
		else
			unit = "";
		xResolution = TwainEx.getXResolution();
		yResolution = TwainEx.getYResolution();
	}

	void needsGnome(Throwable e) {
		if (!IJ.isMacintosh())
			IJ.error("The Twain plug-in requires the Java Twain Package from\n"
				+ "http://www.gnome.sk/. The files 'twain.jar' and 'JavaTwain32.dll'\n"
				+ "from this package must be placed in the ImageJ folder.\n \n"
				+ e);
		
	}

	public boolean selectSource() {
		try {
			check(TwainEx.register(1, 1, "Gnome", "Twain", "Twain"), "register");
			check(TwainEx.selectSource(), "selectSource");
			check(TwainEx.unregister(), "unregister");
		}
		catch (NoClassDefFoundError e) {
			needsGnome(e);
			return false;
		}
		catch (Exception e) {
			TwainEx.unload();
			String s = e.toString();
			if (s.indexOf("selectSource")<0)
				IJ.write(s);
			return false;
		}
		return true;
	}

}

class  TwainConsumer implements ImageConsumer {

	ImageProcessor cp, bp;
	int width, height;

	public void setDimensions(int width, int height) {
		if (IJ.debugMode) IJ.write("setDimensions: "+width+" "+height);
		this.width = width;
		this.height = height;
	}

	public void setProperties(Hashtable props) {
	}

 	public void setColorModel(ColorModel cm) {
		if (IJ.debugMode) IJ.write("setColorModel: ");
		if (cm instanceof IndexColorModel) {
			bp = new ByteProcessor(width, height);
 			bp.setColorModel(cm);
		} else
			cp = new ColorProcessor(width, height);
	}

	public void setHints(int hintflags) {
		if (IJ.debugMode) IJ.write("setHints:"+hintflags);
	}

	public void setPixels(int x, int y, int w, int h, ColorModel model, byte pixels[], int off, int scansize) {
		//IJ.write("setPixels (byte): "+w+" "+h+" "+x+" "+off+" "+scansize);
		for (int y2=y; y2<(y+h); y2++) {
			int i = (y2-y)*scansize+off;			
			for (int x2=x; x2<(x+w); x2++)
				bp.putPixel(x2, y2, pixels[i++]&255);
		}
	}

	public void setPixels(int x, int y, int w, int h, ColorModel model, int pixels[], int off, int scansize) {
		//if (y==0) IJ.write("setPixels (int): "+w+" "+h+" "+x+" "+off+" "+scansize);
		for (int y2=y; y2<(y+h); y2++) {
			int i = (y2-y)*scansize+off;			
			for (int x2=x; x2<(x+w); x2++)
				cp.putPixel(x2, y2, pixels[i++]);
		}
	}

	public void imageComplete(int status) {
		if (IJ.debugMode) IJ.write("imageComplete: "+status);
	}

	public ImageProcessor getProcessor() {
		ImageProcessor ip;
		if (bp!=null) 
			{ip = bp; bp = null;}
		else 
			{ip = cp; cp = null;}
		return ip;
	}
}


