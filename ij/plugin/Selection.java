package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.frame.*;
import ij.macro.Interpreter;
import ij.plugin.filter.*;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Vector;


/** This plugin implements the commands in the Edit/Selection submenu. */
public class Selection implements PlugIn, Measurements {
	private ImagePlus imp;
	private float[] kernel = {1f, 1f, 1f, 1f, 1f};
	private float[] kernel3 = {1f, 1f, 1f};
	private static int bandSize = 15; // pixels
	private static Color linec, fillc;
	private static int lineWidth = 1;
	private static boolean smooth;
	private static boolean adjust;

	

	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (arg.equals("add"))
			{addToRoiManager(imp); return;}
		if (imp==null)
			{IJ.noImage(); return;}
		if (arg.equals("all"))
			imp.setRoi(0,0,imp.getWidth(),imp.getHeight());
		else if (arg.equals("none"))
			imp.deleteRoi();
		else if (arg.equals("restore"))
			imp.restoreRoi();
		else if (arg.equals("spline"))
			fitSpline();
		else if (arg.equals("interpolate"))
			interpolate();
		else if (arg.equals("circle"))
			fitCircle(imp);
		else if (arg.equals("ellipse"))
			createEllipse(imp);
		else if (arg.equals("hull"))
			convexHull(imp);
		else if (arg.equals("mask"))
			createMask(imp);		
		else if (arg.equals("from"))
			createSelectionFromMask(imp);		
		else if (arg.equals("inverse"))
			invert(imp); 
		else if (arg.equals("toarea"))
			lineToArea(imp); 
		else if (arg.equals("toline"))
			areaToLine(imp); 
		else if (arg.equals("properties"))
			{setProperties("Properties ", imp.getRoi()); imp.draw();}
		else if (arg.equals("band"))
			makeBand(imp);
		else if (arg.equals("tobox"))
			toBoundingBox(imp); 
		else if (arg.equals("rotate"))
			rotate(imp); 
		else if (arg.equals("enlarge"))
			enlarge(imp); 
	}
	
	private void rotate(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (IJ.macroRunning()) {
			String options = Macro.getOptions();
			if (options!=null && (options.indexOf("grid=")!=-1||options.indexOf("interpolat")!=-1)) {
				IJ.run("Rotate... ", options); // run Image>Transform>Rotate
				return;
			}
		}
		(new RoiRotator()).run("");
	}
	
	private void enlarge(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi!=null) {
			Undo.setup(Undo.ROI, imp);
			roi = (Roi)roi.clone();
			(new RoiEnlarger()).run("");
		} else
			noRoi("Enlarge");
	}

	/*
	if selection is closed shape, create a circle with the same area and centroid, otherwise use<br>
	the Pratt method to fit a circle to the points that define the line or multi-point selection.<br>
	Reference: Pratt V., Direct least-squares fitting of algebraic surfaces", Computer Graphics, Vol. 21, pages 145-152 (1987).<br>
	Original code: Nikolai Chernov's MATLAB script for Newton-based Pratt fit.<br>
	(http://www.math.uab.edu/~chernov/cl/MATLABcircle.html)<br>
	Java version: https://github.com/mdoube/BoneJ/blob/master/src/org/doube/geometry/FitCircle.java<br>
	Authors: Nikolai Chernov, Michael Doube, Ved Sharma
	*/
	void fitCircle(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null) {
			noRoi("Fit Circle");
			return;
		}
		
		if (roi.isArea()) {	  //create circle with the same area and centroid
			Undo.setup(Undo.ROI, imp);
			ImageProcessor ip = imp.getProcessor();
			ip.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.AREA+Measurements.CENTROID, null);
			double r = Math.sqrt(stats.pixelCount/Math.PI);
			imp.deleteRoi();
			int d = (int)Math.round(2.0*r);
			Roi roi2 = new OvalRoi((int)Math.round(stats.xCentroid-r), (int)Math.round(stats.yCentroid-r), d, d);
			transferProperties(roi, roi2);
			imp.setRoi(roi2);
			return;
		}
		
		Polygon poly = roi.getPolygon();
		int n=poly.npoints;
		int[] x = poly.xpoints;
		int[] y = poly.ypoints;
		if (n<3) {
			IJ.error("Fit Circle", "At least 3 points are required to fit a circle.");
			return;
		}
		
		// calculate point centroid
		double sumx = 0, sumy = 0;
		for (int i=0; i<n; i++) {
			sumx = sumx + poly.xpoints[i];
			sumy = sumy + poly.ypoints[i];
		}
		double meanx = sumx/n;
		double meany = sumy/n;
		
		// calculate moments
		double[] X = new double[n], Y = new double[n];
		double Mxx=0, Myy=0, Mxy=0, Mxz=0, Myz=0, Mzz=0;
		for (int i=0; i<n; i++) {
			X[i] = x[i] - meanx;
			Y[i] = y[i] - meany;
			double Zi = X[i]*X[i] + Y[i]*Y[i];
			Mxy = Mxy + X[i]*Y[i];
			Mxx = Mxx + X[i]*X[i];
			Myy = Myy + Y[i]*Y[i];
			Mxz = Mxz + X[i]*Zi;
			Myz = Myz + Y[i]*Zi;
			Mzz = Mzz + Zi*Zi;
		}
		Mxx = Mxx/n;
		Myy = Myy/n;
		Mxy = Mxy/n;
		Mxz = Mxz/n;
		Myz = Myz/n;
		Mzz = Mzz/n;
		
		// calculate the coefficients of the characteristic polynomial
		double Mz = Mxx + Myy;
		double Cov_xy = Mxx*Myy - Mxy*Mxy;
		double Mxz2 = Mxz*Mxz;
		double Myz2 = Myz*Myz;
		double A2 = 4*Cov_xy - 3*Mz*Mz - Mzz;
		double A1 = Mzz*Mz + 4*Cov_xy*Mz - Mxz2 - Myz2 - Mz*Mz*Mz;
		double A0 = Mxz2*Myy + Myz2*Mxx - Mzz*Cov_xy - 2*Mxz*Myz*Mxy + Mz*Mz*Cov_xy;
		double A22 = A2 + A2;
		double epsilon = 1e-12; 
		double ynew = 1e+20;
		int IterMax= 20;
		double xnew = 0;
		int iterations = 0;
		
		// Newton's method starting at x=0
		for (int iter=1; iter<=IterMax; iter++) {
			iterations = iter;
			double yold = ynew;
			ynew = A0 + xnew*(A1 + xnew*(A2 + 4.*xnew*xnew));
			if (Math.abs(ynew)>Math.abs(yold)) {
				if (IJ.debugMode) IJ.log("Fit Circle: wrong direction: |ynew| > |yold|");
				xnew = 0;
				break;
			}
			double Dy = A1 + xnew*(A22 + 16*xnew*xnew);
			double xold = xnew;
			xnew = xold - ynew/Dy;
			if (Math.abs((xnew-xold)/xnew) < epsilon)
				break;
			if (iter >= IterMax) {
				if (IJ.debugMode) IJ.log("Fit Circle: will not converge");
				xnew = 0;
			}
			if (xnew<0) {
				if (IJ.debugMode) IJ.log("Fit Circle: negative root:  x = "+xnew);
				xnew = 0;
			}
		}
		if (IJ.debugMode) IJ.log("Fit Circle: n="+n+", xnew="+IJ.d2s(xnew,2)+", iterations="+iterations);
		
		// calculate the circle parameters
		double DET = xnew*xnew - xnew*Mz + Cov_xy;
		double CenterX = (Mxz*(Myy-xnew)-Myz*Mxy)/(2*DET);
		double CenterY = (Myz*(Mxx-xnew)-Mxz*Mxy)/(2*DET);
		double radius = Math.sqrt(CenterX*CenterX + CenterY*CenterY + Mz + 2*xnew);
		if (Double.isNaN(radius)) {
			IJ.error("Fit Circle", "Points are collinear.");
			return;
		}
		CenterX = CenterX + meanx;
		CenterY = CenterY + meany;
		Undo.setup(Undo.ROI, imp);
		imp.deleteRoi();
		IJ.makeOval((int)Math.round(CenterX-radius), (int)Math.round(CenterY-radius), (int)Math.round(2*radius), (int)Math.round(2*radius));
	}

	void fitSpline() {
		Roi roi = imp.getRoi();
		if (roi==null)
			{noRoi("Spline"); return;}
		int type = roi.getType();
		boolean segmentedSelection = type==Roi.POLYGON||type==Roi.POLYLINE;
		if (!(segmentedSelection||type==Roi.FREEROI||type==Roi.TRACED_ROI||type==Roi.FREELINE))
			{IJ.error("Spline Fit", "Polygon or polyline selection required"); return;}
		if (roi instanceof EllipseRoi)
			return;
		PolygonRoi p = (PolygonRoi)roi;
		Undo.setup(Undo.ROI, imp);
		if (!segmentedSelection && p.getNCoordinates()>3) {
			if (p.subPixelResolution())
				p = trimFloatPolygon(p, p.getUncalibratedLength());
			else
				p = trimPolygon(p, p.getUncalibratedLength());
		}
		String options = Macro.getOptions();
		if (options!=null && options.indexOf("straighten")!=-1)
			p.fitSplineForStraightening();
		else if (options!=null && options.indexOf("remove")!=-1)
			p.removeSplineFit();
		else
			p.fitSpline();
		imp.draw();
		LineWidthAdjuster.update(); 
	}
	
	void interpolate() {
		Roi roi = imp.getRoi();
		if (roi==null)
			{noRoi("Interpolate"); return;}
		if (roi.getType()==Roi.POINT)
			return;
		if (IJ.isMacro()&&Macro.getOptions()==null)
			Macro.setOptions("interval=1");
		GenericDialog gd = new GenericDialog("Interpolate");
		gd.addNumericField("Interval:", 1.0, 1, 4, "pixel");
		gd.addCheckbox("Smooth", IJ.isMacro()?false:smooth);
		gd.addCheckbox("Adjust interval to match", IJ.isMacro()?false:adjust);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		double interval = gd.getNextNumber();
		smooth = gd.getNextBoolean();
		Undo.setup(Undo.ROI, imp);
		adjust = gd.getNextBoolean();
		int sign = adjust ? -1 : 1;
		FloatPolygon poly = roi.getInterpolatedPolygon(sign*interval, smooth);
		int t = roi.getType();
		int type = roi.isLine()?Roi.FREELINE:Roi.FREEROI;
		if (t==Roi.POLYGON && interval>1.0)
			type = Roi.POLYGON;
		if ((t==Roi.RECTANGLE||t==Roi.OVAL||t==Roi.FREEROI) && interval>=8.0)
			type = Roi.POLYGON;
		if ((t==Roi.LINE||t==Roi.FREELINE) && interval>=8.0)
			type = Roi.POLYLINE;
		if (t==Roi.POLYLINE && interval>=8.0)
			type = Roi.POLYLINE;
		ImageCanvas ic = imp.getCanvas();
		if (poly.npoints<=150 && ic!=null && ic.getMagnification()>=12.0)
			type = roi.isLine()?Roi.POLYLINE:Roi.POLYGON;
		Roi p = new PolygonRoi(poly,type);
		if (roi.getStroke()!=null)
			p.setStrokeWidth(roi.getStrokeWidth());
		p.setStrokeColor(roi.getStrokeColor());
		p.setName(roi.getName());
		transferProperties(roi, p);
		imp.setRoi(p);
	}
	
	private static void transferProperties(Roi roi1, Roi roi2) {
		if (roi1==null || roi2==null)
			return;
		roi2.setStrokeColor(roi1.getStrokeColor());
		if (roi1.getStroke()!=null)
			roi2.setStroke(roi1.getStroke());
		roi2.setDrawOffset(roi1.getDrawOffset());
	}
	
	PolygonRoi trimPolygon(PolygonRoi roi, double length) {
		int[] x = roi.getXCoordinates();
		int[] y = roi.getYCoordinates();
		int n = roi.getNCoordinates();
		x = smooth(x, n);
		y = smooth(y, n);
		float[] curvature = getCurvature(x, y, n);
		Rectangle r = roi.getBounds();
		double threshold = rodbard(length);
		//IJ.log("trim: "+length+" "+threshold);
		double distance = Math.sqrt((x[1]-x[0])*(x[1]-x[0])+(y[1]-y[0])*(y[1]-y[0]));
		x[0] += r.x; y[0]+=r.y;
		int i2 = 1;
		int x1,y1,x2=0,y2=0;
		for (int i=1; i<n-1; i++) {
			x1=x[i]; y1=y[i]; x2=x[i+1]; y2=y[i+1];
			distance += Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)) + 1;
			distance += curvature[i]*2;
			if (distance>=threshold) {
				x[i2] = x2 + r.x;
				y[i2] = y2 + r.y;
				i2++;
				distance = 0.0;
			}
		}
		int type = roi.getType()==Roi.FREELINE?Roi.POLYLINE:Roi.POLYGON;
		if (type==Roi.POLYLINE && distance>0.0) {
			x[i2] = x2 + r.x;
			y[i2] = y2 + r.y;
			i2++;
		}		
		PolygonRoi p = new PolygonRoi(x, y, i2, type);
		if (roi.getStroke()!=null)
			p.setStrokeWidth(roi.getStrokeWidth());
		p.setStrokeColor(roi.getStrokeColor());
		p.setName(roi.getName());
		imp.setRoi(p);
		return p;
	}
	
	double rodbard(double x) {
		// y = c*((a-x/(x-d))^(1/b)
		// a=3.9, b=.88, c=712, d=44
		double ex;
		if (x == 0.0)
			ex = 5.0;
		else
			ex = Math.exp(Math.log(x/700.0)*0.88);
		double y = 3.9-44.0;
		y = y/(1.0+ex);
		return y+44.0;
	}

	int[] smooth(int[] a, int n) {
		FloatProcessor fp = new FloatProcessor(n, 1);
		for (int i=0; i<n; i++)
			fp.putPixelValue(i, 0, a[i]);
		GaussianBlur gb = new GaussianBlur();
		gb.blur1Direction(fp, 2.0, 0.01, true, 0);
		for (int i=0; i<n; i++)
			a[i] = (int)Math.round(fp.getPixelValue(i, 0));
		return a;
	}
	
	float[] getCurvature(int[] x, int[] y, int n) {
		float[] x2 = new float[n];
		float[] y2 = new float[n];
		for (int i=0; i<n; i++) {
			x2[i] = x[i];
			y2[i] = y[i];
		}
		ImageProcessor ipx = new FloatProcessor(n, 1, x2, null);
		ImageProcessor ipy = new FloatProcessor(n, 1, y2, null);
		ipx.convolve(kernel, kernel.length, 1);
		ipy.convolve(kernel, kernel.length, 1);
		float[] indexes = new float[n];
		float[] curvature = new float[n];
		for (int i=0; i<n; i++) {
			indexes[i] = i;
			curvature[i] = (float)Math.sqrt((x2[i]-x[i])*(x2[i]-x[i])+(y2[i]-y[i])*(y2[i]-y[i]));
		}
		return curvature;
	}
	
	PolygonRoi trimFloatPolygon(PolygonRoi roi, double length) {
		FloatPolygon poly = roi.getFloatPolygon();
		float[] x = poly.xpoints;
		float[] y = poly.ypoints;
		int n = poly.npoints;
		x = smooth(x, n);
		y = smooth(y, n);
		float[] curvature = getCurvature(x, y, n);
		double threshold = rodbard(length);
		//IJ.log("trim: "+length+" "+threshold);
		double distance = Math.sqrt((x[1]-x[0])*(x[1]-x[0])+(y[1]-y[0])*(y[1]-y[0]));
		int i2 = 1;
		double x1,y1,x2=0,y2=0;
		for (int i=1; i<n-1; i++) {
			x1=x[i]; y1=y[i]; x2=x[i+1]; y2=y[i+1];
			distance += Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)) + 1;
			distance += curvature[i]*2;
			if (distance>=threshold) {
				x[i2] = (float)x2;
				y[i2] = (float)y2;
				i2++;
				distance = 0.0;
			}
		}
		int type = roi.getType()==Roi.FREELINE?Roi.POLYLINE:Roi.POLYGON;
		if (type==Roi.POLYLINE && distance>0.0) {
			x[i2] = (float)x2;
			y[i2] = (float)y2;
			i2++;
		}		
		PolygonRoi p = new PolygonRoi(x, y, i2, type);
		if (roi.getStroke()!=null)
			p.setStrokeWidth(roi.getStrokeWidth());
		p.setStrokeColor(roi.getStrokeColor());
		p.setDrawOffset(roi.getDrawOffset());
		p.setName(roi.getName());
		imp.setRoi(p);
		return p;
	}
	
	float[] smooth(float[] a, int n) {
		FloatProcessor fp = new FloatProcessor(n, 1);
		for (int i=0; i<n; i++)
			fp.setf(i, 0, a[i]);
		GaussianBlur gb = new GaussianBlur();
		gb.blur1Direction(fp, 2.0, 0.01, true, 0);
		for (int i=0; i<n; i++)
			a[i] = fp.getf(i, 0);
		return a;
	}
	
	float[] getCurvature(float[] x, float[] y, int n) {
		float[] x2 = new float[n];
		float[] y2 = new float[n];
		for (int i=0; i<n; i++) {
			x2[i] = x[i];
			y2[i] = y[i];
		}
		ImageProcessor ipx = new FloatProcessor(n, 1, x, null);
		ImageProcessor ipy = new FloatProcessor(n, 1, y, null);
		ipx.convolve(kernel, kernel.length, 1);
		ipy.convolve(kernel, kernel.length, 1);
		float[] indexes = new float[n];
		float[] curvature = new float[n];
		for (int i=0; i<n; i++) {
			indexes[i] = i;
			curvature[i] = (float)Math.sqrt((x2[i]-x[i])*(x2[i]-x[i])+(y2[i]-y[i])*(y2[i]-y[i]));
		}
		return curvature;
	}

	void createEllipse(ImagePlus imp) {
		IJ.showStatus("Fitting ellipse");
		Roi roi = imp.getRoi();
		if (roi==null)
			{noRoi("Fit Ellipse"); return;}
		if (roi.isLine())
			{IJ.error("Fit Ellipse", "\"Fit Ellipse\" does not work with line selections"); return;}
		ImageProcessor ip = imp.getProcessor();
		ip.setRoi(roi);
		int options = Measurements.CENTROID+Measurements.ELLIPSE;
		ImageStatistics stats = ImageStatistics.getStatistics(ip, options, null);
		double dx = stats.major*Math.cos(stats.angle/180.0*Math.PI)/2.0;
		double dy = - stats.major*Math.sin(stats.angle/180.0*Math.PI)/2.0;
		double x1 = stats.xCentroid - dx;
		double x2 = stats.xCentroid + dx;
		double y1 = stats.yCentroid - dy;
		double y2 = stats.yCentroid + dy;
		double aspectRatio = stats.minor/stats.major;
		Undo.setup(Undo.ROI, imp);
		imp.deleteRoi();
		Roi roi2 = new EllipseRoi(x1,y1,x2,y2,aspectRatio);
		transferProperties(roi, roi2);
		imp.setRoi(roi2);
	}

	void convexHull(ImagePlus imp) {
		Roi roi = imp.getRoi();
		int type = roi!=null?roi.getType():-1;
		if (!(type==Roi.FREEROI||type==Roi.TRACED_ROI||type==Roi.POLYGON||type==Roi.POINT))
			{IJ.error("Convex Hull", "Polygonal or point selection required"); return;}
		if (roi instanceof EllipseRoi)
			return;
		//if (roi.subPixelResolution() && roi instanceof PolygonRoi) {
		//	FloatPolygon p = ((PolygonRoi)roi).getFloatConvexHull();
		//	if (p!=null)
		//		imp.setRoi(new PolygonRoi(p.xpoints, p.ypoints, p.npoints, roi.POLYGON));
		//} else {
		Polygon p = roi.getConvexHull();
		if (p!=null) {
			Undo.setup(Undo.ROI, imp);
			Roi roi2 = new PolygonRoi(p.xpoints, p.ypoints, p.npoints, roi.POLYGON);
			transferProperties(roi, roi2);
			imp.setRoi(roi2);
		}
	}
	
	// Finds the index of the upper right point that is guaranteed to be on convex hull
	int findFirstPoint(int[] xCoordinates, int[] yCoordinates, int n, ImagePlus imp) {
		int smallestY = imp.getHeight();
		int x, y;
		for (int i=0; i<n; i++) {
			y = yCoordinates[i];
			if (y<smallestY)
			smallestY = y;
		}
		int smallestX = imp.getWidth();
		int p1 = 0;
		for (int i=0; i<n; i++) {
			x = xCoordinates[i];
			y = yCoordinates[i];
			if (y==smallestY && x<smallestX) {
				smallestX = x;
				p1 = i;
			}
		}
		return p1;
	}
	
	void createMask(ImagePlus imp) {
		Roi roi = imp.getRoi();
		boolean useInvertingLut = Prefs.useInvertingLut;
		Prefs.useInvertingLut = false;
		boolean selectAll = roi!=null && roi.getType()==Roi.RECTANGLE && roi.getBounds().width==imp.getWidth()
			&& roi.getBounds().height==imp.getHeight() && imp.isThreshold();
		boolean overlay = imp.getOverlay()!=null && imp.getProcessor().getMinThreshold()==ImageProcessor.NO_THRESHOLD;
		if (!overlay && (roi==null || !(roi.isArea()||roi.getType()==Roi.POINT) || selectAll)) {
			createMaskFromThreshold(imp);
			Prefs.useInvertingLut = useInvertingLut;
			return;
		}
		if (roi==null && imp.getOverlay()==null) {
			IJ.error("Create Mask", "Area selection or overlay required");
			return;
		}
		ByteProcessor mask = imp.createRoiMask();
		if (!Prefs.blackBackground)
			mask.invertLut();
		ImagePlus maskImp = null;
		Frame frame = WindowManager.getFrame("Mask");
		if (frame!=null && (frame instanceof ImageWindow))
			maskImp = ((ImageWindow)frame).getImagePlus();
		if (maskImp!=null && maskImp.getBitDepth()==8) {
			ImageProcessor ip = maskImp.getProcessor();
			ip.copyBits(mask, 0, 0, Blitter.OR);
			maskImp.setProcessor(ip);
		} else {
			maskImp = new ImagePlus("Mask", mask);
			maskImp.show();
		}
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) {
			Calibration cal2 = maskImp.getCalibration();
			cal2.pixelWidth = cal.pixelWidth;
			cal2.pixelHeight = cal.pixelHeight;
			cal2.setUnit(cal.getUnit());
		}
		maskImp.updateAndRepaintWindow();
		Prefs.useInvertingLut = useInvertingLut;
		Recorder.recordCall("mask = imp.createRoiMask();");
	}
	
	void createMaskFromThreshold(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMinThreshold()==ImageProcessor.NO_THRESHOLD) {
			IJ.error("Create Mask", "Area selection, overlay or thresholded image required");
			return;
		}
		ByteProcessor mask = imp.createThresholdMask();
		if (!Prefs.blackBackground)
			mask.invertLut();
		new ImagePlus("mask",mask).show();
		Recorder.recordCall("mask = imp.createThresholdMask();");
	}

	void createSelectionFromMask(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD) {
			IJ.runPlugIn("ij.plugin.filter.ThresholdToSelection", "");
			return;
		}
		if (!ip.isBinary()) {
			IJ.error("Create Selection",
				"This command creates a composite selection from\n"+
				"a mask (8-bit binary image with white background)\n"+
				"or from an image that has been thresholded using\n"+
				"the Image>Adjust>Threshold tool. The current\n"+
				"image is not a mask and has not been thresholded.");
			return;
		}
		int threshold = ip.isInvertedLut()?255:0;
		ip.setThreshold(threshold, threshold, ImageProcessor.NO_LUT_UPDATE);
		IJ.runPlugIn("ij.plugin.filter.ThresholdToSelection", "");
	}

	void invert(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isArea())
			{IJ.error("Inverse", "Area selection required"); return;}
		ShapeRoi s1, s2;
		if (roi instanceof ShapeRoi)
			s1 = (ShapeRoi)roi;
		else
			s1 = new ShapeRoi(roi);
		s2 = new ShapeRoi(new Roi(0,0, imp.getWidth(), imp.getHeight()));
		Undo.setup(Undo.ROI, imp);
		imp.setRoi(s1.xor(s2));
	}
	
	private void lineToArea(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isLine())
			{IJ.error("Line to Area", "Line selection required"); return;}
		Undo.setup(Undo.ROI, imp);
		Roi roi2 = lineToArea(roi);
		imp.setRoi(roi2);
		Roi.previousRoi = (Roi)roi.clone();
	}
	
	/** Converts a line selection into an area selection. */
	public static Roi lineToArea(Roi roi) {
		Roi roi2 = null;
		if (roi.getType()==Roi.LINE) {
			double width = roi.getStrokeWidth();
			if (width<=1.0)
				roi.setStrokeWidth(1.0000001);
			FloatPolygon p = roi.getFloatPolygon();
			roi.setStrokeWidth(width);
			roi2 = new PolygonRoi(p, Roi.POLYGON);
			roi2.setDrawOffset(roi.getDrawOffset());
		} else {
			roi = (Roi)roi.clone();
			int lwidth = (int)roi.getStrokeWidth();
			if (lwidth<1)
				lwidth = 1;
			Rectangle bounds = roi.getBounds();
			int width = bounds.width + lwidth*2;
			int height = bounds.height + lwidth*2;
			ImageProcessor ip2 = new ByteProcessor(width, height);
			roi.setLocation(lwidth, lwidth);
			ip2.setColor(255);
			roi.drawPixels(ip2);
			ip2.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
			ThresholdToSelection tts = new ThresholdToSelection();
			roi2 = tts.convert(ip2);
			if (roi2==null)
				return roi;
			if (bounds.x==0&&bounds.y==0)
				roi2.setLocation(0, 0);
			else
				roi2.setLocation(bounds.x-lwidth/2, bounds.y-lwidth/2);
		}
		transferProperties(roi, roi2);
		roi2.setStrokeWidth(0);
		Color c = roi2.getStrokeColor();
		if (c!=null)  // remove any transparency
			roi2.setStrokeColor(new Color(c.getRed(),c.getGreen(),c.getBlue()));
		return roi2;
	}
	
	void areaToLine(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isArea()) {
			IJ.error("Area to Line", "Area selection required");
			return;
		}
		Undo.setup(Undo.ROI, imp);
		Polygon p = roi.getPolygon();
		if (p==null) return;
		int type1 = roi.getType();
		if (type1==Roi.COMPOSITE) {
			IJ.error("Area to Line", "Composite selections cannot be converted to lines.");
			return;
		}
		int type2 = Roi.POLYLINE;
		if (type1==Roi.OVAL||type1==Roi.FREEROI||type1==Roi.TRACED_ROI
		||((roi instanceof PolygonRoi)&&((PolygonRoi)roi).isSplineFit()))
			type2 = Roi.FREELINE;
		Roi roi2 = new PolygonRoi(p.xpoints, p.ypoints, p.npoints, type2);
		transferProperties(roi, roi2);
		imp.setRoi(roi2);
	}

	void toBoundingBox(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null) {
			noRoi("To Bounding Box");
			return;
		}
		Undo.setup(Undo.ROI, imp);
		Rectangle r = roi.getBounds();
		imp.deleteRoi();
		Roi roi2 = new Roi(r.x, r.y, r.width, r.height);
		transferProperties(roi, roi2);
		imp.setRoi(roi2);
	}

	void addToRoiManager(ImagePlus imp) {
		if (IJ.macroRunning() &&  Interpreter.isBatchModeRoiManager())
			IJ.error("run(\"Add to Manager\") may not work in batch mode macros");
		Frame frame = WindowManager.getFrame("ROI Manager");
		if (frame==null)
			IJ.run("ROI Manager...");
		if (imp==null) return;
		Roi roi = imp.getRoi();
		if (roi==null) return;
		frame = WindowManager.getFrame("ROI Manager");
		if (frame==null || !(frame instanceof RoiManager))
			IJ.error("ROI Manager not found");
		RoiManager rm = (RoiManager)frame;
		boolean altDown= IJ.altKeyDown();
		IJ.setKeyUp(IJ.ALL_KEYS);
		if (altDown && !IJ.macroRunning())
			IJ.setKeyDown(KeyEvent.VK_SHIFT);
		if (roi.getState()==Roi.CONSTRUCTING) { //wait (up to 2 sec.) until ROI finished
			long start = System.currentTimeMillis();
			while (true) {
				IJ.wait(10);
				if (roi.getState()!=Roi.CONSTRUCTING)
					break;
				if ((System.currentTimeMillis()-start)>2000) {
					IJ.beep();
					IJ.error("Add to Manager", "Selection is not complete");
					return;
				}
			}
		}
		rm.allowRecording(true);
		rm.runCommand("add");
		rm.allowRecording(false);
		IJ.setKeyUp(IJ.ALL_KEYS);
	}
	
	boolean setProperties(String title, Roi roi) {
		if ((roi instanceof PointRoi) && Toolbar.getMultiPointMode() && IJ.altKeyDown()) {
			((PointRoi)roi).displayCounts();
			return true;
		}
		Frame f = WindowManager.getFrontWindow();
		if (f!=null && f.getTitle().indexOf("3D Viewer")!=-1)
			return false;
		if (roi==null) {
			IJ.error("This command requires a selection.");
			return false;
		}
		RoiProperties rp = new RoiProperties(title, roi);
		boolean ok = rp.showDialog();
		if (IJ.debugMode)
			IJ.log(roi.getDebugInfo());
		return ok;
	}
	
	private void makeBand(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null) {
			noRoi("Make Band");
			return;
		}
		Roi roiOrig = roi;
		if (!roi.isArea()) {
			IJ.error("Make Band", "Area selection required");
			return;
		}
		Calibration cal = imp.getCalibration();
		double pixels = bandSize;
		double size = pixels*cal.pixelWidth;
		int decimalPlaces = 0;
		if ((int)size!=size)
			decimalPlaces = 2;
		GenericDialog gd = new GenericDialog("Make Band");
		gd.addNumericField("Band Size:", size, decimalPlaces, 4, cal.getUnits());
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		size = gd.getNextNumber();
		if (Double.isNaN(size)) {
			IJ.error("Make Band", "invalid number");
			return;
		}
		int n = (int)Math.round(size/cal.pixelWidth); 
		if (n >255) {
			IJ.error("Make Band", "Cannot make bands wider that 255 pixels");
			return;
		}
		int width = imp.getWidth();
		int height = imp.getHeight();
		Rectangle r = roi.getBounds();
		ImageProcessor ip = roi.getMask();
		if (ip==null) {
			ip = new ByteProcessor(r.width, r.height);
			ip.invert();
		}
		ImageProcessor mask = new ByteProcessor(width, height);
		mask.insert(ip, r.x, r.y);
		ImagePlus edm = new ImagePlus("mask", mask);
		boolean saveBlackBackground = Prefs.blackBackground;
		Prefs.blackBackground = false;
		int saveType = EDM.getOutputType();
		EDM.setOutputType(EDM.BYTE_OVERWRITE);
		IJ.run(edm, "Distance Map", "");
		EDM.setOutputType(saveType);
		Prefs.blackBackground = saveBlackBackground;
		ip = edm.getProcessor();
		ip.setThreshold(0, n, ImageProcessor.NO_LUT_UPDATE);
		int xx=-1, yy=-1;
		for (int x=r.x; x<r.x+r.width; x++) {
			for (int y=r.y; y<r.y+r.height; y++) {
				if (ip.getPixel(x, y)<n) {
					xx=x; yy=y;
					break;
				}
			}
			if (xx>=0||yy>=0)
				break;
		}
		int count = IJ.doWand(edm, xx, yy, 0, null);
		if (count<=0) {
			IJ.error("Make Band", "Unable to make band");
			return;
		}
		ShapeRoi roi2 = new ShapeRoi(edm.getRoi());
		if (!(roi instanceof ShapeRoi))
			roi = new ShapeRoi(roi);
		ShapeRoi roi1 = (ShapeRoi)roi;
		roi2 = roi2.not(roi1);
		Undo.setup(Undo.ROI, imp);
		transferProperties(roiOrig, roi2);
		imp.setRoi(roi2);
		bandSize = n;
	}
	
	void noRoi(String command) {
		IJ.error(command, "This command requires a selection");
	}

}

