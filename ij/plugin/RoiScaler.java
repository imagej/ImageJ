package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.geom.*;

/** This plugin implements the Edit/Selection/Scale command. */
public class RoiScaler implements PlugIn {
	private static double defaultXScale = 1.5;
	private static double defaultYScale = 1.5;
	private double xscale;
	private double yscale;
	private boolean centered;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		if (roi==null) {
			IJ.error("Scale", "This command requires a selection");
			return;
		}
		if (!imp.okToDeleteRoi())
			return;
		if (!showDialog())
			return;
		if (!IJ.macroRunning()) {
			defaultXScale = xscale;
			defaultYScale = yscale;
		}
		//if (roi instanceof ImageRoi) {
		//	((ImageRoi)roi).rotate(angle);
		//	imp.draw();
		//	return;
		//}
		Roi roi2 = scale(roi, xscale, yscale, centered);
		if (roi2==null)
			return;
		Undo.setup(Undo.ROI, imp);
		roi = (Roi)roi.clone();
		imp.setRoi(roi2);
		Roi.previousRoi = roi;
	}
	
	public boolean showDialog() {
		GenericDialog gd = new GenericDialog("Scale Selection");
		gd.addNumericField("X scale factor:", defaultXScale, 2, 3, "");
		gd.addNumericField("Y scale factor:", defaultYScale, 2, 3, "");
		gd.addCheckbox("Centered", false);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		xscale = gd.getNextNumber();
		yscale = gd.getNextNumber();
		centered = gd.getNextBoolean();
		return true;
	}
	
	public static Roi scale(Roi roi, double xscale, double yscale, boolean centered) {
		if (roi instanceof ShapeRoi)
			return scaleShape((ShapeRoi)roi, xscale, yscale, centered);
		FloatPolygon poly = roi.getFloatPolygon();
		int type = roi.getType();
		if (type==Roi.LINE) {
			Line line = (Line)roi;
			double x1=line.x1d;
			double y1=line.y1d;
			double x2=line.x2d;
			double y2=line.y2d;
			poly = new FloatPolygon();
			poly.addPoint(x1, y1);
			poly.addPoint(x2, y2);
		}
		Rectangle r = roi.getBounds();
		double xbase = r.x - (r.width*xscale-r.width)/2.0;
		double ybase = r.y - (r.height*yscale-r.height)/2.0;
		for (int i=0; i<poly.npoints; i++) {
			if (centered) {
				poly.xpoints[i] = (float)((poly.xpoints[i]-r.x)*xscale + xbase);
				poly.ypoints[i] = (float)((poly.ypoints[i]-r.y)*yscale + ybase);
			} else {
				poly.xpoints[i] = (float)(poly.xpoints[i]*xscale);
				poly.ypoints[i] = (float)(poly.ypoints[i]*yscale);
			}
		}
		Roi roi2 = null;
		if (type==Roi.LINE)
			roi2 = new Line(poly.xpoints[0], poly.ypoints[0], poly.xpoints[1], poly.ypoints[1]);
		else if (type==Roi.POINT)
			roi2 = new PointRoi(poly.xpoints, poly.ypoints,poly.npoints);
		else {
			if (type==Roi.RECTANGLE)
				type = Roi.POLYGON;
			if (type==Roi.RECTANGLE && poly.npoints>4) // rounded rectangle
				type = Roi.FREEROI;
			if (type==Roi.OVAL||type==Roi.TRACED_ROI)
				type = Roi.FREEROI;
			roi2 = new PolygonRoi(poly.xpoints, poly.ypoints,poly.npoints, type);
		}
		roi2.setStrokeColor(roi.getStrokeColor());
		if (roi.getStroke()!=null)
			roi2.setStroke(roi.getStroke());
		return roi2;
	}
	
	private static Roi scaleShape(ShapeRoi roi, double xscale, double yscale, boolean centered) {
		Rectangle r = roi.getBounds();
		Shape shape = roi.getShape();
		AffineTransform at = new AffineTransform();
		at.scale(xscale, yscale);
		if (!centered)
			at.translate(r.x, r.y);
		Shape shape2 = at.createTransformedShape(shape);
		Roi roi2 = new ShapeRoi(shape2);
		if (centered) {
			int xbase = (int)(centered?r.x-(r.width*xscale-r.width)/2.0:r.x);
			int ybase = (int)(centered?r.y-(r.height*yscale-r.height)/2.0:r.y);
			roi2.setLocation(xbase, ybase);
		}
		return roi2;
	}
	
}
