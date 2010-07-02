package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import ij.measure.Measurements;
import java.awt.*;

/** This plugin implements the Image/Stacks/Label command. */
public class StackLabeler implements ExtendedPlugInFilter, DialogListener {
	private static final String[] formats = {"0", "0000", "00:00", "00:00:00", "Text"};
	private static final int NUMBER=0, ZERO_PADDED_NUMBER=1, MIN_SEC=2, HOUR_MIN_SEC=3, TEXT=4;
	private static int format = (int)Prefs.get("label.format", NUMBER);
	private int flags = DOES_ALL;
	private ImagePlus imp;
	private static int x = 5;
	private static int y = 20;
	private static int fontSize = 18;
	private int maxWidth;
	private Font font;
	private static double start = 0;
	private static double interval = 1;
	private static String text = "";
	private static int decimalPlaces = 0;
	private static boolean useOverlay;
	private int fieldWidth;
	private Color color;
	private int firstSlice, lastSlice;
	private Overlay overlay;
	private boolean previewing; 
	private boolean virtualStack; 
	private int yoffset;

	public int setup(String arg, ImagePlus imp) {
		if (imp!=null) {
			if (imp.isHyperStack()&&imp.getNFrames()==1) {
				IJ.error("StackLabeler", "This command does not work with\nsingle time-point hyperstacks.");
				return DONE;
			}
			virtualStack = imp.getStack().isVirtual();
			if (virtualStack || imp.isHyperStack())
				useOverlay = true;
			flags += virtualStack?0:DOES_STACKS;
		}
		this.imp = imp;
		return flags;
	}

    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		ImageProcessor ip = imp.getProcessor();
		Rectangle roi = ip.getRoi();
		if (roi.width<ip.getWidth() || roi.height<ip.getHeight()) {
			x = roi.x;
			y = roi.y+roi.height;
			fontSize = (int) ((roi.height - 1.10526)/0.934211);	
			if (fontSize<7) fontSize = 7;
			if (fontSize>80) fontSize = 80;
		}
		if (IJ.macroRunning()) {
			format = NUMBER;
			decimalPlaces = 0;
		    interval=1;
			text = "";
			start = 0;
			useOverlay = false;
			String options = Macro.getOptions();
			if (options!=null) {
				if (options.indexOf("interval=0")!=-1 && options.indexOf("format=")==-1)
					format = TEXT;
				if (options.indexOf(" slice=")!=-1) {
					options = options.replaceAll(" slice=", " range=");
					Macro.setOptions(options);
				}
			}
		}
		if (format<0||format>TEXT) format = NUMBER;
		GenericDialog gd = new GenericDialog("StackLabeler");
		gd.setInsets(2, 5, 0);
		gd.addChoice("Format:", formats, formats[format]);
		gd.addStringField("Starting value:", IJ.d2s(start,decimalPlaces));
		gd.addStringField("Interval:", ""+IJ.d2s(interval,decimalPlaces));
		gd.addNumericField("X location:", x, 0);
		gd.addNumericField("Y location:", y, 0);
		gd.addNumericField("Font size:", fontSize, 0);
		gd.addStringField("Text:", text, 10);
        addRange(gd, "Range:", 1, imp.isHyperStack()?imp.getNFrames():imp.getStackSize());
		gd.setInsets(10,20,0);
        gd.addCheckbox(" Use overlay", useOverlay);
        gd.addPreviewCheckbox(pfr);
        gd.addHelp(IJ.URL+"/docs/menus/image.html#label");
        gd.addDialogListener(this);
        previewing = true;
		gd.showDialog();
		previewing = false;
		if (gd.wasCanceled())
        	return DONE;
        else
        	return flags;
    }

	void addRange(GenericDialog gd, String label, int start, int end) {
		gd.addStringField(label, start+"-"+end);
	}
	
	double[] getRange(GenericDialog gd, int start, int end) {
		String[] range = Tools.split(gd.getNextString(), " -");
		double d1 = Tools.parseDouble(range[0]);
		double d2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
		double[] result = new double[2];
		result[0] = Double.isNaN(d1)?1:(int)d1;
		result[1] = Double.isNaN(d2)?end:(int)d2;
		if (result[0]<start) result[0] = start;
		if (result[1]>end) result[1] = end;
		if (result[0]>result[1]) {
			result[0] = start;
			result[1] = end;
		}
		return result;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		format = gd.getNextChoiceIndex();
		start = Tools.parseDouble(gd.getNextString());
 		String str = gd.getNextString();
 		interval = Tools.parseDouble(str);
		x = (int)gd.getNextNumber();
		y = (int)gd.getNextNumber();
		fontSize = (int)gd.getNextNumber();
		text = gd.getNextString();
		double[] range = getRange(gd, 1, imp.getStackSize());
		useOverlay = gd.getNextBoolean();
		if (virtualStack) useOverlay = true;
		firstSlice=(int)range[0]; lastSlice=(int)range[1];
		int index = str.indexOf(".");
		if (index!=-1)
			decimalPlaces = str.length()-index-1;
		else
			decimalPlaces = 0;
		if (gd.invalidNumber()) return false;
		font = new Font("SansSerif", Font.PLAIN, fontSize);
		if (y<fontSize) y = fontSize+5;
		ImageProcessor ip = imp.getProcessor();
		ip.setFont(font);
		int stackSize = imp.getStackSize();
		maxWidth = ip.getStringWidth(getString(stackSize, interval, format));
		fieldWidth = 1;
		if (stackSize>=10) fieldWidth = 2;
		if (stackSize>=100) fieldWidth = 3;
		if (stackSize>=1000) fieldWidth = 4;
		if (stackSize>=10000) fieldWidth = 5;
		 Prefs.set("label.format", format);
        return true;
    }
	
	public void run(ImageProcessor ip) {
		int slice = ip.getSliceNumber();
		int n = slice - 1;
		if (imp.isHyperStack())
			n = (int)(n*((double)(imp.getNFrames())/imp.getStackSize()));
		if ((slice<firstSlice||slice>lastSlice) && !useOverlay) return;
		if (virtualStack) {
			int nSlices = imp.getStackSize();
			if (previewing) nSlices = 1;
			for (int i=1; i<=nSlices; i++) {
				slice=i; n=i-1;
				if (imp.isHyperStack())
					n = (int)(n*((double)(imp.getNFrames())/imp.getStackSize()));
				drawLabel(ip, slice, n);
			}
		} else {
			if (previewing && overlay!=null) {
				imp.setOverlay(null);
				overlay = null;
			}
			drawLabel(ip, slice, n);
		}
	}
	
	void drawLabel(ImageProcessor ip, int slice, int n) {
		String s = getString(n, interval, format);
		ip.setFont(font);
		int textWidth = ip.getStringWidth(s);
		if (color==null) {
			color = Toolbar.getForegroundColor();
			if ((color.getRGB()&0xffffff)==0) {
				ip.setRoi(x, y-fontSize, maxWidth+textWidth, fontSize);
				double mean = ImageStatistics.getStatistics(ip, Measurements.MEAN, null).mean;
				if (mean<50.0 && !ip.isInvertedLut()) color=Color.white;
				ip.resetRoi();
			}
		}
		if (useOverlay) {
			if (slice==1) {
				overlay = new Overlay();
				Roi roi = imp.getRoi();
				Rectangle r = roi!=null?roi.getBounds():null;
				yoffset = r!=null?r.height:fontSize;
			}
			int frame = slice;
			if (imp.isHyperStack()) {
				int[] pos = imp.convertIndexToPosition(slice);
				frame = pos[2];
			}
			Roi roi = new TextRoi(x+maxWidth-textWidth, y-yoffset, s, font);
			if (frame>=firstSlice&&frame<=lastSlice)
				roi.setStrokeColor(color);
			else
				roi.setStrokeColor(new Color(0f,0f,0f,0f)); // transparent
			overlay.add(roi);
			if (slice==imp.getStackSize() || previewing)
				imp.setOverlay(overlay);
		} else {
			ip.setColor(color); 
			ip.setAntialiasedText(fontSize>=18);
			ip.moveTo(x+maxWidth-textWidth, y);
			ip.drawString(s);
		}
	}
	
	String getString(int index, double interval, int format) {
		double time = start+index*interval;
		int itime = (int)Math.floor(time);
		String str = "";
		switch (format) {
			case NUMBER: str=IJ.d2s(time, decimalPlaces)+" "+text; break;
			case ZERO_PADDED_NUMBER:
				if (decimalPlaces==0)
					str=zeroFill((int)time); 
				else
					str=IJ.d2s(time, decimalPlaces);
				break;
			case MIN_SEC:
				str=pad((int)Math.floor((itime/60)%60))+":"+pad(itime%60);
				break;
			case HOUR_MIN_SEC:
				str=pad((int)Math.floor(itime/3600))+":"+pad((int)Math.floor((itime/60)%60))+":"+pad(itime%60);
				break;
			case TEXT: str=text; break;
		}
		return str;
	}
	
	String pad(int n) {
		String str = ""+n;
		if (str.length()==1) str="0"+str;
		return str;
	}
	
	String  zeroFill(int n) {
		String str = ""+n;
		while (str.length()<fieldWidth)
			str = "0" + str;
		return str;
	}
		
	public void setNPasses (int nPasses) {}

}
