package ij.plugin.filter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import ij.*;
import ij.process.*;
import ij.io.*;
import ij.gui.*;

/** Saves the current ROI outline to a file. RoiDecoder.java 
	has a description of the file format.
	@see ij.io.RoiDecoder
	@see ij.plugin.RoiReader
*/
public class RoiWriter implements PlugInFilter {
	ImagePlus imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+ROI_REQUIRED+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		try {
			saveRoi(imp);
		} catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null || msg.equals(""))
				msg = ""+e;
			IJ.error("ROI Writer", msg);
		}
	}

	public void saveRoi(ImagePlus imp) throws IOException{
		Roi roi = imp.getRoi();
		if (roi==null)
			throw new IllegalArgumentException("ROI required");
		String name;
		switch (roi.getType()) {
			case Roi.POLYGON: name="Polygon.roi"; break;
			case Roi.FREEROI: name="Freehand.roi"; break;
			case Roi.TRACED_ROI: name="TracedRoi.roi"; break;
			case Roi.OVAL: name="Oval.roi"; break;
			case Roi.LINE: name="Line.roi"; break;
			case Roi.POLYLINE: name="PolyLine.roi"; break;
			case Roi.FREELINE: name="FreeLine.roi"; break;
			case Roi.ANGLE: name="Angle.roi"; break;
			case Roi.COMPOSITE: name="Composite.roi"; break;
			case Roi.POINT: name="Point.roi"; break;
			default: name="Rectangle.roi"; break;
		}
		SaveDialog sd = new SaveDialog("Save Selection...", name, ".roi");
		name = sd.getFileName();
		if (name == null)
			return;
		String dir = sd.getDirectory();
		RoiEncoder re = new RoiEncoder(dir+name);
		re.write(roi);
		if (name.endsWith(".roi"))
			name = name.substring(0, name.length()-4);
		roi.setName(name);
	}
	
}
