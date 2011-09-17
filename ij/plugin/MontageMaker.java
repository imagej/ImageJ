package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.RGBStackSplitter;
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
	private static int fontSize = 12;
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
			if (mode==CompositeImage.COMPOSITE)
				ci.setMode(CompositeImage.COLOR);
			ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
			for (int c=1; c<=channels; c++) {
				imp.setPositionWithoutUpdate(c, imp.getSlice(), imp.getFrame());
				Image img = imp.getImage();
				stack.addSlice(null, new ColorProcessor(img));
			}
			if (ci.getMode()!=mode)
				ci.setMode(mode);
			imp.setPosition(channel, imp.getSlice(), imp.getFrame());
			imp = new ImagePlus(imp.getTitle(), stack);
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
			if (columns==0 || !(imp.getID()==saveID || nSlices==saveStackSize)) {
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
			saveStackSize = nSlices;
			
			GenericDialog gd = new GenericDialog("Make Montage", IJ.getInstance());
			gd.addNumericField("Columns:", columns, 0);
			gd.addNumericField("Rows:", rows, 0);
			gd.addNumericField("Scale Factor:", scale, 2);
			if (!hyperstack) {
				gd.addNumericField("First Slice:", first, 0);
				gd.addNumericField("Last Slice:", last, 0);
			}
			gd.addNumericField("Increment:", inc, 0);
			gd.addNumericField("Border Width:", borderWidth, 0);
			gd.addNumericField("Font Size:", fontSize, 0);
			gd.addCheckbox("Label Slices", label);
			gd.addCheckbox("Use Foreground Color", useForegroundColor);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			columns = (int)gd.getNextNumber();
			rows = (int)gd.getNextNumber();
			scale = gd.getNextNumber();
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
		int montageWidth = width*columns;
		int montageHeight = height*rows;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor montage = ip.createProcessor(montageWidth+borderWidth/2, montageHeight+borderWidth/2);
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
		Dimension screen = IJ.getScreenSize();
		montage.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
		montage.setAntialiasedText(true);
		ImageStack stack = imp.getStack();
		int x = 0;
		int y = 0;
		ImageProcessor aSlice;
	    int slice = first;
		while (slice<=last) {
			aSlice = stack.getProcessor(slice);
			if (scale!=1.0)
				aSlice = aSlice.resize(width, height);
			montage.insert(aSlice, x, y);
			String label = stack.getShortSliceLabel(slice);
			if (borderWidth>0) drawBorder(montage, x, y, width, height, borderWidth);
			if (labels) drawLabel(montage, slice, label, x, y, width, height, borderWidth);
			x += width;
			if (x>=montageWidth) {
				x = 0;
				y += height;
				if (y>=montageHeight)
					break;
			}
			IJ.showProgress((double)(slice-first)/(last-first));
			slice += inc;
		}
		if (borderWidth>0) {
			int w2 = borderWidth/2;
			drawBorder(montage, w2, w2, montageWidth-w2, montageHeight-w2, borderWidth);
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
		ImagePlus[] channels = RGBStackSplitter.splitChannels(imp);
		int n = channels.length;
		ImagePlus[] montages = new ImagePlus[n];
		for (int i=0; i<n; i++) {
			int last = channels[i].getStackSize();
			montages[i] = makeMontage2(channels[i], columns, rows, scale, 1, last, inc, borderWidth, labels);
		}
		ImagePlus montage = (new RGBStackMerge()).mergeHyperstacks(montages, false);
		montage.setTitle("Montage");
		return montage;
	}
	
	private void error(String msg) {
		IJ.error("Make Montage", msg);
	}
	
	void drawBorder(ImageProcessor montage, int x, int y, int width, int height, int borderWidth) {
		montage.setLineWidth(borderWidth);
		montage.moveTo(x, y);
		montage.lineTo(x+width, y);
		montage.lineTo(x+width, y+height);
		montage.lineTo(x, y+height);
		montage.lineTo(x, y);
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


