package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;
import java.util.Properties;

/** This plugin implements the Page Setup and Print commands. */
public class Printer implements PlugInFilter {
    private ImagePlus imp;
    private static double scaling = 100.0;
    private static boolean drawBorder;
    private static boolean center = true;
    private static boolean label;
    private static boolean printSelection;
	private static Properties printPrefs = new Properties();

	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("setup"))
			{pageSetup(); return DONE;}
		this.imp = imp;
		IJ.register(Printer.class);
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		print(imp);
	}
	
	void pageSetup() {
		GenericDialog gd = new GenericDialog("Page Setup");
		gd.addNumericField("Scaling (5-500%):", scaling, 0);
		gd.addCheckbox("Draw Border", drawBorder);
		gd.addCheckbox("Center on Page", center);
		gd.addCheckbox("Print Title", label);
		gd.addCheckbox("Selection Only", printSelection);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		scaling = gd.getNextNumber();
		if (scaling<5.0) scaling = 5;
		drawBorder = gd.getNextBoolean();
		center = gd.getNextBoolean();
		label = gd.getNextBoolean();
		printSelection = gd.getNextBoolean();
	}

	void print(ImagePlus imp) {
		ImageWindow win = imp.getWindow();
		if (win==null)
			return;
		ImageCanvas ic = win.getCanvas();
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		PrintJob job = toolkit.getPrintJob(win, imp.getTitle(), printPrefs);
		if (job==null)
			return;
		imp.startTiming();
		Graphics g = job.getGraphics();
		if (g==null)
			return;
		Dimension pageSize = job.getPageDimension();
		if (IJ.debugMode) IJ.log("pageSize: "+pageSize);
		double scale = scaling/100.0;
		int width = imp.getWidth();
		int height = imp.getHeight();
		Roi roi = imp.getRoi();
		boolean crop = false;
		if (printSelection && roi!=null && roi.getType()<=Roi.TRACED_ROI) {
			Rectangle r = roi.getBoundingRect();
			width = r.width;
			height = r.height;
			crop = true;
		}
		int printWidth = (int)(width*scale);
		int printHeight = (int)(height*scale);
		int margin = 20;
		int labelHeight = 0;
		int maxWidth = pageSize.width-margin*2;
		int maxHeight = pageSize.height-(margin+labelHeight)*2;
		g.setColor(Color.black);
		if (label) {
			labelHeight = 15;
			g.setFont(new Font("SanSerif", Font.PLAIN, 12));
			g.drawString(imp.getTitle(), margin+5, margin+labelHeight-3);
		}
		ImageProcessor ip = imp.getProcessor();
		if (crop)
			ip = ip.crop();
		if (width>maxWidth || height>maxHeight) {
			// scale to fit page
			double hscale = (double)maxWidth/width;
			double vscale = (double)maxHeight/height;
			if (hscale<=vscale)
				scale = hscale;
			else
				scale = vscale;
			printWidth = (int)(width*scale);
			printHeight = (int)(height*scale);
			if (System.getProperty("os.name").startsWith("Windows") && System.getProperty("java.version").startsWith("1.3.1")) {
				// workaround for Windows/Java 1.3.1 printing bug
				ip.setInterpolate(true);
				ip = ip.resize(printWidth, printHeight);
			}
		}
		Image img = ip.createImage();
		if (center && width<maxWidth && height<maxHeight)
			g.translate((pageSize.width-width)/2, labelHeight+(pageSize.height-height)/2);
		else
			g.translate(margin, margin+labelHeight);
		if (drawBorder)
			g.drawRect(-1, -1, (int)(printWidth)+1, (int)(printHeight)+1);
		//g.setClip(0, 0, pageSize.width, pageSize.height);
		//IJ.log(width+" "+height+" "+printWidth+" "+printHeight+" "+pageSize.width+" "+pageSize.height);
		g.drawImage(img, 0, 0, printWidth, printHeight, null);
		g.dispose();
		job.end();
	}

}