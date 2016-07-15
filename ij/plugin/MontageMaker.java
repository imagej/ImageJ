package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

/** Implements the Image/Stacks/Make Montage command. */
public class MontageMaker implements PlugIn {
			
	private static int columns, rows, first, last, inc, borderWidth;
	private static double scale;
	private static boolean label;
	private static boolean useForegroundColor;
	private static int saveID;
	private static int saveStackSize;
	private static final int defaultFontSize = 12;
	private static int fontSize = defaultFontSize;
	private boolean hyperstack;

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || imp.getStackSize()==1) {
			error("Stack required");
			return;
		}
		hyperstack = imp.isHyperStack();
		if (hyperstack && imp.getNSlices()>1 && imp.getNFrames()>1) {
			error("5D hyperstacks are not supported");
			return;
		}
		int channels = imp.getNChannels();
		if (!hyperstack && imp.isComposite() && channels>1) {
			int channel = imp.getChannel();
			CompositeImage ci = (CompositeImage)imp;
			int mode = ci.getMode();
			if (mode==IJ.COMPOSITE)
				ci.setMode(IJ.COLOR);
			ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
			for (int c=1; c<=channels; c++) {
				imp.setPosition(c, imp.getSlice(), imp.getFrame());
				Image img = imp.getImage();
				stack.addSlice(null, new ColorProcessor(img));
			}
			if (ci.getMode()!=mode)
				ci.setMode(mode);
			imp.setPosition(channel, imp.getSlice(), imp.getFrame());
			Calibration cal = imp.getCalibration();
			imp = new ImagePlus(imp.getTitle(), stack);
			imp.setCalibration(cal);
		}
		makeMontage(imp);
		imp.updateImage();
		saveID = imp.getID();
		IJ.register(MontageMaker.class);
	}
	
	public void makeMontage(ImagePlus imp) {
			int nSlices = imp.getStackSize();
			if (hyperstack) {
				nSlices = imp.getNSlices();
				if (nSlices==1)
					nSlices = imp.getNFrames();
			}
			boolean macro = Macro.getOptions()!=null;
			if (macro || columns==0 || !(imp.getID()==saveID || nSlices==saveStackSize)) {
				columns = (int)Math.sqrt(nSlices);
				rows = columns;
				int n = nSlices - columns*rows;
				if (n>0) columns += (int)Math.ceil((double)n/rows);
				scale = 1.0;
				if (imp.getWidth()*columns>800)
					scale = 0.5;
				if (imp.getWidth()*columns>1600)
					scale = 0.25;
				inc = 1;
				first = 1;
				last = nSlices;
			}
			if (macro) {
				fontSize = defaultFontSize;
				borderWidth = 0;
				label = false;
				useForegroundColor = false;
			}
			saveStackSize = nSlices;
			
			GenericDialog gd = new GenericDialog("Make Montage");
			gd.addNumericField("Columns:", columns, 0);
			gd.addNumericField("Rows:", rows, 0);
			gd.addNumericField("Scale factor:", scale, 2);
			if (!hyperstack) {
				gd.addNumericField("First slice:", first, 0);
				gd.addNumericField("Last slice:", last, 0);
			}
			gd.addNumericField("Increment:", inc, 0);
			gd.addNumericField("Border width:", borderWidth, 0);
			gd.addNumericField("Font size:", fontSize, 0);
			gd.addCheckbox("Label slices", label);
			gd.addCheckbox("Use foreground color", useForegroundColor);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			columns = (int)gd.getNextNumber();
			rows = (int)gd.getNextNumber();
			scale = gd.getNextNumber();
			gd.setSmartRecording(true);
			if (!hyperstack) {
				first = (int)gd.getNextNumber();
				last = (int)gd.getNextNumber();
			}
			inc = (int)gd.getNextNumber();
			borderWidth = (int)gd.getNextNumber();
			fontSize = (int)gd.getNextNumber();
			if (borderWidth<0) borderWidth = 0;
			if (first<1) first = 1;
			if (last>nSlices) last = nSlices;
			if (first>last)
				{first=1; last=nSlices;}
			if (inc<1) inc = 1;
			if (gd.invalidNumber()) {
				error("Invalid number");
				return;
			}
			label = gd.getNextBoolean();
			useForegroundColor = gd.getNextBoolean();
			ImagePlus imp2 = null;
			if (hyperstack)
				imp2 = makeHyperstackMontage(imp, columns, rows, scale, inc, borderWidth, label);
			else
				imp2 = makeMontage2(imp, columns, rows, scale, first, last, inc, borderWidth, label);
			if (imp2!=null)
				imp2.show();
			if (macro) {
				fontSize = defaultFontSize;
				borderWidth = 0;
				label = false;
				useForegroundColor = false;
				columns = 0;
			}
	}
	
	/** Creates a montage and displays it. */
	public void makeMontage(ImagePlus imp, int columns, int rows, double scale, int first, int last, int inc, int borderWidth, boolean labels) {
		ImagePlus imp2 = makeMontage2(imp, columns, rows, scale, first, last, inc, borderWidth, labels);
		imp2.show();
	}

	/** Creates a montage and returns it as an ImagePlus. */
	public ImagePlus makeMontage2(ImagePlus imp, int columns, int rows, double scale, int first, int last, int inc, int borderWidth, boolean labels) {
		int stackWidth = imp.getWidth();
		int stackHeight = imp.getHeight();
		int nSlices = imp.getStackSize();
		int width = (int)(stackWidth*scale);
		int height = (int)(stackHeight*scale);
		int montageWidth = width*columns + borderWidth*(columns-1);
		int montageHeight = height*rows + borderWidth*(rows-1);
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor montage = ip.createProcessor(montageWidth, montageHeight);
		ImagePlus imp2 = new ImagePlus("Montage", montage);
		imp2.setCalibration(imp.getCalibration());
		montage = imp2.getProcessor();
		Color fgColor=Color.white;
		Color bgColor = Color.black;
		if (useForegroundColor) {
			fgColor = Toolbar.getForegroundColor();
			bgColor = Toolbar.getBackgroundColor();
		} else {
			boolean whiteBackground = false;
			if ((ip instanceof ByteProcessor) || (ip instanceof ColorProcessor)) {
				ip.setRoi(0, stackHeight-12, stackWidth, 12);
				ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.MODE, null);
				ip.resetRoi();
				whiteBackground = stats.mode>=200;
				if (imp.isInvertedLut())
					whiteBackground = !whiteBackground;
			}
			if (whiteBackground) {
				fgColor=Color.black;
				bgColor = Color.white;
			}
		}
		montage.setColor(bgColor);
		montage.fill();
		montage.setColor(fgColor);
		montage.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
		montage.setAntialiasedText(true);
		ImageStack stack = imp.getStack();
		int x = 0;
		int y = 0;
		ImageProcessor aSlice;
	    int slice = first;
		while (slice<=last) {
			aSlice = stack.getProcessor(slice);
			if (scale!=1.0) {
				aSlice.setInterpolationMethod(ImageProcessor.BILINEAR);
				boolean averageWhenDownSizing = width<200;
				aSlice = aSlice.resize(width, height, averageWhenDownSizing);
			}
			montage.insert(aSlice, x, y);
			String label = stack.getShortSliceLabel(slice);
			if (labels)
				drawLabel(montage, slice, label, x, y, width, height, borderWidth);
			x += width + borderWidth;
			if (x>=montageWidth) {
				x = 0;
				y += height + borderWidth;;
				if (y>=montageHeight)
					break;
			}
			IJ.showProgress((double)(slice-first)/(last-first));
			slice += inc;
		}
		if (borderWidth>0) {
			for (x=width; x<montageWidth; x+=width+borderWidth) {
				montage.setRoi(x, 0, borderWidth, montageHeight);
				montage.fill();
			}
			for (y=height; y<montageHeight; y+=height+borderWidth) {
				montage.setRoi(0, y, montageWidth, borderWidth);
				montage.fill();
			}
		}
		IJ.showProgress(1.0);
		Calibration cal = imp2.getCalibration();
		if (cal.scaled()) {
			cal.pixelWidth /= scale;
			cal.pixelHeight /= scale;
		}
        imp2.setProperty("Info", "xMontage="+columns+"\nyMontage="+rows+"\n");
		return imp2;
	}
		
	/** Creates a hyperstack montage and returns it as an ImagePlus. */
	private ImagePlus makeHyperstackMontage(ImagePlus imp, int columns, int rows, double scale, int inc, int borderWidth, boolean labels) {
		ImagePlus[] channels = ChannelSplitter.split(imp);
		int n = channels.length;
		ImagePlus[] montages = new ImagePlus[n];
		for (int i=0; i<n; i++) {
			int last = channels[i].getStackSize();
			montages[i] = makeMontage2(channels[i], columns, rows, scale, 1, last, inc, borderWidth, labels);
		}
		ImagePlus montage = (new RGBStackMerge()).mergeHyperstacks(montages, false);
		montage.setCalibration(montages[0].getCalibration());
		montage.setTitle("Montage");
		return montage;
	}
	
	private void error(String msg) {
		IJ.error("Make Montage", msg);
	}
	
	void drawLabel(ImageProcessor montage, int slice, String label, int x, int y, int width, int height, int borderWidth) {
		if (label!=null && !label.equals("") && montage.getStringWidth(label)>=width) {
			do {
				label = label.substring(0, label.length()-1);
			} while (label.length()>1 && montage.getStringWidth(label)>=width);
		}
		if (label==null || label.equals(""))
			label = ""+slice;
		int swidth = montage.getStringWidth(label);
		x += width/2 - swidth/2;
		y -= borderWidth/2;
		y += height;
		montage.drawString(label, x, y);
	}
}


