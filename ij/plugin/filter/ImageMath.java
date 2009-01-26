package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.macro.Interpreter;
import java.awt.*;

/** This plugin implements ImageJ's Process/Math submenu. */
public class ImageMath implements PlugInFilter {
	
	private String arg;
	private ImagePlus imp;
	private boolean canceled;	
	private int image;
	private double lower;
	private double upper;
	
	private static double addValue = 25;
	private static double mulValue = 1.25;
	private static double minValue = 0;
	private static double maxValue = 255;
	private static final String defaultAndValue = "11110000";
	private static String andValue = defaultAndValue;
	private static final double defaultGammaValue = 0.5;
	private static double gammaValue = defaultGammaValue;
	private static String equation = "v2=255-v1";
	private Interpreter macro;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		IJ.register(ImageMath.class);
		return IJ.setupDialog(imp, DOES_ALL+SUPPORTS_MASKING);
	}

	public void run(ImageProcessor ip) {
		double value;
	 	if (canceled)
	 		return;
	 	image++;
	 	
	 	if (arg.equals("add")) {
	 		if (image==1) addValue = getValue("Add", "Value: ", addValue, 0);
	 		if (canceled) return;
			ip.add(addValue);
			return;
		}

	 	if (arg.equals("sub")) {
	 		if (image==1) addValue = getValue("Subtract", "Value: ", addValue, 0);
	 		if (canceled) return;
			ip.add(-addValue);
			return;
		}

	 	if (arg.equals("mul")) {
	 		if (image==1) mulValue = getValue("Multiply", "Value: ", mulValue, 2);
	 		if (canceled) return;
			ip.multiply(mulValue);
			return;
		}

	 	if (arg.equals("div")) {
	 		if (image==1) mulValue = getValue("Divide", "Value: ", mulValue, 2);
	 		if (canceled) return;
			if (mulValue!=0.0) ip.multiply(1.0/mulValue);
			return;
		}

	 	if (arg.equals("and")) {
	 		if (image==1) andValue = getBinaryValue("AND", "Value (binary): ", andValue);
	 		if (canceled) return;
	 		try {
				ip.and(Integer.parseInt(andValue,2));
			} catch (NumberFormatException e) {
				andValue = defaultAndValue;
				IJ.error("Binary number required");
			}
			return;
		}

	 	if (arg.equals("or")) {
	 		if (image==1) andValue = getBinaryValue("OR", "Value (binary): ", andValue);
	 		if (canceled) return;
	 		try {
				ip.or(Integer.parseInt(andValue,2));
			} catch (NumberFormatException e) {
				andValue = defaultAndValue;
				IJ.error("Binary number required");
			}
			return;
		}
			
	 	if (arg.equals("xor")) {
	 		if (image==1) andValue = getBinaryValue("XOR", "Value (binary): ", andValue);
	 		if (canceled) return;
	 		try {
				ip.xor(Integer.parseInt(andValue,2));
			} catch (NumberFormatException e) {
				andValue = defaultAndValue;
				IJ.error("Binary number required");
			}
			return;
		}
		
	 	if (arg.equals("min")) {
	 		if (image==1) minValue = getValue("Min", "Value: ", minValue, 0);
	 		if (canceled) return;
	 		ip.min(minValue);
			if (!(ip instanceof ByteProcessor))
				ip.resetMinAndMax();
			return;
		}

	 	if (arg.equals("max")) {
	 		if (image==1) maxValue = getValue("Max", "Value: ", maxValue, 0);
	 		if (canceled) return;
	 		ip.max(maxValue);
			if (!(ip instanceof ByteProcessor))
				ip.resetMinAndMax();
			return;
		}

	 	if (arg.equals("gamma")) {
	 		if (image==1) gammaValue = getValue("Gamma", "Value (0.1-5.0): ", gammaValue, 2);
	 		if (canceled) return;
	 		if (gammaValue<0.1 || gammaValue>5.0) {
	 			IJ.error("Gamma must be between 0.1 and 5.0");
	 			gammaValue = defaultGammaValue;
	 			return;
	 		}
			ip.gamma(gammaValue);
			return;
		}

	 	if (arg.equals("set")) {
	 		boolean rgb = ip instanceof ColorProcessor;
	 		String prompt = rgb?"Value (0-255): ":"Value: ";
	 		if (image==1) addValue = getValue("Set", prompt, addValue, 0);
	 		if (canceled) return;
			if (rgb) {
				if (addValue>255.0) addValue=255.0;
				if (addValue<0.0) addValue=0.0;
				int ival = (int)addValue;
	 			ip.setValue(ival + (ival<<8) + (ival<<16));
			} else
	 			ip.setValue(addValue);
			ip.fill();
			return;
		}

	 	if (arg.equals("log")) {
			ip.log();
			return;
		}
		
	 	if (arg.equals("exp")) {
			ip.exp();
			return;
		}

	 	if (arg.equals("sqr")) {
			ip.sqr();
			return;
		}

	 	if (arg.equals("sqrt")) {
			ip.sqrt();
			return;
		}

	 	if (arg.equals("reciprocal")) {
			if (!isFloat(ip)) return;
			float[] pixels = (float[])ip.getPixels();
			for (int i=0; i<ip.getWidth()*ip.getHeight(); i++) {
				if (pixels[i]==0f)
					pixels[i] = Float.NaN;
				else
					pixels[i] = 1f/pixels[i];
			}
			ip.resetMinAndMax();
			return;
		}
		
	 	if (arg.equals("nan")) {
	 		setBackgroundToNaN(ip);
			return;
		}

	 	if (arg.equals("abs")) {
			if (!((ip instanceof FloatProcessor)||imp.getCalibration().isSigned16Bit())) {
				IJ.error("32-bit or signed 16-bit image required");
				canceled = true;
			} else {
				ip.abs();
				ip.resetMinAndMax();
			}
			return;
		}

	 	if (arg.equals("equation")) {
			applyEquation(ip);
			return;
		}

	}
	
	boolean isFloat(ImageProcessor ip) {
		if (!(ip instanceof FloatProcessor)) {
			IJ.error("32-bit float image required");
			canceled = true;
			return false;
		} else
			return true;
	}
	
	double getValue (String title, String prompt, double defaultValue, int digits) {
			int places = Analyzer.getPrecision();
			if (digits>0 || (int)defaultValue!=defaultValue)
				digits = Math.max(places, 1);
			GenericDialog gd = new GenericDialog(title);
			gd.addNumericField(prompt, defaultValue, digits, 8, null);
			gd.showDialog();
			if (image==1) imp.startTiming();
			canceled = gd.wasCanceled();
			if (canceled)
				return defaultValue;
			return gd.getNextNumber();
	}

	String getBinaryValue (String title, String prompt, String defaultValue) {
			GenericDialog gd = new GenericDialog(title);
			gd.addStringField(prompt, defaultValue);
			gd.showDialog();
			if (image==1) imp.startTiming();
			canceled = gd.wasCanceled();
			if (canceled)
				return defaultValue;
			return gd.getNextString();
	}

	/** Set non-thresholded pixels in a float image to NaN. */
	void setBackgroundToNaN(ImageProcessor ip) {
		if (image==1) {
			lower = ip.getMinThreshold();
			upper = ip.getMaxThreshold();
			if (lower==ImageProcessor.NO_THRESHOLD || !(ip instanceof FloatProcessor)) {
				IJ.error("Thresholded 32-bit float image required");
				canceled = true;
				return;
			}
		}
        float[] pixels = (float[])ip.getPixels();
        int width = ip.getWidth();
        int height = ip.getHeight();
        double v;
        for (int y=0; y<height; y++) {
            for (int x=0; x<width; x++) {
                  v = pixels[y*width+x];
                  if (v<lower || v>upper)
                      pixels[y*width+x] = Float.NaN;
            }
        }
		ip.resetMinAndMax();
		return;
	}
	
	void applyEquation(ImageProcessor ip) {
		int PCStart = 19;
		if (image==1) {
			String eqn = getEquation(equation);
			if (eqn==null) return;
			String code =
				"var v1,v2,x,y,z;\n"+
				"function dummy() {}\n"+
				eqn+";\n"; // code starts at program counter location 19
			macro = new Interpreter();
			macro.run(code);
			if (macro.wasError())
				return;
			equation = eqn;
		}
		macro.setVariable("z", image-1);
		boolean hasX = equation.indexOf("x")!=-1;
		int bitDepth = imp.getBitDepth();
		Rectangle r = ip.getRoi();
		int inc = r.height/50;
		if (inc<1) inc = 1;
		double v1;
		if (bitDepth==8) {
			for (int y=r.y; y<(r.y+r.height); y++) {
				if (image==1 && y%inc==0)
					IJ.showProgress(y-r.y, r.height);
				macro.setVariable("y", y);
				for (int x=r.x; x<(r.x+r.width); x++) {
					v1 = ip.getPixel(x, y);
					macro.setVariable("v1", v1);
					if (hasX) macro.setVariable("x", x);
					macro.run(PCStart);
					ip.putPixel(x, y, (int)macro.getVariable("v2"));
				}
			}
		} else if (bitDepth==24) {
			int rgb, red, green, blue;
			for (int y=r.y; y<(r.y+r.height); y++) {
				if (image==1 && y%inc==0)
					IJ.showProgress(y-r.y, r.height);
				macro.setVariable("y", y);
				for (int x=r.x; x<(r.x+r.width); x++) {
					if (hasX) macro.setVariable("x", x);
					rgb = ip.getPixel(x, y);
					red = (rgb&0xff0000)>>16;
					green = (rgb&0xff00)>>8;
					blue = rgb&0xff;
					macro.setVariable("v1", red);
					macro.run(PCStart);
					red = (int)macro.getVariable("v2");
					if (red<0) red=0; if (red>255) red=255;
					macro.setVariable("v1", green);
					macro.run(PCStart);
					green= (int)macro.getVariable("v2");
					if (green<0) green=0; if (green>255) green=255;
					macro.setVariable("v1", blue);
					macro.run(PCStart);
					blue = (int)macro.getVariable("v2");
					if (blue<0) blue=0; if (blue>255) blue=255;
					rgb = 0xff000000 | ((red&0xff)<<16) | ((green&0xff)<<8) | blue&0xff;
					ip.putPixel(x, y, rgb);
				}
			}
		} else {
			for (int y=r.y; y<(r.y+r.height); y++) {
				if (image==1 && y%inc==0)
					IJ.showProgress(y-r.y, r.height);
				macro.setVariable("y", y);
				for (int x=r.x; x<(r.x+r.width); x++) {
					v1 = ip.getPixelValue(x, y);
					macro.setVariable("v1", v1);
					if (hasX) macro.setVariable("x", x);
					macro.run(PCStart);
					ip.putPixelValue(x, y, macro.getVariable("v2"));
				}
			}
		}
		if (image==1)
			IJ.showProgress(1.0);
		if (image==imp.getCurrentSlice())
			ip.resetMinAndMax();
	}

	String getEquation(String equation) {
			GenericDialog gd = new GenericDialog("Equation");
			gd.addStringField("Eqn: ", equation, 35);
			gd.showDialog();
			if (image==1) imp.startTiming();
			canceled = gd.wasCanceled();
			if (canceled)
				return null;
			return gd.getNextString();
	}

}
