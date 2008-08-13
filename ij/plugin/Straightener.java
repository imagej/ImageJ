package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import java.awt.*;

/** This plugin implements the Edit/Selection/Straighten command. */
public class Straightener implements PlugIn {

 	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isLine()) {
			IJ.error("Straightener", "Line selection required");
			return;
		}
		int width = Line.getWidth();
		int originalWidth = width;
		boolean isMacro = IJ.macroRunning();
		if (width==1 || isMacro) {
			GenericDialog gd = new GenericDialog("Straightener");
			gd.addNumericField("Line Width:", 20, 0, 3, "pixels");
			gd.showDialog();
			if (gd.wasCanceled()) return;
			width = (int)gd.getNextNumber();
			Line.setWidth(width);
		}
		roi = (Roi)imp.getRoi().clone();
		int type = roi.getType();
		if (type==Roi.FREELINE)
			IJ.run(imp, "Fit Spline", "");
		ImageProcessor ip2;
		if (imp.getBitDepth()==24)
			ip2 = straightenRGB(imp, width);
		else if (imp.isComposite() && ((CompositeImage)imp).getMode()==CompositeImage.COMPOSITE)
			ip2 = straightenComposite(imp, width);
		else if (roi.getType()==Roi.LINE)
			ip2 = straightenStraightLine(imp, width);
		else
			ip2 = straighten(imp, width);
		(new ImagePlus(WindowManager.getUniqueName(imp.getTitle()), ip2)).show();
		imp.setRoi(roi);
		if (type==Roi.POLYLINE&& !((PolygonRoi)roi).isSplineFit())
			((PolygonRoi)roi).fitSpline();
		if (isMacro) Line.setWidth(originalWidth);
	}
	
	public ImageProcessor straighten(ImagePlus imp, int width) {
		PolygonRoi roi = (PolygonRoi)imp.getRoi();
		boolean isSpline = roi.isSplineFit();
		int type = roi.getType();
		roi.fitSplineForStraightening();
		FloatPolygon p = roi.getFloatPolygon();
		int n = p.npoints;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor ip2 = new FloatProcessor(n, width);
		ImageProcessor distances = null;
		if (IJ.debugMode)  distances = new FloatProcessor(n, 1);
		float[] pixels = (float[])ip2.getPixels();
		float x1, y1;
		float x2=p.xpoints[0]-(p.xpoints[1]-p.xpoints[0]);
		float y2=p.ypoints[0]-(p.ypoints[1]-p.ypoints[0]);
		if (width==1)
			ip2.putPixelValue(0, 0, ip.getInterpolatedValue(x2, y2));
		for (int i=0; i<n; i++) {
			x1=x2; y1=y2;
			x2=p.xpoints[i]; y2=p.ypoints[i];
			if (distances!=null) distances.putPixelValue(i, 0, (float)Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)));
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
		if (imp.getBitDepth()!=24) {
			ip2.setColorModel(ip.getColorModel());
			ip2.resetMinAndMax();
		}
		if (distances!=null) {
			distances.resetMinAndMax();
			(new ImagePlus("Distances", distances)).show();
		}
		return ip2;
	}
	
	public ImageProcessor straightenStraightLine(ImagePlus imp, int width) {
		Line.setWidth(1);
		Polygon p = imp.getRoi().getPolygon();
		Line.setWidth(width);
		imp.setRoi(new PolygonRoi(p.xpoints, p.ypoints, 2, Roi.POLYLINE));
		ImageProcessor ip2 = straighten(imp, width);
		imp.setRoi(new Line(p.xpoints[0], p.ypoints[0], p.xpoints[1], p.ypoints[1]));
		return ip2;
	}
	
	ImageProcessor straightenRGB(ImagePlus imp, int width) {
		int w=imp.getWidth(), h=imp.getHeight();
		int size = w*h;
		byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
		ColorProcessor cp = (ColorProcessor)imp.getProcessor();
		cp.getRGB(r, g, b);
        ImagePlus imp2 = new ImagePlus("red", new ByteProcessor(w, h, r, null));
        imp2.setRoi((Roi)imp.getRoi().clone());
        ImageProcessor red = straighten(imp2, width);
        imp2 = new ImagePlus("green", new ByteProcessor(w, h, g, null));
        imp2.setRoi((Roi)imp.getRoi().clone());
        ImageProcessor green = straighten(imp2, width);
        imp2 = new ImagePlus("blue", new ByteProcessor(w, h, b, null));
        imp2.setRoi((Roi)imp.getRoi().clone());
        ImageProcessor blue = straighten(imp2, width);
        ColorProcessor cp2 = new ColorProcessor(red.getWidth(), red.getHeight());
        red = red.convertToByte(false);
        green = green.convertToByte(false);
        blue = blue.convertToByte(false);
        cp2.setRGB((byte[])red.getPixels(), (byte[])green.getPixels(), (byte[])blue.getPixels());
        imp.setRoi(imp2.getRoi());
        return cp2;
 	}
 	
	ImageProcessor straightenComposite(ImagePlus imp, int width) {
		Image img = imp.getImage();
		ImagePlus imp2 = new ImagePlus("temp", new ColorProcessor(img));
		imp2.setRoi(imp.getRoi());
		ImageProcessor ip2 = straightenRGB(imp2, width);
        imp.setRoi(imp2.getRoi());
        return ip2;
	}

}