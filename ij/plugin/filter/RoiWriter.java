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

	static final int HEADER_SIZE = 64;
	static final int VERSION = 217;
	final int polygon=0, rect=1, oval=2, line=3,freeLine=4, segLine=5, noRoi=6,freehand=7, traced=8;
	ImagePlus imp;
	byte[] data;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+ROI_REQUIRED+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		try {
			saveRoi(imp);
		} catch (Exception e) {
			String msg = e.getMessage();
			if (msg==null || msg.equals(""))
				msg = ""+e;
			IJ.showMessage("ROI Writer", msg);
		}
	}

	public void saveRoi(ImagePlus imp) throws Exception{
		Roi roi = imp.getRoi();
		if (roi==null)
			throw new IllegalArgumentException("ROI required");
		int roiType = roi.getType();
		if (roiType>=Roi.LINE)
			throw new IllegalArgumentException("Area selection required");
		int type;
		String name;
		if (roiType==Roi.POLYGON) {
			type = polygon;
			name = "Polygon.roi";
		} else if (roiType==Roi.FREEROI) {
			type = freehand;
			name = "Freehand.roi";
		} else if (roiType==Roi.TRACED_ROI) {
			type = traced;
			name = "TracedRoi.roi";
		} else if (roiType==Roi.OVAL) {
			type = oval;
			name = "Oval.roi";
		} else {
			type = rect;
			name = "Rectangle.roi";
		}
		
		SaveDialog sd = new SaveDialog("Save ROI...", name, ".roi");
		name = sd.getFileName();
		if (name == null)
			return;
		String dir = sd.getDirectory();
		FileOutputStream f = new FileOutputStream(dir+name);
		
		int n=0;
		int[] x=null,y=null;
		if (roi instanceof PolygonRoi) {
			PolygonRoi p = (PolygonRoi)roi;
			n = p.getNCoordinates();
			x = p.getXCoordinates();
			y = p.getYCoordinates();
		}
		data = new byte[HEADER_SIZE+n*4];
		
		Rectangle r = roi.getBoundingRect();
		
		data[0]=73; data[1]=111; data[2]=117; data[3]=116; // "Iout"
		putShort(4, VERSION);
		data[6] = (byte)type;
		putShort(8, r.y);			//top
		putShort(10, r.x);			//left
		putShort(12, r.y+r.height);	//bottom
		putShort(14, r.x+r.width);	//right
		putShort(16, n);

		if (n>0) {
			int base1 = 64;
			int base2 = base1+2*n;
			for (int i=0; i<n; i++) {
				putShort(base1+i*2, x[i]);
				putShort(base2+i*2, y[i]);
			}
		}
		
		f.write(data);
		f.close();
	}

    void putShort(int base, int v) {
		data[base] = (byte)((v>>>8)&255);
		data[base+1] = (byte)(v&255);
    }

}
