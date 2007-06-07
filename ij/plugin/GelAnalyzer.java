package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

/** This plugin generates gel profile plots that can be analyzed using
the wand tool. It is similar to the "Gel Plotting Macros" in NIH Image. */
public class GelAnalyzer implements PlugIn {

	static int saveID;
	static int nLanes = 0;
	static Rectangle firstRect;
	static final int MAX_LANES = 100;
	static int[] y = new int[MAX_LANES+1];
	static PlotsCanvas plotsCanvas;
	static boolean uncalibratedOD = true;
	static boolean labelWithPercentages = true;
	boolean invertedLut;
			
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
			
		if (arg.equals("reset")) {
			nLanes=0;
			saveID=imp.getID();
			if (plotsCanvas!=null) {
				plotsCanvas.reset();
			}
			return;
		}
		
		if (arg.equals("percent") && plotsCanvas!=null) {
			plotsCanvas.displayPercentages();
			return;
		}
		
		if (arg.equals("label") && plotsCanvas!=null) {
			if (plotsCanvas.counter==0)
				show("There are no peak area measurements.");
			else
				plotsCanvas.labelPeaks();
			return;
		}
		
		if (arg.equals("options")) {
			GenericDialog gd = new GenericDialog("Gel Analyzer Options...", IJ.getInstance());
			gd.addCheckbox("Uncalibrated OD", uncalibratedOD);
			gd.addCheckbox("Label with Percentages", labelWithPercentages);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			uncalibratedOD = gd.getNextBoolean();
			labelWithPercentages = gd.getNextBoolean();
			return;
		}
		
		if (imp.getID()!=saveID)
			{nLanes=0; saveID=imp.getID();}
			
		Roi roi = imp.getRoi();
		if (roi!=null && arg.equals("perimeter")) {
			IJ.write("Perimeter: "+roi.getLength());
			return;
		}
		if (roi==null || roi.getType()!=Roi.RECTANGLE)
			{show("Rectangular selection required."); return;}
		invertedLut = imp.isInvertedLut();
		Rectangle rect = roi.getBoundingRect();
		if (nLanes==0)
			IJ.register(GelAnalyzer.class);  // keeps this class from being GC'd
			
		if (arg.equals("first"))
			{selectFirstLane(rect); return;}
		if (nLanes==0)
			{show("You must first use the \"Outline First Lane\" command."); return;}
		if (arg.equals("next"))
			{selectNextLane(rect); return;}
		if (arg.equals("plot")) {
			if (rect.y!=y[nLanes])
				selectNextLane(rect);
			plotLanes(imp);
			return;
		}
		
	}

	void selectFirstLane(Rectangle rect) {
		if (rect.height>=rect.width)
			{show("Lanes must be horizontal."); return;}
		IJ.showStatus("Lane 1 selected");
		firstRect = rect;
		nLanes = 1;
		y[1] = rect.y;
	}

	void selectNextLane(Rectangle rect) {
		if (rect.width!=firstRect.width || rect.height!=firstRect.height)
			{show("Selections must all be the same size."); return;}
		if (nLanes<MAX_LANES)
			nLanes += 1;
		IJ.showStatus("Lane " + nLanes + " selected");
		y[nLanes] = rect.y;
	}

	double od(double v) {
		if (invertedLut) {
			if (v==255.0) v = 254.5;
			return 0.434294481*Math.log(255.0/(255.0-v));
		} else {
			if (v==0.0) v = 0.5;
			return 0.434294481*Math.log(255.0/v);
		}
	}

	void plotLanes(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		if (uncalibratedOD)
			cal.setFunction(Calibration.UNCALIBRATED_OD, null, "Uncalibrated OD");
		else if (cal.getFunction()==Calibration.UNCALIBRATED_OD)
			cal.setFunction(Calibration.NONE, null, "Gray Value");
		int topMargin = 16;
		int bottomBorder = 2;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		int plotWidth, plotHeight;
		double[][] profiles;
		profiles = new double[MAX_LANES+1][];
		IJ.showStatus("Plotting " + nLanes + " lanes");
		for (int i=1; i<=nLanes; i++) {
			imp.setRoi(firstRect.x, y[i], firstRect.width, firstRect.height);
			ProfilePlot pp = new ProfilePlot(imp);
			profiles[i] = pp.getProfile();
			if (pp.getMin()<min)
				min = pp.getMin();
			if (pp.getMax()>max)
				max = pp.getMax();
			//IJ.write("  " + i + ": " + pp.getMin() + " " + pp.getMax());
		}
		plotWidth = firstRect.width;
		if (plotWidth<500)
			plotWidth = 500;
		if (plotWidth>2*firstRect.width)
			plotWidth = 2*firstRect.width;
		plotHeight = plotWidth/2;
		if (plotHeight<200)
			plotHeight = 200;
		if (plotHeight>400)
			plotHeight = 400;
		ImageProcessor ip = new ByteProcessor(plotWidth, topMargin+nLanes*plotHeight+bottomBorder);
		ip.setColor(Color.white);
		ip.fill();
		ip.setColor(Color.black);
		//draw border
		int h= ip.getHeight();
		ip.moveTo(0,0);
		ip.lineTo(plotWidth-1,0);
		ip.lineTo(plotWidth-1, h-1);
		ip.lineTo(0, h-1);
		ip.lineTo(0, 0);
		ip.moveTo(0, h-2);
		ip.lineTo(plotWidth-1, h-2);
		String s = imp.getTitle()+"; ";
		if (cal.calibrated())
			s += cal.getValueUnit();
		else
			s += "**Uncalibrated**";
		ip.moveTo(5,topMargin);
		ip.drawString(s);
		double xScale = (double)plotWidth/profiles[1].length;
		double yScale;
		if ((max-min)==0.0)
			yScale = 1.0;
		else
			yScale = plotHeight/(max-min);
		for (int i=1; i<=nLanes; i++) {
			double[] profile = profiles[i];
			int top = (i-1)*plotHeight + topMargin;
     		int base = top+plotHeight;
			ip.moveTo(0, base);
			ip.lineTo((int)(profile.length*xScale), base);
			ip.moveTo(0, base-(int)((profile[0]-min)*yScale));
			for (int j = 1; j<profile.length; j++)
				ip.lineTo((int)(j*xScale+0.5), base-(int)((profile[j]-min)*yScale+0.5));
	 	}
	 	ImagePlus plots = new Plots();
	 	plots.setProcessor("Plots", ip);
	 	ip.setThreshold(0,0,ImageProcessor.NO_LUT_UPDATE); // Wand tool works better with threshold set
	 	plots.show();
		nLanes = 0;
		saveID = 0;
		Toolbar toolbar = Toolbar.getInstance();
		toolbar.setColor(Color.black);
		toolbar.setTool(Toolbar.LINE);
		ImageWindow win = WindowManager.getCurrentWindow();
		plotsCanvas = (PlotsCanvas)win.getCanvas();
	}
	
	void show(String msg) {
		IJ.showMessage("Gel Analyzer", msg);
	}
}


class Plots extends ImagePlus {

	/** Overrides ImagePlus.show(). */
	public void show() {
		img = ip.createImage();
		ImageCanvas ic = new PlotsCanvas(this);
		win = new ImageWindow(this, ic);
		while(ic.getMagnification()<1.0)
			ic.zoomIn(0,0);
		Dimension d = win.getSize();
		int w = getWidth()+20;
		int h = getHeight()+20;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		if (w>screen.width)
			w = screen.width-20;
		if (h>(screen.height+100))
			h = screen.height-100;
		win.setSize(w, h);
		win.validate();
		repaintWindow();
		IJ.showStatus("");
	}

}


class PlotsCanvas extends ImageCanvas {

	public static final int MAX_PEAKS = 200;
	
	double[] actual = {428566.00,351368.00,233977.00,99413.00,60057.00,31382.00,
						14531.00,7843.00,2146.00,752.00,367.00};
	double[] measured = new double[MAX_PEAKS];
	Rectangle[] rect = new Rectangle[MAX_PEAKS];
	int counter;
	
	public PlotsCanvas(ImagePlus imp) {
		super(imp);
	}

	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		Roi roi = imp.getRoi();
		if (roi==null)
			return;
		if (roi.getType()==Roi.LINE)
			Roi.setColor(Color.blue);
		else
			Roi.setColor(Color.yellow);
		if (Toolbar.getToolId()!=Toolbar.WAND || IJ.spaceBarDown())
			return;
		ImageStatistics s = imp.getStatistics();
		if (counter==0)
			IJ.setColumnHeadings(" \tArea");
		double perimeter = roi.getLength();
		String error = "";
		double circularity = 4.0*Math.PI*(s.pixelCount/(perimeter*perimeter));
		if (circularity<0.025)
			error = " (error?)";
		double area = s.pixelCount+perimeter/2.0; // add perimeter/2 to account area under border
		rect[counter] = roi.getBoundingRect();
		//area += (rect[counter].width/rect[counter].height)*1.5; // adjustment for small peaks from NIH Image gel macros
		IJ.write((counter+1)+"\t"+IJ.d2s(area, 0)+error);
		measured[counter] = area;
		if (counter<MAX_PEAKS)
			counter++;
	}

	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
		Roi roi = imp.getRoi();
		if (roi!=null && roi.getType()==Roi.LINE) {
			Undo.setup(Undo.FILTER, imp);
			imp.getProcessor().snapshot();
			roi.drawPixels();
			imp.updateAndDraw();
			imp.killRoi();
		}
	}

	void reset() {
		counter = 0;
	}

	void labelPeaks() {
		imp.killRoi();
		double total = 0.0;
		for (int i=0; i<counter; i++)
			total += measured[i];
		ImageProcessor ip = imp.getProcessor();
		for (int i=0; i<counter; i++) {
			Rectangle r = rect[i];
			String s;
			if (GelAnalyzer.labelWithPercentages)
				s = IJ.d2s((measured[i]/total)*100, 2);
			else
				s = IJ.d2s(measured[i], 0);
			int swidth = ip.getStringWidth(s);
			int x = r.x + r.width/2 - swidth/2;
			int	y = r.y + r.height*3/4 + 9;
			int[] data = new int[swidth];
			ip.getRow(x, y, data, swidth);
			boolean fits = true;
			for (int j=0; j<swidth; j++)
				if (data[j]!=255)
					{fits = false; break;}
			fits = fits && measured[i]>500;
			if (!fits) y = r.y - 2;
			ip.moveTo(x, y); 
			ip.drawString(s);
			//IJ.write(i+": "+x+" "+y+" "+s+" "+ip.StringWidth(s)/2);
		}
		imp.updateAndDraw();
		displayPercentages();
		reset();
	}

	void displayPercentages() {
		IJ.setColumnHeadings(" \tarea\tpercent");
		double total = 0.0;
		for (int i=0; i<counter; i++)
				total += measured[i];
		if (IJ.debugMode && counter==actual.length) {
			debug();
			return;
		}
		for (int i=0; i<counter; i++) {
			double percent = (measured[i]/total)*100;
			IJ.write((i+1)+"\t"+IJ.d2s(measured[i],4)+"\t"+IJ.d2s(percent,4));
		}
	}
	
	void debug() {
		for (int i=0; i<counter; i++) {
			double a = (actual[i]/actual[0])*100;
			double m = (measured[i]/measured[0])*100;
			IJ.write(IJ.d2s(a, 4)+" "
			+IJ.d2s(m, 4)+" "
			+IJ.d2s(((m-a)/m)*100, 4));
		}
	}
	
}


