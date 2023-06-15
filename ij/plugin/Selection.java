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
import java.awt.geom.*;


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
	private static double translateX;
	private static double translateY;

	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (arg.equals("add")) {
			addToRoiManager(imp);
			return;
		}		
		if (imp==null) {
			if (!(IJ.isMacro()&&arg.equals("none")))
				IJ.noImage();
			return;
		}
		if (arg.equals("all")) {
			if (imp.okToDeleteRoi()) {
				imp.saveRoi();
				imp.setRoi(0,0,imp.getWidth(),imp.getHeight());
			}
		} else if (arg.equals("none")) {
			if (imp.okToDeleteRoi())
				imp.deleteRoi();
		} else if (arg.equals("restore"))
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
		else if (arg.equals("translate"))
			translate(imp); 
		else if (arg.equals("enlarge"))
			enlarge(imp); 
		else if (arg.equals("rect"))
			fitRectangle(imp); 
	}
	
	private void rotate(ImagePlus imp) {
		if (!imp.okToDeleteRoi()) return;
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
		if (!imp.okToDeleteRoi()) return;
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
		if (!imp.okToDeleteRoi()) return;
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
		Roi newRoi = null;
		if (roi instanceof ShapeRoi && ((ShapeRoi)roi).getRois().length>1) {
			// handle composite roi, thanks to Michael Ellis
			Roi[] rois = ((ShapeRoi) roi).getRois();
			ShapeRoi newShapeRoi = null;
			for (Roi roi2 : rois) {
				FloatPolygon fPoly = roi2.getInterpolatedPolygon(interval,smooth);
				PolygonRoi polygon = new PolygonRoi(fPoly,PolygonRoi.POLYGON);
				if (newShapeRoi==null) // First Roi is the outer boundary
					newShapeRoi = new ShapeRoi(polygon);
				else {
					// Assume subsequent Rois are holes to be subtracted
					ShapeRoi tempRoi = new ShapeRoi(polygon);
					tempRoi.not(newShapeRoi);
					newShapeRoi = tempRoi;
				}
			}
			newRoi = newShapeRoi;
		} else {
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
			newRoi = new PolygonRoi(poly,type);
		}
		if (roi.getStroke()!=null)
			newRoi.setStrokeWidth(roi.getStrokeWidth());
		newRoi.setStrokeColor(roi.getStrokeColor());
		newRoi.setName(roi.getName());
		transferProperties(roi, newRoi);
		imp.setRoi(newRoi);
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

	/**
		Smooth the specified line, preserving the end points.<br>
		Author: Eugene Katrukha
	*/
	int[] smooth(int[] a, int n) {
		float [] out = new float[n];
		int i,j;
		//no point in averaging 2 points
		if(n<3)
			return a;
		//preserve end points
		out[0]=	a[0];
		out[n-1] = a[n-1];
		//average middle points
		for (i=1; i<(n-1); i++) {
			out[i]=0.0f;			
			for(j=(i-1);j<(i+2);j++){
				out[i] += a[j];
			}
			out[i] /= 3.0f;
		}
		for (i=0; i<n; i++)
			a[i] = (int)Math.round(out[i]);
		return a;
	}

	/**
		Smooth the specified line, preserving the end points.<br>
		Author: Eugene Katrukha
	*/
	float[] smooth(float[] a, int n) {
		float [] out = new float[n];
		int i,j;
		//no point in averaging 2 points
		if(n<3)
			return a;
		//preserve end points
		out[0]=	a[0];
		out[n-1] = a[n-1];
		for (i=1; i<(n-1); i++) {
			out[i]=0.0f;
			for(j=(i-1);j<(i+2);j++)
				out[i] += a[j];
			out[i] /= 3.0f;
		}
		return out;
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
		if (!imp.okToDeleteRoi()) return;
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
		if (!imp.okToDeleteRoi()) return;
		long startTime = System.currentTimeMillis();
		Roi roi = imp.getRoi();
		if (roi == null) { IJ.error("Convex Hull", "Selection required"); return; }
		if (roi instanceof Line) { IJ.error("Convex Hull", "Area selection, point selection, or segmented or free line required"); return; }
		FloatPolygon p = roi.getFloatConvexHull();
		if (p!=null) {
			Undo.setup(Undo.ROI, imp);
			imp.deleteRoi();
			Roi roi2 = new PolygonRoi(p, Roi.POLYGON);
			transferProperties(roi, roi2);
			imp.setRoi(roi2);
			IJ.showTime(imp, startTime, "Convex Hull ", 1);
		}
	}
	
	// Finds the index of the upper right point that is guaranteed to be on convex hull
	/*int findFirstPoint(int[] xCoordinates, int[] yCoordinates, int n, ImagePlus imp) {
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
	}*/
	
	void createMask(ImagePlus imp) {
		Roi roi = imp.getRoi();
		boolean useInvertingLut = Prefs.useInvertingLut;
		Prefs.useInvertingLut = false;
		boolean selectAll = roi!=null && roi.getType()==Roi.RECTANGLE && roi.getBounds().width==imp.getWidth()
			&& roi.getBounds().height==imp.getHeight() && imp.isThreshold();
		boolean overlay = imp.getOverlay()!=null && !imp.isThreshold();
		if (!overlay && (roi==null || selectAll)) {
			createMaskFromThreshold(imp);
			Prefs.useInvertingLut = useInvertingLut;
			return;
		}
		if (roi==null && imp.getOverlay()==null) {
			IJ.error("Create Mask", "Selection, overlay or threshold required");
			return;
		}
		ByteProcessor mask = imp.createRoiMask();
		if (!Prefs.blackBackground)
			mask.invertLut();
		mask.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
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
		if (!ip.isThreshold()) {
			IJ.error("Create Mask", "Area selection, overlay or thresholded image required");
			return;
		}
		ByteProcessor mask = imp.createThresholdMask();
		if (!Prefs.blackBackground)
			mask.invertLut();
		mask.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		ImagePlus maskImp = new ImagePlus("mask",mask);
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) {
			Calibration cal2 = maskImp.getCalibration();
			cal2.pixelWidth = cal.pixelWidth;
			cal2.pixelHeight = cal.pixelHeight;
			cal2.setUnit(cal.getUnit());
		}
		maskImp.show();
		Recorder.recordCall("mask = imp.createThresholdMask();");
	}

	void createSelectionFromMask(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (ip.isThreshold()) {
			IJ.runPlugIn("ij.plugin.filter.ThresholdToSelection", "");
			return;
		}
		if (!ip.isBinary()) {
			IJ.error("Create Selection",
				"This command creates a composite selection from\n"+
				"a mask (8-bit binary image) or from an image\n"+
				"thresholded using Image>Adjust>Threshold\n"+
				"The current image is not a mask and has not\n"+
				"been thresholded.");
			return;
		}
		int threshold = ip.isInvertedLut()?255:0;
		if (Prefs.blackBackground)
			threshold = (threshold==255)?0:255;
		ip.setThreshold(threshold, threshold, ImageProcessor.NO_LUT_UPDATE);
		if (!IJ.isMacro())
			IJ.log("Create Selection: threshold not set; assumed to be "+threshold+"-"+threshold);
		IJ.runPlugIn("ij.plugin.filter.ThresholdToSelection", "");
	}
	
	void invert(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi == null)
			{run("all"); return;}
		if (!roi.isArea())
			{IJ.error("Inverse", "Area selection required"); return;}
		Roi inverse = roi.getInverse(imp);
		Undo.setup(Undo.ROI, imp);
		imp.setRoi(inverse);
	}
	
	private void lineToArea(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isLine())
			{IJ.error("Line to Area", "Line selection required"); return;}
		Undo.setup(Undo.ROI, imp);
		Roi roi2 = lineToArea(roi);
		imp.setRoi(roi2);
		Roi.setPreviousRoi(roi);
	}
	
	/** Converts a line selection into an area selection. */
	public static Roi lineToArea(Roi roi) {
		return Roi.convertLineToArea(roi);
	}
	
	void areaToLine(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null || !roi.isArea()) {
			IJ.error("Area to Line", "Area selection required");
			return;
		}
		Undo.setup(Undo.ROI, imp);
		Polygon p = roi.getPolygon();
		FloatPolygon fp = (roi.subPixelResolution()) ? roi.getFloatPolygon() : null;
		if (p==null && fp==null)
			return;
		int type1 = roi.getType();
		if (type1==Roi.COMPOSITE) {
			IJ.error("Area to Line", "Composite selections cannot be converted to lines.");
			return;
		}
		if (fp==null && type1==Roi.TRACED_ROI) {
			for (int i=0; i<p.npoints; i++) {
				if (p.xpoints[i]>=imp.getWidth()) p.xpoints[i]=imp.getWidth()-1;
				if (p.ypoints[i]>=imp.getHeight()) p.ypoints[i]=imp.getHeight()-1;
			}
		}
		int type2 = Roi.POLYLINE;
		if (type1==Roi.OVAL||type1==Roi.FREEROI||type1==Roi.TRACED_ROI
		||((roi instanceof PolygonRoi)&&((PolygonRoi)roi).isSplineFit()))
			type2 = Roi.FREELINE;
		Roi roi2 = fp==null ? new PolygonRoi(p, type2) : new PolygonRoi(fp, type2);
		transferProperties(roi, roi2);
		Rectangle2D.Double bounds = roi.getFloatBounds();
		roi2.setLocation(bounds.x - 0.5, bounds.y -0.5);	//area and line roi coordinates are 0.5 pxl different
		imp.setRoi(roi2);
	}

	void toBoundingBox(ImagePlus imp) {
		Roi roi = imp.getRoi();
		if (roi==null) {
			noRoi("To Bounding Box");
			return;
		}
		if (!imp.okToDeleteRoi())
			return;
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
		if (Recorder.record && imp.getStackSize()>1) {
			if (imp.isHyperStack()) {
				int C = imp.getChannel();
				int Z = imp.getSlice();
				int T = imp.getFrame();
				if (Recorder.scriptMode()) {
					Recorder.recordCall("roi = imp.getRoi();");
					Recorder.recordCall("roi.setPosition("+C+", "+Z+", "+T+");");
				} else
					Recorder.record("Roi.setPosition", C, Z, T);				
			} else {
				int position = imp.getCurrentSlice();
				if (Recorder.scriptMode()) {
					Recorder.recordCall("roi = imp.getRoi();");
					Recorder.recordCall("roi.setPosition("+position+");");
				} else
					Recorder.record("Roi.setPosition", position);
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
		String name = roi.getName();
		if (name==null) name = "";
		int position = roi.getPosition();
		int group = roi.getGroup();
		Color color = roi.getStrokeColor();
		Color fillColor = roi.getFillColor();
		int width = (int)roi.getStrokeWidth();
		RoiProperties rp = new RoiProperties(title, roi);
		boolean ok = rp.showDialog();
		if (Recorder.record) {
			boolean groupChanged = false;
			String name2 = roi.getName();
			if (name2==null) name2 = "";
			int position2 = roi.getPosition();
			int group2 = roi.getGroup();
			if (group2!=group) groupChanged=true;
			Color color2 = roi.getStrokeColor();
			Color fillColor2 = roi.getFillColor();
			int width2 = (int)roi.getStrokeWidth();
			if (Recorder.scriptMode()) {
				Recorder.recordCall("roi = imp.getRoi();");
				if (name2!=name)
					Recorder.recordCall("roi.setName(\""+name2+"\");");
				if (position2!=position)
					Recorder.recordCall("roi.setPosition("+position2+");");
				if (group2!=group)
					Recorder.recordCall("roi.setGroup("+group2+");");
				if (width2!=width)
					Recorder.recordCall("roi.setStrokeWidth("+width2+");");
				if (color2!=color && !groupChanged)
					Recorder.recordCall("roi.setStrokeColor("+getColor(color2)+");");
				if (fillColor2!=fillColor)
					Recorder.recordCall("roi.setFillColor("+getColor(fillColor2)+");");
				Recorder.recordCall("imp.draw();");
			} else {
				if (name2!=name)
					Recorder.record("Roi.setName", name2);
				if (groupChanged)
					Recorder.record("Roi.setGroup", group2);
				if (position2!=position)
					Recorder.record("Roi.setPosition", position2);
				if (color2!=color && !groupChanged)
					Recorder.record("Roi.setStrokeColor", Colors.colorToString(color2));
				if (fillColor2!=fillColor)
					Recorder.record("Roi.setFillColor", Colors.colorToString(fillColor2));
				if (width2!=width)
					Recorder.record("Roi.setStrokeWidth", width2);
			}
			Recorder.disableCommandRecording();
		}
		if (IJ.debugMode)
			IJ.log(roi.getDebugInfo());
		return ok;
	}
	
	private String getColor(Color color) {
		return "new Color("+color.getRed()+","+color.getGreen()+","+color.getBlue()+")";
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
		int xx=-1, yy=-1;	//will become the start position for the Wand
		for (int y=Math.max(r.y,0); y<Math.min(r.y+r.height, height); y++) {
			for (int x=Math.max(r.x,0); x<Math.min(r.x+r.width, width); x++) {
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
	
	/*  Fits a minimum area rectangle into a ROI, by searching for the minimum area bounding rectangles
	 *  among the ones having a side that is colinear with an edge of the convex hull.
	 *	
	 * 	Loosely based on:
	 * 	H. Freeman and R. Shapira. 1975. Determining the minimum-area encasing rectangle for an arbitrary 
	 * 	closed curve. Commun. ACM 18, 7 (July 1975), 409–413. DOI:https://doi.org/10.1145/360881.360919	
	*/
	private void fitRectangle(ImagePlus imp) {
		if (!imp.okToDeleteRoi()) return;
		long startTime = System.currentTimeMillis();
		Roi roi = imp.getRoi();
		if (roi == null)
			{noRoi("Fit Rectangle"); return;}
		if (roi instanceof Line || roi.isDrawingTool()) 
			{IJ.error("Fit Rectangle", "Area selection, point selection, or segmented or free line required"); return;}
		if (!roi.isArea()) {
			// check number of points and colinearity before proceeding
			FloatPolygon poly = roi.getFloatPolygon();
			int n = poly.npoints;
			if (n < 3)
				{IJ.error("Fit Rectangle", "At least three points are required"); return;}
			float[] x = poly.xpoints;
			float[] y = poly.ypoints;
			boolean colinear = true;
			for(int i=2; i<n; i++) {
				float prod = (x[i] - x[0]) * (y[i] - y[0]) - (x[i] - x[1]) * (y[i] - y[1]);
				if (prod != 0)	colinear = false;	
			}
			if (colinear)
				{IJ.error("Fit Rectangle", "Points are colinear"); return;}
		}
		FloatPolygon p = roi.getFloatConvexHull();
		if (p!=null) {		
			int np = p.npoints;
			float[] xp = p.xpoints;
			float[] yp = p.ypoints;
			Rectangle r = roi.getBounds();
			double minArea = 2 * r.width * r.height; // generous overestimation
			double minFD = 0;
			int imin = -1;
			int i2min = -1;
			int jmin = -1;
			double min_hmin = 0;
			double min_hmax = 0;
			for (int i = 0; i < np; i++) {
				double maxLD = 0;
				int imax = -1;
				int i2max = -1;
				int jmax = -1;
				int i2 = i + 1;
				if(i == np-1) i2 = 0;
				for (int j = 0; j < np; j++) {
					// distance based on vector cross product
					double d = Math.abs( ((xp[i2] - xp[i]) * (yp[j] - yp[i]) - (xp[j] - xp[i]) * (yp[i2] - yp[i])) / Math.sqrt(Math.pow(xp[i2] - xp[i], 2) + Math.pow(yp[i2] - yp [i], 2)) );
					if (maxLD < d) {
						maxLD = d;
						imax = i;
						jmax = j;
						i2max = i2;
					}
				}
				double hmin = 0;
				double hmax = 0;
				for (int k = 0; k < np; k++) { // rotating calipers
					// projected distance based on vector dot product, includes sign
					double hd = ((xp[i2max] - xp[imax]) * (xp[k] -  xp[imax]) + (yp[k] - yp[imax]) * (yp[i2max] - yp[imax])) / Math.sqrt(Math.pow(xp[i2max] - xp[imax], 2) + Math.pow(yp[i2max] - yp [imax], 2));
					hmin = Math.min(hmin, hd);
					hmax = Math.max(hmax, hd);
				}
				double area = maxLD * (hmax - hmin);
				if (minArea > area) {
					minArea = area;
					minFD = maxLD;
					min_hmin = hmin;
					min_hmax = hmax;

					imin = imax;
					i2min = i2max;
					jmin = jmax;
				}
			}
			double pd = ((xp[i2min] - xp[imin]) * (yp[jmin] - yp[imin]) - (xp[jmin] - xp[imin]) * (yp[i2min] - yp[imin])) / Math.sqrt(Math.pow(xp[i2min] - xp[imin], 2) + Math.pow(yp[i2min] - yp [imin], 2)); // signed feret diameter
			double pairAngle = Math.atan2( yp[i2min]- yp[imin], xp[i2min]- xp[imin]);
			double minAngle = pairAngle + Math.PI/2;

			// rectangle center and signed full height
			double xm = xp[imin] + Math.cos(pairAngle) * (min_hmax + min_hmin)/2 + Math.cos(minAngle) * pd/2;
			double ym = yp[imin] + Math.sin(pairAngle) * (min_hmax + min_hmin)/2 + Math.sin(minAngle) * pd/2;
			double hm = min_hmax - min_hmin;
			
			if (minFD > Math.abs(hm)) { // ensure control axis is parallel to longer side
				pairAngle = pairAngle - Math.PI/2;
				minFD = Math.abs(hm);
				hm = pd;
				}
			
			if (pairAngle * hm > 0)	hm = -hm; // ensure first control point at the top
	
			double x1 = xm + Math.cos(pairAngle) * hm/2;
			double y1 = ym + Math.sin(pairAngle) * hm/2;
			double x2 = xm - Math.cos(pairAngle) * hm/2;
			double y2 = ym - Math.sin(pairAngle) * hm/2;
			Undo.setup(Undo.ROI, imp);
			imp.deleteRoi();
			Roi roi2 = new RotatedRectRoi(x1,  y1,  x2,  y2,  minFD);
			transferProperties(roi, roi2);
			imp.setRoi(roi2);
			IJ.showTime(imp, startTime, "Fit Rectangle ", 1);
		}
	}
	
	private void translate(ImagePlus imp) {
		if (!imp.okToDeleteRoi())
			return;
		Roi roi = imp.getRoi();
		String options = Macro.getOptions();
		if (options!=null && options.contains("interpolation=")) {
			IJ.run("Translate...", options); // run Image>Transform>Translate
			return;
		}
		if (roi==null) {
			noRoi("Translate");
			return;
		}
		double dx = translateX;
		double dy = translateY;
		GenericDialog gd = new GenericDialog("Translate");
		gd.addNumericField("X offset (pixels): ", dx, 0);
		gd.addNumericField("Y offset (pixels): ", dy, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		dx = gd.getNextNumber();
		dy = gd.getNextNumber();
		Rectangle2D r = roi.getFloatBounds();
		roi.setLocation(r.getX()+dx, r.getY()+dy);
		if (imp!=null)
			imp.draw();
		if (options==null) {
			translateX = dx;
			translateY = dy;
		}
	}
	
	void noRoi(String command) {
		IJ.error(command, "This command requires a selection");
	}

}
