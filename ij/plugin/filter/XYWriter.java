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

/** Saves the XY coordinates of the current ROI boundary. */
public class XYWriter implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+ROI_REQUIRED+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		try {
			saveXYCoordinates(imp);
		} catch (IllegalArgumentException e) {
			IJ.showMessage("XYWriter", e.getMessage());
		}
	}

	public void saveXYCoordinates(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null)
			throw new IllegalArgumentException("ROI required");
		if (!(roi instanceof PolygonRoi))
			throw new IllegalArgumentException("Irregular area or line selection required");
		
		SaveDialog sd = new SaveDialog("Save Coordinates as Text...", imp.getTitle(), ".txt");
		String name = sd.getFileName();
		if (name == null)
			return;
		String directory = sd.getDirectory();
		PrintWriter pw = null;
		try {
			FileOutputStream fos = new FileOutputStream(directory+name);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			pw = new PrintWriter(bos);
		}
		catch (IOException e) {
			IJ.showMessage("XYWriter", ""+e);
			return;
		}
		
		PolygonRoi p = (PolygonRoi)roi;
		int n = p.getNCoordinates();
		int[] x = p.getXCoordinates();
		int[] y = p.getYCoordinates();
		
		Calibration cal = imp.getCalibration();
		String ls = System.getProperty("line.separator");
		boolean scaled = cal.scaled();
		for (int i=0; i<n; i++) {
			if (scaled)
				pw.print(IJ.d2s(x[i]*cal.pixelWidth) + "\t" + IJ.d2s(y[i]*cal.pixelHeight) + ls);
			else
				pw.print(x[i] + "\t" + y[i] + ls);
		}
		pw.close();
	}

}
