package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;

/** This plugin implements the Edit/Selection/Straighten command. */
public class Straightener implements PlugIn {

 	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isLine() || roi.getType()==Roi.LINE) {
			IJ.error("Straightener", "Segmented line of freehand line selection required");
			return;
		}
		int width = Line.getWidth();
		if (width==1) {
			GenericDialog gd = new GenericDialog("Straightener");
			gd.addNumericField("Line Width:", 20, 0, 3, "pixels");
			gd.showDialog();
			if (gd.wasCanceled()) return;
			width = (int)gd.getNextNumber();
			Line.setWidth(width);
		}
		Straighten(imp, width);
	}
	
	void Straighten(ImagePlus imp, int width) {
		PolygonRoi roi = (PolygonRoi)imp.getRoi();
		boolean isSpline = roi.isSplineFit();
		int type = roi.getType();
		roi.fitSplineForStraightening();
		FloatPolygon p = roi.getFloatPolygon();
		int n = p.npoints;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor ip2 = new FloatProcessor(n, width);
		ImageProcessor distances = null;
		if (IJ.debugMode)  distances = new FloatProcessor(n-1, 1);
		float[] pixels = (float[])ip2.getPixels();
		float x1, y1, x2=p.xpoints[0], y2=p.ypoints[0];
		if (width==1)
			ip2.putPixelValue(0, 0, ip.getInterpolatedValue(x2, y2));
		for (int i=1; i<n; i++) {
			x1=x2; y1=y2;
			x2=p.xpoints[i]; y2=p.ypoints[i];
			if (distances!=null) distances.putPixelValue(i-1, 0, (float)Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)));
			if (width==1) {
				ip2.putPixelValue(i, 0, ip.getInterpolatedValue(x2, y2));
				continue;
			}
			float dx = x2-x1;
			float dy = y1-y2;
			//IJ.log(i+"  "+x2+"  "+dy+"  "+(dy*width/2f)+"   "+y2+"  "+dx+"   "+(dx*width/2f));
			float x = x2-dy*width/2f;
			float y = y2-dx*width/2f;
			int j = 0;
			int n2 = width;
			do {
				ip2.putPixelValue(i, j++, ip.getInterpolatedValue(x, y));;
				//ip.drawDot((int)x, (int)y);
				x += dy;
				y += dx;
			} while (--n2>0);
		}
		imp.updateAndDraw();
		if (!isSpline) {
			if (type==Roi.FREELINE)
				roi.removeSplineFit();
			else
				imp.draw();
		}
		ip2.setColorModel(ip.getColorModel());
		ip2.resetMinAndMax();
		(new ImagePlus(WindowManager.getUniqueName(imp.getTitle()), ip2)).show();
		if (distances!=null) {
			distances.resetMinAndMax();
			(new ImagePlus("Distances", distances)).show();
		}
	}

}