package ij.gui;
import ij.ImagePlus;

/** Plugins that generate "Live" profile plots (Profiler and ZAxisProfiler)
	displayed in PlotWindows implement this interface. */
public interface PlotMaker {

   /** Returns a profile plot. */
   public Plot getPlot();
   
   /** Returns the ImagePlus used to generate the profile plots. */
   public ImagePlus getSourceImage();

}

