package ij.plugin.filter;

import java.awt.*;
import java.awt.image.*;
import java.util.Vector;
import java.io.*;
import ij.*;
import ij.process.*;
import ij.io.*;
import ij.gui.*;
import ij.measure.*;
import java.awt.geom.*;


/** Saves the XY coordinates of the current ROI boundary. */
public class XYWriter implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+ROI_REQUIRED+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		saveXYCoordinates(imp);
	}

	public void saveXYCoordinates(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null)
			throw new IllegalArgumentException("ROI required");
		SaveDialog sd = new SaveDialog("Save Coordinates as Text...", imp.getTitle(), ".txt");
		String name = sd.getFileName();
		if (name == null)
			return;
		String directory = sd.getDirectory();
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(directory+name)));
		}
		catch (IOException e) {
			IJ.error("XYWriter", "Unable to save coordinates:\n   "+e.getMessage());
			return;
		}
		
		Calibration cal = imp.getCalibration();
		String ls = System.getProperty("line.separator");
		if (roi.subPixelResolution()) {
			FloatPolygon p = roi.getFloatPolygon();
			for (int i=0; i<p.npoints; i++)
				pw.print(IJ.d2s((p.xpoints[i])*cal.pixelWidth,4) + "\t" + IJ.d2s((p.ypoints[i])*cal.pixelHeight,4) + ls);
		} else {
			Polygon p = roi.getPolygon();
			boolean scaled = cal.scaled();
			for (int i=0; i<p.npoints; i++) {
				if (scaled)
					pw.print(IJ.d2s((p.xpoints[i])*cal.pixelWidth,4) + "\t" + IJ.d2s((p.ypoints[i])*cal.pixelHeight,4) + ls);
				else
					pw.print((p.xpoints[i]) + "\t" + (p.ypoints[i]) + ls);
			}
		}
		pw.close();
	}

}
