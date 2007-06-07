package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.measure.*;
import ij.plugin.filter.RGBStackSplitter;

/** Implements the Image/Stacks/Reslice command. */
public class Slicer implements PlugIn {

	private static final String[] starts = {"Top", "Left", "Bottom", "Right"};
	private static String startAt = starts[0];
	private static boolean rotate;
	private static boolean flip;
	private double outputZSpacing = 1.0;
	private int outputSlices = 1;
	private ImageWindow win;
	private boolean noRoi;
	private boolean rgb;

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) {
			IJ.noImage();
			return;
		}
		int stackSize = imp.getStackSize();
		if (stackSize<2) {
			IJ.showMessage("Reslicer", "Stack required");
			return;
		}
		if (!showDialog(imp))
			return; 	
		IJ.showStatus("Reslice... (press esc to abort)");
		win = imp.getWindow();
		if (win!=null) win.running = true;
		long startTime = System.currentTimeMillis();
		ImagePlus imp2 = null;
		rgb = imp.getType()==ImagePlus.COLOR_RGB;
		imp2 = reslice(imp);
		if (imp2==null)
			return;
		imp2.setCalibration(imp.getCalibration());
		Calibration cal = imp2.getCalibration();
		cal.pixelDepth = outputZSpacing*cal.pixelWidth;
		imp2.show();
		if (noRoi)
			imp.killRoi();
		else
		   imp.draw();
		if (win!=null) win.running = false;
		IJ.showStatus(IJ.d2s(((System.currentTimeMillis()-startTime)/1000.0),2)+" seconds");
	}

	/*
	public ImagePlus resliceRGB(ImagePlus imp) {
		Roi roi = imp.getRoi();
		RGBStackSplitter splitter = new RGBStackSplitter();
		splitter.split(imp.getStack(), true);
		IJ.showStatus("Slicer: RGB split");
		ImagePlus red = new ImagePlus("Red", splitter.red);
		ImagePlus green = new ImagePlus("Green", splitter.green);
		ImagePlus blue = new ImagePlus("Blue", splitter.blue);
		red.setRoi(roi); green.setRoi(roi); blue.setRoi(roi);
		Calibration cal = imp.getCalibration();
		red.setCalibration(cal); green.setCalibration(cal); blue.setCalibration(cal);
		IJ.showStatus("Slicer: reslicing red");
		red = reslice(red);
		IJ.showStatus("Slicer: reslicing green");
		green = reslice(green);
		IJ.showStatus("Slicer: reslicing blue");
		blue = reslice(blue);
		int w = red.getWidth(), h = red.getHeight(), d = red.getStackSize();
		RGBStackMerge merge = new RGBStackMerge();
		IJ.showStatus("Slicer: RGB merge");
		ImageStack stack = merge.mergeStacks(w, h, d, red.getStack(), green.getStack(), blue.getStack(), true);
		return new ImagePlus("Reslice of  "+imp.getShortTitle(), stack);
	}
	*/

	public ImagePlus reslice(ImagePlus imp) {
		Roi roi = imp.getRoi();
		int roiType = roi!=null?roi.getType():0;
		if (roi==null || roiType==Roi.RECTANGLE || roiType==Roi.LINE)
			return resliceRectOrLine(imp);
		else if (roiType==Roi.POLYLINE || roiType==Roi.FREELINE) {
			String status = imp.getStack().isVirtual()?"":null;
			 ImageProcessor ip2 = getSlice(imp, 0.0, 0.0, 0.0, 0.0, status);
			 return new ImagePlus("Reslice of  "+imp.getShortTitle(), ip2);
		} else {
			IJ.showMessage("Reslice...", "Line or rectangular selection required");
			return null;
		}
	}

   boolean showDialog(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		String units = cal.getUnits();
		if (cal.pixelWidth==0.0)
			cal.pixelWidth = 1.0;
		double outputSpacing = cal.pixelDepth;
		Roi roi = imp.getRoi();
		boolean line = roi!=null && roi.getType()==Roi.LINE;
		GenericDialog gd = new GenericDialog("Reslice");
		gd.addNumericField("Input Z Spacing ("+units+"):", cal.pixelDepth, 3);
		gd.addNumericField("Output Z Spacing ("+units+"):", outputSpacing, 3);
		if (line)
			gd.addNumericField("Slice Count:", outputSlices, 0);
		else
		   gd.addChoice("Start At:", starts, startAt);
		gd.addCheckbox("Flip Vertically", flip);
		gd.addCheckbox("Rotate 90 Degrees", rotate);
		gd.showDialog();
		if(gd.wasCanceled())
			return false;
		cal.pixelDepth = gd.getNextNumber();
		if (cal.pixelDepth==0.0) cal.pixelDepth = 1.0;
		outputZSpacing = gd.getNextNumber()/cal.pixelWidth;
		if (line)
			outputSlices = (int)gd.getNextNumber();
		else
			startAt = gd.getNextChoice();
		flip = gd.getNextBoolean();
		rotate = gd.getNextBoolean();
		return true;
	}

	ImagePlus resliceRectOrLine(ImagePlus imp) {
		double x1 = 0.0;
		double y1 = 0.0;
		double x2 = 0.0;
		double y2 = 0.0;
		double xInc = 0.0;
		double yInc = 0.0;
		noRoi = false;

		Roi roi = imp.getRoi();
		if (roi==null) {
			noRoi = true;
			imp.setRoi(0, 0, imp.getWidth(), imp.getHeight());
			roi = imp.getRoi();
		}
		if (roi.getType()==Roi.RECTANGLE) {
			Rectangle r = roi.getBoundingRect();
			if (startAt.equals(starts[0])) { // top
				x1 = r.x;
				y1 = r.y;
				x2 = r.x + r.width;
				y2 = r.y;
				xInc = 0.0;
				yInc = outputZSpacing;
				outputSlices =	(int)(r.height/outputZSpacing); 	
		   } else if (startAt.equals(starts[1])) { // left
				x1 = r.x;
				y1 = r.y;
				x2 = r.x;
				y2 = r.y + r.height;
				xInc = outputZSpacing;
				yInc = 0.0;
				outputSlices =	(int)(r.width/outputZSpacing);		
			} else if (startAt.equals(starts[2])) { // bottom
				x1 = r.x;
				y1 = r.y + r.height;
				x2 = r.x + r.width;
				y2 = r.y + r.height;
				xInc = 0.0;
				yInc = -outputZSpacing;
				outputSlices =	(int)(r.height/outputZSpacing); 	
			} else if (startAt.equals(starts[3])) { // right
				x1 = r.x + r.width;
				y1 = r.y;
				x2 = r.x + r.width;
				y2 = r.y + r.height;
				xInc = -outputZSpacing;
				yInc = 0.0;
				outputSlices =	(int)(r.width/outputZSpacing);		
			}
		} else if (roi.getType()==Roi.LINE) {
				Line line = (Line)roi;
				x1 = line.x1;
				y1 = line.y1;
				x2 = line.x2;
				y2 = line.y2;
				double dx = x2 - x1;
				double dy = y2 - y1;
				double nrm = Math.sqrt(dx*dx + dy*dy)/outputZSpacing;
				xInc = -(dy/nrm);
				yInc = (dx/nrm);
	   } else
			return null;

		if (outputSlices==0) {
		   IJ.showMessage("Reslicer", "Output Z spacing ("+IJ.d2s(outputZSpacing,0)+" pixels) is too large.");
		   return null;
		}
		ImageStack stack=null;
		boolean virtualStack = imp.getStack().isVirtual();
		String status = null;
		for (int i=0; i<outputSlices; i++)	{
			if (virtualStack)
				status = outputSlices>1?(i+1)+"/"+outputSlices+", ":"";
			ImageProcessor ip = getSlice(imp, x1, y1, x2, y2, status);
			drawLine(x1, y1, x2, y2, imp);
			if (stack==null)
				stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice(null, ip);
			x1 += xInc;
			x2 += xInc;
			y1 += yInc;
			y2 += yInc;
			if (win!=null && !win.running)
				{IJ.beep(); imp.draw(); return null;}
		}
		
		return new ImagePlus("Reslice of  "+imp.getShortTitle(), stack);
	}

   ImageProcessor getSlice(ImagePlus imp, double x1, double y1, double x2, double y2, String status) {
		Roi roi = imp.getRoi();
		int roiType = roi!=null?roi.getType():0;
		ImageStack stack = imp.getStack();
		int stackSize = stack.getSize();
		ImageProcessor ip,ip2=null;
		float[] line = null;
		for (int i=0; i<stackSize; i++) {
			ip = stack.getProcessor(flip?stackSize-i:i+1);
			if (roiType==Roi.POLYLINE || roiType==Roi.FREELINE)
				line = getIrregularProfile(roi, ip);
			 else
				line = getLine(ip, x1, y1, x2, y2, line);
			if (rotate) {
				if (i==0) ip2 = ip.createProcessor(stackSize, line.length);
				putColumn(ip2, i, 0, line, line.length);
			} else {
				if (i==0) ip2 = ip.createProcessor(line.length, stackSize);
				putRow(ip2, 0, i, line, line.length);
			}
			if (status!=null) IJ.showStatus("Slicing: "+status +i+"/"+stackSize);
		}
		Calibration cal = imp.getCalibration();
		double zSpacing = cal.pixelDepth/cal.pixelWidth;
		if (zSpacing!=1.0) {
			ip2.setInterpolate(true);
			if (rotate)
				ip2 = ip2.resize((int)(stackSize*zSpacing), line.length);
			else
				ip2 = ip2.resize(line.length, (int)(stackSize*zSpacing));
		}	
		return ip2;
	}

	public void putRow(ImageProcessor ip, int x, int y, float[] data, int length) {
		if (rgb) {
			for (int i=0; i<length; i++)
				ip.putPixel(x++, y, Float.floatToIntBits(data[i]));
		} else {
			for (int i=0; i<length; i++)
				ip.putPixelValue(x++, y, data[i]);
		}
	}

	public void putColumn(ImageProcessor ip, int x, int y, float[] data, int length) {
		if (rgb) {
			for (int i=0; i<length; i++)
				ip.putPixel(x, y++, Float.floatToIntBits(data[i]));
		} else {
			for (int i=0; i<length; i++)
				ip.putPixelValue(x, y++, data[i]);
		}
	}

	float[] getIrregularProfile(Roi roi, ImageProcessor ip) {
		int n = ((PolygonRoi)roi).getNCoordinates();
		int[] x = ((PolygonRoi)roi).getXCoordinates();
		int[] y = ((PolygonRoi)roi).getYCoordinates();
		Rectangle r = roi.getBoundingRect();
		int xbase = r.x;
		int ybase = r.y;
		double length = 0.0;
		double segmentLength;
		int xdelta, ydelta, iLength;
		double[] segmentLengths = new double[n];
		int[] dx = new int[n];
		int[] dy = new int[n];
		for (int i=0; i<(n-1); i++) {
			xdelta = x[i+1] - x[i];
			ydelta = y[i+1] - y[i];
			segmentLength = Math.sqrt(xdelta*xdelta+ydelta*ydelta);
			length += segmentLength;
			segmentLengths[i] = segmentLength;
			dx[i] = xdelta;
			dy[i] = ydelta;
		}
		float[] values = new float[(int)length];
		double leftOver = 1.0;
		double distance = 0.0;
		int index;
		double oldx=xbase, oldy=ybase;
		for (int i=0; i<n; i++) {
			double len = segmentLengths[i];
			if (len==0.0)
				continue;
			double xinc = dx[i]/len;
			double yinc = dy[i]/len;
			double start = 1.0-leftOver;
			double rx = xbase+x[i]+start*xinc;
			double ry = ybase+y[i]+start*yinc;
			double len2 = len - start;
			int n2 = (int)len2;
			//double d=0;;
			//IJ.write("new segment: "+IJ.d2s(xinc)+" "+IJ.d2s(yinc)+" "+IJ.d2s(len)+" "+IJ.d2s(len2)+" "+IJ.d2s(n2)+" "+IJ.d2s(leftOver));
			for (int j=0; j<=n2; j++) {
				index = (int)distance+j;
				if (index<values.length) {
					if (rgb) {
						int rgbPixel = ((ColorProcessor)ip).getInterpolatedRGBPixel(rx, ry);
						values[index] = Float.intBitsToFloat(rgbPixel&0xffffff);
					} else
						values[index] = (float)ip.getInterpolatedValue(rx, ry);
				}
				//d = Math.sqrt((rx-oldx)*(rx-oldx)+(ry-oldy)*(ry-oldy));
				//IJ.write(IJ.d2s(rx)+"    "+IJ.d2s(ry)+"	 "+IJ.d2s(d));
				//oldx = rx; oldy = ry;
				rx += xinc;
				ry += yinc;
			}
			distance += len;
			leftOver = len2 - n2;
		}

		return values;

	}

	private float[] getLine(ImageProcessor ip, double x1, double y1, double x2, double y2, float[] data) {
		double dx = x2-x1;
		double dy = y2-y1;
		int n = (int)Math.round(Math.sqrt(dx*dx + dy*dy));
		if (data==null)
			data = new float[n];
		double xinc = dx/n;
		double yinc = dy/n;
		double rx = x1;
		double ry = y1;
		for (int i=0; i<n; i++) {
			if (rgb) {
				int rgbPixel = ((ColorProcessor)ip).getInterpolatedRGBPixel(rx, ry);
				data[i] = Float.intBitsToFloat(rgbPixel&0xffffff);
			} else
				data[i] = (float)ip.getInterpolatedValue(rx, ry);
			rx += xinc;
			ry += yinc;
		}
		return data;
	}
	
	void drawLine(double x1, double y1, double x2, double y2, ImagePlus imp) {
		ImageWindow win = imp.getWindow();
		if (win==null)
			return;
		ImageCanvas ic = win.getCanvas();
		Graphics g = ic.getGraphics();
		g.setColor(Roi.getColor());
		g.setXORMode(Color.black);
		g.drawLine(ic.screenX((int)(x1+0.5)), ic.screenY((int)(y1+0.5)), ic.screenX((int)(x2+0.5)), ic.screenY((int)(y2+0.5)));
	}

}
