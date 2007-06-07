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
	final int polygon=0, rect=1, oval=2, line=3, freeline=4, polyline=5, noRoi=6, freehand=7, 
		traced=8, angle=9;
	ImagePlus imp;
	byte[] data;

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
			IJ.showMessage("ROI Writer", msg);
		}
	}

	public void saveRoi(ImagePlus imp) throws IOException{
		Roi roi = imp.getRoi();
		if (roi==null)
			throw new IllegalArgumentException("ROI required");
		int roiType = roi.getType();
		//if (roiType>=Roi.LINE)
		//	throw new IllegalArgumentException("Area selection required");
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
		} else if (roiType==Roi.LINE) {
			type = line;
			name = "Line.roi";
		} else if (roiType==Roi.POLYLINE) {
			type = polyline;
			name = "PolyLine.roi";
		} else if (roiType==Roi.FREELINE) {
			type = freeline;
			name = "FreeLine.roi";
		} else if (roiType==Roi.ANGLE) {
			type = angle;
			name = "Angle.roi";
		} else if (roiType==Roi.COMPOSITE) {
			type = rect; // shape array size (36-39) will be >0 to indicate composite type
			name ="Composite.roi";
		} else {
			type = rect;
			name = "Rectangle.roi";
		}
		
		SaveDialog sd = new SaveDialog("Save Selection...", name, ".roi");
		name = sd.getFileName();
		if (name == null)
			return;
		String dir = sd.getDirectory();
		FileOutputStream f = new FileOutputStream(dir+name);
		
		if (roiType==Roi.COMPOSITE) {
			saveShapeRoi(roi, name, type, f);
			return;
		}

		int n=0;
		int[] x=null,y=null;
		if (roi instanceof PolygonRoi) {
			PolygonRoi p = (PolygonRoi)roi;
			n = p.getNCoordinates();
			x = p.getXCoordinates();
			y = p.getYCoordinates();
		}
		data = new byte[HEADER_SIZE+n*4];
		
		Rectangle r = roi.getBounds();
		
		data[0]=73; data[1]=111; data[2]=117; data[3]=116; // "Iout"
		putShort(4, VERSION);
		data[6] = (byte)type;
		putShort(8, r.y);			//top
		putShort(10, r.x);			//left
		putShort(12, r.y+r.height);	//bottom
		putShort(14, r.x+r.width);	//right
		putShort(16, n);
		
		if (roi instanceof Line) {
			Line l = (Line)roi;
			putFloat(18, l.x1);
			putFloat(22, l.y1);
			putFloat(26, l.x2);
			putFloat(30, l.y2);
		}

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
		if (name.endsWith(".roi"))
			name = name.substring(0, name.length()-4);
		roi.setName(name);
	}

	void saveShapeRoi(Roi roi, String name, int type, FileOutputStream f) throws IOException {
		float[] shapeArray = ((ShapeRoi)roi).getShapeAsArray();
		if (shapeArray==null) return;
		BufferedOutputStream bout = new BufferedOutputStream(f);
		Rectangle r = roi.getBounds();
		data  = new byte[HEADER_SIZE + shapeArray.length*4];
		data[0]=73; data[1]=111; data[2]=117; data[3]=116; // "Iout"
		putShort(4, VERSION);
		data[6] = (byte)type;
		putShort(8, r.y);			//top
		putShort(10, r.x);			//left
		putShort(12, r.y+r.height);	//bottom
		putShort(14, r.x+r.width);	//right
		//putShort(16, n);
		putInt(36, shapeArray.length); // non-zero segment count indicate composite type

		// handle the actual data: data are stored segment-wise, i.e.,
		// the type of the segment followed by 0-6 control point coordinates.
		int base = 64;
		for (int i=0; i<shapeArray.length; i++) {
			putFloat(base, shapeArray[i]);
			base += 4;
		}
		bout.write(data,0,data.length);
		bout.flush();
		bout.close();
		if (name.endsWith(".roi"))
			name = name.substring(0, name.length()-4);
		roi.setName(name);
	}

    void putShort(int base, int v) {
		data[base] = (byte)(v>>>8);
		data[base+1] = (byte)v;
    }

	void putFloat(int base, float v) {
		int tmp = Float.floatToIntBits(v);
		data[base]   = (byte)(tmp>>24);
		data[base+1] = (byte)(tmp>>16);
		data[base+2] = (byte)(tmp>>8);
		data[base+3] = (byte)tmp;
	}

	void putInt(int base, int i) {
		data[base]   = (byte)(i>>24);
		data[base+1] = (byte)(i>>16);
		data[base+2] = (byte)(i>>8);
		data[base+3] = (byte)i;
	}
	
}
