package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import java.awt.*;

/** This plugin implements the Edit/Selection/Straighten command. */
public class Straightener implements PlugIn {
	static boolean processStack;

 	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		boolean rotatedRectangle = roi!=null && (roi instanceof RotatedRectRoi);
		if (roi==null || !(roi.isLine()||rotatedRectangle)) {
			IJ.error("Straightener", "Line, or rotated rectangle, selection required");
			return;
		}
		if (rotatedRectangle) {
			new Duplicator().run("");
			return;
		}
		int width = (int)Math.round(roi.getStrokeWidth());
		int originalWidth = width;
		boolean isMacro = IJ.macroRunning() && Macro.getOptions()!=null;
		int stackSize = imp.getStackSize();
		if (stackSize==1) processStack = false;
		String newTitle = WindowManager.getUniqueName(imp.getTitle());
		if (width<=1 || isMacro || stackSize>1) {
			if (width<=1) width = 20;
			GenericDialog gd = new GenericDialog("Straightener");
			gd.addStringField("Title:", newTitle, 15);
			gd.addNumericField("Line Width:", width, 0, 3, "pixels");
			if (stackSize>1)
				gd.addCheckbox("Process Entire Stack", processStack);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			newTitle = gd.getNextString();
			width = (int)gd.getNextNumber();
			Line.setWidth(width);
			if (stackSize>1)
				processStack = gd.getNextBoolean();
		}
		roi = (Roi)imp.getRoi().clone();
		int type = roi.getType();
		if (type==Roi.FREELINE)
			IJ.run(imp, "Fit Spline", "");
		ImageProcessor ip2 = null;
		ImagePlus imp2 = null;
		if (processStack) {
			ImageStack stack2 = straightenStack(imp, roi, width);
			imp2 = new ImagePlus(newTitle, stack2);
		} else {
			ip2 = straighten(imp, roi, width);
			imp2 = new ImagePlus(newTitle, ip2);
		}
		if (imp2==null)
			return;
		Calibration cal = imp.getCalibration();
		if (cal.pixelWidth==cal.pixelHeight)
			imp2.setCalibration(cal);
		imp2.show();
		if (isMacro) Line.setWidth(originalWidth);
	}
	
	public ImageProcessor straighten(ImagePlus imp, Roi roi, int width) {
		ImageProcessor ip2;
		if (imp.getBitDepth()==24 && roi.getType()!=Roi.LINE)
			ip2 = straightenRGB(imp, width);
		else if (imp.isComposite() && ((CompositeImage)imp).getMode()==IJ.COMPOSITE) {
			if (roi.getType()==Roi.LINE)
				ip2 = rotateCompositeLine(imp, width);
			else
				ip2 = straightenComposite(imp, width);
		} else if (roi.getType()==Roi.LINE)
			ip2 = rotateLine(imp, width);
		else
			ip2 = straightenLine(imp, width);
		return ip2;
	}
		
	public ImageStack straightenStack(ImagePlus imp, Roi roi, int width) {
		int current = imp.getCurrentSlice();
		int n = imp.getStackSize();
		ImageStack stack2 = null;
		for (int i=1; i<=n; i++) {
			IJ.showProgress(i, n);
			imp.setSlice(i);
			ImageProcessor ip2 = straighten(imp, roi, width);
			if (stack2==null)
				stack2 = new ImageStack(ip2.getWidth(), ip2.getHeight());
			stack2.addSlice(null, ip2);
		}
		imp.setSlice(current);
		return stack2;
	}

	public ImageProcessor straightenLine(ImagePlus imp, int width) {
		Roi tempRoi = imp.getRoi();
		if (!(tempRoi instanceof PolygonRoi))
			return null;
		PolygonRoi roi = (PolygonRoi)tempRoi;
		if (roi==null)
			return null;
		if (roi.getState()==Roi.CONSTRUCTING)
			roi.exitConstructingMode();
		if (roi.isSplineFit())
			roi.removeSplineFit();
		int type = roi.getType();
		int n = roi.getNCoordinates();
		double len = roi.getLength();
		roi.fitSplineForStraightening();
		if (roi.getNCoordinates()<2)
			return null;
		FloatPolygon p = roi.getFloatPolygon();
		n = p.npoints;
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor ip2 = new FloatProcessor(n, width);
		//ImageProcessor distances = null;
		//if (IJ.debugMode)  distances = new FloatProcessor(n, 1);
		float[] pixels = (float[])ip2.getPixels();
		double x1, y1;
		double x2=p.xpoints[0]-(p.xpoints[1]-p.xpoints[0]);
		double y2=p.ypoints[0]-(p.ypoints[1]-p.ypoints[0]);
		if (width==1)
			ip2.putPixelValue(0, 0, ip.getInterpolatedValue(x2, y2));
		for (int i=0; i<n; i++) {
			if (!processStack&&(i%10)==0) IJ.showProgress(i, n);
			x1=x2; y1=y2;
			x2=p.xpoints[i]; y2=p.ypoints[i];
			//if (distances!=null) distances.putPixelValue(i, 0, (float)Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)));
			if (width==1) {
				ip2.putPixelValue(i, 0, ip.getInterpolatedValue(x2, y2));
				continue;
			}
			double dx = x2-x1;
			double dy = y1-y2;
            double length = (float)Math.sqrt(dx*dx+dy*dy);
            dx /= length;
            dy /= length;
			//IJ.log(i+"  "+x2+"  "+dy+"  "+(dy*width/2f)+"   "+y2+"  "+dx+"   "+(dx*width/2f));
			double x = x2-dy*width/2.0;
			double y = y2-dx*width/2.0;
			int j = 0;
			int n2 = width;
			do {
				ip2.putPixelValue(i, j++, ip.getInterpolatedValue(x, y));;
				//ip.drawDot((int)x, (int)y);
				x += dy;
				y += dx;
			} while (--n2>0);
		}
		if (!processStack) IJ.showProgress(n, n);
		if (type==Roi.FREELINE)
			roi.removeSplineFit();
		else
			imp.draw();
		if (imp.getBitDepth()!=24) {
			ip2.setColorModel(ip.getColorModel());
			ip2.resetMinAndMax();
		}
		return ip2;
	}
	
	public ImageProcessor rotateLine(ImagePlus imp, int width) {
		Roi roi = imp.getRoi();
		if (roi==null || roi.getType()!=Roi.LINE)
			throw new IllegalArgumentException("Straight line selection expected");
		Polygon p = ((Line)roi).getPoints();
		imp.setRoi(new PolygonRoi(p.xpoints, p.ypoints, 2, Roi.POLYLINE));
		ImageProcessor ip2 = imp.getBitDepth()==24?straightenRGB(imp, width):straightenLine(imp, width);
		imp.setRoi(roi);
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
        ImageProcessor red = straightenLine(imp2, width);
        if (red==null) return null;
        imp2 = new ImagePlus("green", new ByteProcessor(w, h, g, null));
        imp2.setRoi((Roi)imp.getRoi().clone());
        ImageProcessor green = straightenLine(imp2, width);
        if (green==null) return null;
        imp2 = new ImagePlus("blue", new ByteProcessor(w, h, b, null));
        imp2.setRoi((Roi)imp.getRoi().clone());
        ImageProcessor blue = straightenLine(imp2, width);
        if (blue==null) return null;
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
	
	ImageProcessor rotateCompositeLine(ImagePlus imp, int width) {
		Image img = imp.getImage();
		ImagePlus imp2 = new ImagePlus("temp", new ColorProcessor(img));
		imp2.setRoi(imp.getRoi());
		ImageProcessor ip2 = rotateLine(imp2, width);
		return ip2;
	}

}
