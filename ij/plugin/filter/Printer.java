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
    private static boolean drawBorder = false;
    private static boolean center = true;
    private static boolean label = false;
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
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		scaling = gd.getNextNumber();
		if (scaling<5.0) scaling = 5;
		drawBorder = gd.getNextBoolean();
		center = gd.getNextBoolean();
		label = gd.getNextBoolean();
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
		int imageWidth = imp.getWidth();
		int imageHeight = imp.getHeight();
		int width = (int)(imageWidth*scale);
		int height = (int)(imageHeight*scale);
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
		if (width>maxWidth || height>maxHeight) {
			// scale to fit page
			double hscale = (double)maxWidth/imageWidth;
			double vscale = (double)maxHeight/imageHeight;
			if (hscale<=vscale)
				scale = hscale;
			else
				scale = vscale;
			width = (int)(imageWidth*scale);
			height = (int)(imageHeight*scale);
		}
		if (center)
			g.translate((pageSize.width-width)/2, labelHeight+(pageSize.height-height)/2);
		else
			g.translate(margin, margin+labelHeight);
		if (drawBorder)
			g.drawRect(-1, -1, (int)(width)+1, (int)(height)+1);
		g.setClip(0, 0, width, height);
		ic.print(g, scale);
		g.dispose();
		job.end();
	}

}