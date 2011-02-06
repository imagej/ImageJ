package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.frame.*;
import ij.macro.Interpreter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.ThresholdToSelection;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Vector;


/** This plugin implements the commands in the Edit/Section submenu. */
public class Selection implements PlugIn, Measurements {
	private ImagePlus imp;
	private float[] kernel = {1f, 1f, 1f, 1f, 1f};
	private float[] kernel3 = {1f, 1f, 1f};
	private static String angle = "15"; // degrees
	private static String enlarge = "15"; // pixels
	private static String bandSize = "15"; // pixels
	private static boolean nonScalable;
	private static Color linec, fillc;
	private static int lineWidth = 1;
	

	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
    	if (arg.equals("add"))
    		{addToRoiManager(imp); return;}
		if (imp==null)
			{IJ.noImage(); return;}
    	if (arg.equals("all"))
    		imp.setRoi(0,0,imp.getWidth(),imp.getHeight());
    	else if (arg.equals("none"))
    		imp.killRoi();
    	else if (arg.equals("restore"))
    		imp.restoreRoi();
    	else if (arg.equals("spline"))
    		fitSpline();
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
    	else if (arg.equals("tobox"))
    		toBoundingBox(imp); 
     	else if (arg.equals("toarea"))
    		lineToArea(imp); 
	   	else if (arg.equals("properties"))
    		{setProperties("Properties", imp.getRoi()); imp.draw();}
    	else
    		runMacro(arg);
	}
	
	void runMacro(String arg) {
		Roi roi = imp.getRoi();
		if (IJ.macroRunning()) {
			String options = Macro.getOptions();
			if (options!=null && (options.indexOf("grid=")!=-1||options.indexOf("interpolat")!=-1)) {
				IJ.run("Rotate... ", options); // run Image>Transform>Rotate
				return;
			}
		}
		if (roi==null) {
			IJ.error("Rotate>Selection", "This command requires a selection");
			return;
		}
		roi = (Roi)roi.clone();
		if (arg.equals("rotate")) {
			double d = Tools.parseDouble(angle);
			if (Double.isNaN(d)) angle = "15";
			String value = IJ.runMacroFile("ij.jar:RotateSelection", angle);
			if (value!=null) angle = value;    	
		} else if (arg.equals("enlarge")) {
			String value = IJ.runMacroFile("ij.jar:EnlargeSelection", enlarge); 
			if (value!=null) enlarge = value; 
			Roi.previousRoi = roi;
		} else if (arg.equals("band")) {
			String value = IJ.runMacroFile("ij.jar:MakeSelectionBand", bandSize); 
			if (value!=null) bandSize = value;    	
			Roi.previousRoi = roi;
		}
	}
	
	/*
	if selection is closed shape, create a circle with the same area and centroid, otherwise use<br>
	the Pratt method to fit a circle to the points that define the line or multi-point selection.<br>
	Reference: Pratt V., Direct least-squares fitting of algebraic surfaces", Computer Graphics, Vol. 21, pages 145-152 (1987).<br>
	Original code: Nikolai Chernov's MATLAB script for Newton-based Pratt fit.<br>
	(http://www.math.uab.edu/~chernov/cl/MATLABcircle.html)<br>
	Java version: https://github.com/mdoube/BoneJ/blob/master/src/org/doube/geometry/FitCircle.java<br>
	@authors Nikolai Chernov, Michael Doube, Ved Sharma
	*/
	void fitCircle(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null) {
			IJ.error("Fit Circle", "Selection required");
			return;
		}
		
		if (roi.isArea()) {   //create circle with the same area and centroid
			ImageProcessor ip = imp.getProcessor();
			ip.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.AREA+Measurements.CENTROID, null);
			double r = Math.sqrt(stats.pixelCount/Math.PI);
			imp.killRoi();
			int d = (int)Math.round(2.0*r);
			IJ.makeOval((int)Math.round(stats.xCentroid-r), (int)Math.round(stats.yCentroid-r), d, d);
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
		imp.killRoi();
		IJ.makeOval((int)Math.round(CenterX-radius), (int)Math.round(CenterY-radius), (int)Math.round(2*radius), (int)Math.round(2*radius));
	}

	void fitSpline() {
		Roi roi = imp.getRoi();
		if (roi==null)
			{IJ.error("Spline", "Selection required"); return;}
		int type = roi.getType();
		boolean segmentedSelection = type==Roi.POLYGON||type==Roi.POLYLINE;
		if (!(segmentedSelection||type==Roi.FREEROI||type==Roi.TRACED_ROI||type==Roi.FREELINE))
			{IJ.error("Spline", "Polygon or polyline selection required"); return;}
		if (roi instanceof EllipseRoi)
			return;
		PolygonRoi p = (PolygonRoi)roi;
		if (!segmentedSelection)
			p = trimPolygon(p, getUncalibratedLength(p));
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
	
	double getUncalibratedLength(PolygonRoi roi) {
		Calibration cal = imp.getCalibration();
		double spw=cal.pixelWidth, sph=cal.pixelHeight;
		cal.pixelWidth=1.0; cal.pixelHeight=1.0;
		double length = roi.getLength();
		cal.pixelWidth=spw; cal.pixelHeight=sph;
		return length;
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
	
	void createEllipse(ImagePlus imp) {
		IJ.showStatus("Fitting ellipse");
		Roi roi = imp.getRoi();
		if (roi==null)
			{IJ.error("Fit Ellipse", "Selection required"); return;}
		if (roi.isLine())
			{IJ.error("Fit Ellipse", "\"Fit Ellipse\" does not work with line selections"); return;}
		ImageStatistics stats = imp.getStatistics(Measurements.CENTROID+Measurements.ELLIPSE);
		double dx = stats.major*Math.cos(stats.angle/180.0*Math.PI)/2.0;
		double dy = - stats.major*Math.sin(stats.angle/180.0*Math.PI)/2.0;
		double x1 = stats.xCentroid - dx;
		double x2 = stats.xCentroid + dx;
		double y1 = stats.yCentroid - dy;
		double y2 = stats.yCentroid + dy;
		double aspectRatio = stats.minor/stats.major;
		imp.killRoi();
		imp.setRoi(new EllipseRoi(x1,y1,x2,y2,aspectRatio));
	}

	void convexHull(ImagePlus imp) {
		Roi roi = imp.getRoi();
		int type = roi!=null?roi.getType():-1;
		if (!(type==Roi.FREEROI||type==Roi.TRACED_ROI||type==Roi.POLYGON||type==Roi.POINT))
			{IJ.error("Convex Hull", "Polygonal or point selection required"); return;}
		if (roi instanceof EllipseRoi)
			return;
		Polygon p = roi.getConvexHull();
		if (p!=null)
			imp.setRoi(new PolygonRoi(p.xpoints, p.ypoints, p.npoints, roi.POLYGON));
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
		if (roi==null || !(roi.isArea()||roi.getType()==Roi.POINT)) {
			createMaskFromThreshold(imp);
			Prefs.useInvertingLut = useInvertingLut;
			return;
		}
		ImagePlus maskImp = null;
		Frame frame = WindowManager.getFrame("Mask");
		if (frame!=null && (frame instanceof ImageWindow))
			maskImp = ((ImageWindow)frame).getImagePlus();
		if (maskImp==null) {
			ImageProcessor ip = new ByteProcessor(imp.getWidth(), imp.getHeight());
			if (!Prefs.blackBackground)
				ip.invertLut();
			maskImp = new ImagePlus("Mask", ip);
			maskImp.show();
		}
		ImageProcessor ip = maskImp.getProcessor();
		ip.setRoi(roi);
		ip.setValue(255);
		ip.fill(ip.getMask());
		maskImp.updateAndDraw();
		Prefs.useInvertingLut = useInvertingLut;
	}
	
	void createMaskFromThreshold(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMinThreshold()==ImageProcessor.NO_THRESHOLD)
			{IJ.error("Create Mask", "Area selection or thresholded image required"); return;}
		double t1 = ip.getMinThreshold();
		double t2 = ip.getMaxThreshold();
		IJ.run("Duplicate...", "title=mask");
		ImagePlus imp2 = WindowManager.getCurrentImage();
		ImageProcessor ip2 = imp2.getProcessor();
		ip2.setThreshold(t1, t2, ImageProcessor.NO_LUT_UPDATE);
		IJ.run("Convert to Mask");
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
		imp.setRoi(s1.xor(s2));
	}
	
	void lineToArea(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isLine())
			{IJ.error("Line to Area", "Line selection required"); return;}
		if (roi.getType()==Roi.LINE && roi.getStrokeWidth()==1)
			{IJ.error("Line to Area", "Straight line width must be > 1"); return;}
		ImageProcessor ip2 = new ByteProcessor(imp.getWidth(), imp.getHeight());
		ip2.setColor(255);
		if (roi.getType()==Roi.LINE)
			ip2.fillPolygon(roi.getPolygon());
		else {
			roi.drawPixels(ip2);
			//BufferedImage bi = new BufferedImage(imp.getWidth(), imp.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			//Graphics g = bi.getGraphics();
			//Roi roi2 = (Roi)roi.clone();
			//roi2.setStrokeColor(Color.white);
			//roi2.drawOverlay(g);
			//ip2 = new ByteProcessor(bi);
		}
		//new ImagePlus("ip2", ip2.duplicate()).show();
		ip2.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		ThresholdToSelection tts = new ThresholdToSelection();
		Roi roi2 = tts.convert(ip2);
		imp.setRoi(roi2);
		Roi.previousRoi = (Roi)roi.clone();
	}
	
	void toBoundingBox(ImagePlus imp) {
		Roi roi = imp.getRoi();
		Rectangle r = roi.getBounds();
		imp.killRoi();
		imp.setRoi(new Roi(r.x, r.y, r.width, r.height));
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
		if (altDown) IJ.setKeyDown(KeyEvent.VK_SHIFT);
		rm.runCommand("add");
		IJ.setKeyUp(IJ.ALL_KEYS);
	}
	
	boolean setProperties(String title, Roi roi) {
		Frame f = WindowManager.getFrontWindow();
		if (f!=null && f.getTitle().indexOf("3D Viewer")!=-1)
			return false;
		if (roi==null) {
			IJ.error("This command requires a selection.");
			return false;
		}
		RoiProperties rp = new RoiProperties(title, roi);
		return rp.showDialog();
	}

}

