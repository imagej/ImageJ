package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;

/** Implements the Analyze/Plot Profile and Edit/Options/Profile Plot Options commands. */
public class Profiler implements PlugIn, PlotMaker {
	ImagePlus imp;
	boolean firstTime = true;
	boolean plotVertically;

	public void run(String arg) {
		if (arg.equals("set"))
			{doOptions(); return;}
		imp = IJ.getImage();
		if (firstTime)
			plotVertically = Prefs.verticalProfile || IJ.altKeyDown();
		Plot plot = getPlot();
		firstTime = false;
		if (plot==null)
			return;
		plot.setPlotMaker(this);
		plot.show();
	}
	
	public Plot getPlot() {
		Roi roi = imp.getRoi();
		if (roi==null || !(roi.isLine()||roi.getType()==Roi.RECTANGLE)) {
			if (firstTime)
				IJ.error("Plot Profile", "Line or rectangular selection required");
			return null;
		}
		ProfilePlot pp = new ProfilePlot(imp, plotVertically);
		return pp.getPlot();
	}
	
	public ImagePlus getSourceImage() {
		return imp;
	}

	public void doOptions() {
		double ymin = ProfilePlot.getFixedMin();
		double ymax = ProfilePlot.getFixedMax();
		boolean fixedScale = ymin!=0.0 || ymax!=0.0;
		boolean wasFixedScale = fixedScale;
		
		GenericDialog gd = new GenericDialog("Plot Defaults");
		gd.setInsets(4,0,0);
		gd.addMessage("---------- Plot Defaults ---------");
		gd.addNumericField("Width:", PlotWindow.plotWidth, 0);
		gd.addNumericField("Height:", PlotWindow.plotHeight, 0);
		gd.addNumericField("Font size:", PlotWindow.getDefaultFontSize(), 0);
		gd.setInsets(5,20,0); //distance to previous
		//gd.addCheckbox("Draw grid lines", !PlotWindow.noGridLines);
		gd.addCheckbox("Draw_ticks", !PlotWindow.noTicks);
		gd.addCheckbox("Auto-close", PlotWindow.autoClose);
		gd.addCheckbox("List values", PlotWindow.listValues);
		
		gd.setInsets(15,0,0);
		gd.addMessage("------- Profile Plot Options -------");
		gd.setInsets(5,20,0);
		gd.addCheckbox("Fixed y-axis scale", fixedScale);
		gd.addNumericField("Minimum Y:", ymin, 2);
		gd.addNumericField("Maximum Y:", ymax, 2);
		gd.setInsets(10,20,0);
		gd.addCheckbox("Vertical profile", Prefs.verticalProfile);
		gd.addCheckbox("Interpolate line profiles", PlotWindow.interpolate);
		gd.addCheckbox("Sub-pixel resolution", Prefs.subPixelResolution);
		gd.addHelp(IJ.URL+"/docs/menus/edit.html#plot-options");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int w = (int)gd.getNextNumber();
		int h = (int)gd.getNextNumber();
		if (w<Plot.MIN_FRAMEWIDTH) w = Plot.MIN_FRAMEWIDTH;
		if (h<Plot.MIN_FRAMEHEIGHT) h = Plot.MIN_FRAMEHEIGHT;
		if (!gd.invalidNumber()) {
			PlotWindow.plotWidth = w;
			PlotWindow.plotHeight = h;
		}
		int fontSize = (int)gd.getNextNumber();
		if (!gd.invalidNumber())
			PlotWindow.setDefaultFontSize(fontSize);
		//PlotWindow.noGridLines = !gd.getNextBoolean();
		PlotWindow.noTicks = !gd.getNextBoolean();
		//data options
		PlotWindow.autoClose = gd.getNextBoolean();
		PlotWindow.listValues = gd.getNextBoolean();
		//profile plot options
		fixedScale = gd.getNextBoolean();
		ymin = gd.getNextNumber();
		ymax = gd.getNextNumber();
		//profile options
		Prefs.verticalProfile = gd.getNextBoolean();
		PlotWindow.interpolate = gd.getNextBoolean();
		Prefs.subPixelResolution = gd.getNextBoolean();
		if (!fixedScale && !wasFixedScale && (ymin!=0.0 || ymax!=0.0))
			fixedScale = true;
		if (!fixedScale) {
			ymin = 0.0;
			ymax = 0.0;
		} else if (ymin>ymax) {
			double tmp = ymin;
			ymin = ymax;
			ymax = tmp;
		}
		ProfilePlot.setMinAndMax(ymin, ymax);
		IJ.register(Profiler.class);
	}
		
}
