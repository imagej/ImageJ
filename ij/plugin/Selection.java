package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import java.awt.*;


/** This plugin implements the commands in the Edit/Section submenu. */
public class Selection implements PlugIn {
	ImagePlus imp;
	float[] kernel = {1f, 1f, 1f, 1f, 1f};
	float[] kernel3 = {1f, 1f, 1f};

	/** 'arg' must be "all", "none", "restore" or "spline". */
	public void run(String arg) {
		imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
    	if (arg.equals("all"))
    		{imp.setRoi(0,0,imp.getWidth(),imp.getHeight()); return;}
    	if (arg.equals("none"))
    		{imp.killRoi(); return;}
    	if (arg.equals("restore"))
    		{imp.restoreRoi(); return;}
    	if (arg.equals("spline"))
    		{fitSpline(); return;}
	}
	
	void fitSpline() {
		Roi roi = imp.getRoi();
		if (roi==null)
			{IJ.showMessage("Spline", "Selection required"); return;}
		int type = roi.getType();
		boolean segmentedSelection = type==Roi.POLYGON||type==Roi.POLYLINE;
		if (!(segmentedSelection||type==Roi.FREEROI||type==Roi.TRACED_ROI||type==Roi.FREELINE))
			{IJ.showMessage("Spline", "Polygon or polyline selection required"); return;}
		PolygonRoi p = (PolygonRoi)roi;
		double length = getLength(p);
		if (!segmentedSelection)
			p = trimPolygon(p, length);
		int evaluationPoints = (int)(length/2.0);
		ImageWindow win = imp.getWindow();
		if (win!=null) {
			double mag = win.getCanvas().getMagnification();
			if (mag<1.0)
				evaluationPoints *= mag;;
		}
		if (evaluationPoints<100)
			evaluationPoints = 100;
		p.fitSpline(evaluationPoints);
		imp.draw();		
	}
	
	double getLength(PolygonRoi roi) {
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
		float[] curvature = getCurvature(x, y, n);
		Rectangle r = roi.getBoundingRect();
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
		//ImageProcessor ipc = new FloatProcessor(n, 1, curvature, null);
		//ipc.convolve(kernel3, kernel3.length, 1);
		//PlotWindow pw = new PlotWindow("Curvature", "X", "Y", indexes, curvature);
		//pw.draw();											
		return curvature;
	}
	
}

