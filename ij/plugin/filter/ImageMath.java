package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.macro.*;
import java.awt.*;

/** This plugin implements ImageJ's Process/Math submenu. */
public class ImageMath implements PlugInFilter {
	
	public static final String MACRO_KEY = "math.macro";
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
	private static String macro = Prefs.get(MACRO_KEY, "v=v+50*sin(a*PI/180+d/5)");
	private Interpreter interp;
	private int w, h, w2, h2;
	boolean hasX, hasA, hasD, hasGetPixel;

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

	 	if (arg.equals("macro")) {
			applyMacro(ip);
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
	
	// first default: v = v+(sin(x/(w/25))+sin(y/(h/25)))*40
	// a=round(a/10); if (a%2==0) v=0;
	// cone: v=d
	// translate: v=getPixel(x+10,y+10)
	// flip vertically: v=getPixel(x,h-y-1)
	// spiral: v=(sin(d/10+a*PI/180)+1)*128
	// spiral on image: v=v+50*sin(a*PI/180+d/5)
	void applyMacro(ImageProcessor ip) {
		int PCStart = 25;
		if (image==1) {
			String macro2 = getMacro(macro);
			if (macro2==null) return;
			if (macro2.indexOf("=")==-1) {
				IJ.error("The variable 'v' must be assigned a value (e.g., \"v=255-v\")");
				canceled = true;
				return;
			}
			macro = macro2;
			Program pgm = (new Tokenizer()).tokenize(macro);
			hasX = pgm.lookupWord("x")!=null;
			hasA = pgm.lookupWord("a")!=null;
			hasD = pgm.lookupWord("d")!=null;
			hasGetPixel = pgm.lookupWord("getPixel")!=null;
			w = imp.getWidth();
			h = imp.getHeight();
			w2 = w/2;
			h2 = h/2;
			String code =
				"var v,x,y,z,w,h,d,a;\n"+
				"function dummy() {}\n"+
				macro2+";\n"; // code starts at program counter location 25
			interp = new Interpreter();
			interp.run(code);
			if (interp.wasError())
				return;
			Prefs.set(MACRO_KEY, macro);
			interp.setVariable("w", w);
			interp.setVariable("h", h);
		}
		interp.setVariable("z", image-1);
		int bitDepth = imp.getBitDepth();
		Rectangle r = ip.getRoi();
		int inc = r.height/50;
		if (inc<1) inc = 1;
		double v;
		int index, v2;
		if (bitDepth==8) {
			byte[] pixels1 = (byte[])ip.getPixels();
			byte[] pixels2 = pixels1;
			if (hasGetPixel)
				pixels2 = new byte[w*h];
			for (int y=r.y; y<(r.y+r.height); y++) {
				if (image==1 && y%inc==0)
					IJ.showProgress(y-r.y, r.height);
				interp.setVariable("y", y);
				for (int x=r.x; x<(r.x+r.width); x++) {
					index = y*w+x;
					v = pixels1[index]&255;
					interp.setVariable("v", v);
					if (hasX) interp.setVariable("x", x);
					if (hasA) interp.setVariable("a", getA(x,y));
					if (hasD) interp.setVariable("d", getD(x,y));
					interp.run(PCStart);
					v2 = (int)interp.getVariable("v");
					if (v2<0) v2 = 0;
					if (v2>255) v2 = 255;
					pixels2[index] = (byte)v2;
				}
			}
			if (hasGetPixel) System.arraycopy(pixels2, 0, pixels1, 0, w*h);
		} else if (bitDepth==24) {
			int rgb, red, green, blue;
			for (int y=r.y; y<(r.y+r.height); y++) {
				if (image==1 && y%inc==0)
					IJ.showProgress(y-r.y, r.height);
				interp.setVariable("y", y);
				for (int x=r.x; x<(r.x+r.width); x++) {
					if (hasX) interp.setVariable("x", x);
					if (hasA) interp.setVariable("a", getA(x,y));
					if (hasD) interp.setVariable("d", getD(x,y));
					rgb = ip.getPixel(x, y);
					red = (rgb&0xff0000)>>16;
					green = (rgb&0xff00)>>8;
					blue = rgb&0xff;
					interp.setVariable("v", red);
					interp.run(PCStart);
					red = (int)interp.getVariable("v");
					if (red<0) red=0; if (red>255) red=255;
					interp.setVariable("v", green);
					interp.run(PCStart);
					green= (int)interp.getVariable("v");
					if (green<0) green=0; if (green>255) green=255;
					interp.setVariable("v", blue);
					interp.run(PCStart);
					blue = (int)interp.getVariable("v");
					if (blue<0) blue=0; if (blue>255) blue=255;
					rgb = 0xff000000 | ((red&0xff)<<16) | ((green&0xff)<<8) | blue&0xff;
					ip.putPixel(x, y, rgb);
				}
			}
		} else {
			for (int y=r.y; y<(r.y+r.height); y++) {
				if (image==1 && y%inc==0)
					IJ.showProgress(y-r.y, r.height);
				interp.setVariable("y", y);
				for (int x=r.x; x<(r.x+r.width); x++) {
					v = ip.getPixelValue(x, y);
					interp.setVariable("v", v);
					if (hasX) interp.setVariable("x", x);
					if (hasA) interp.setVariable("a", getA(x,y));
					if (hasD) interp.setVariable("d", getD(x,y));
					interp.run(PCStart);
					ip.putPixelValue(x, y, interp.getVariable("v"));
				}
			}
		}
		if (image==1)
			IJ.showProgress(1.0);
		if (image==imp.getCurrentSlice())
			ip.resetMinAndMax();
	}
	
	final double getD(int x, int y) {
          double dx = x - w2; 
          double dy = y - h2;
          return Math.sqrt(dx*dx + dy*dy);
	}
	
	final double getA(int x, int y) {
		double angle = Math.atan2((h-y-1)-h2, x-w2);
		if (angle<0) angle += 2*Math.PI;
		return angle;
	}

	String getMacro(String macro) {
			GenericDialog gd = new GenericDialog("Macro");
			gd.addStringField("Code:", macro, 35);
			gd.setInsets(5,40,0);
			gd.addMessage("v=pixel value, x=x-coordinate, y=y-coordinate\nw=image width, h=image height, a=angle\nd=distance from center\n");
			gd.showDialog();
			if (image==1) imp.startTiming();
			canceled = gd.wasCanceled();
			if (canceled)
				return null;
			return gd.getNextString();
	}

}
