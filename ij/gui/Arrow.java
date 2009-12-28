package ij.gui;
import ij.*;
import ij.process.*;
import java.awt.*;
import java.awt.geom.*;


/** This is an Roi subclass for creating and displaying arrows. */
public class Arrow extends Line {
	public static final int FILLED=0, NOTCHED=1, OPEN=2;
	public static final String[] styles = {"Filled", "Notched", "Open"};
	private static int defaultStyle = FILLED;
	private static float defaultWidth = 2;
	private int style;
	private static double defaultHeadSize = 10;  // 0-30;

	public Arrow(double ox1, double oy1, double ox2, double oy2) {
		super(ox1, oy1, ox2, oy2);
		setStrokeWidth(2);
		style = defaultStyle;
		headSize = defaultHeadSize;
	}

	public Arrow(int sx, int sy, ImagePlus imp) {
		super(sx, sy, imp);
		setStrokeWidth(defaultWidth);
		style = defaultStyle;
		headSize = defaultHeadSize;
	}

	/** Draws this arrow on the image. */
	public void draw(Graphics g) {
		if (ic==null) return;
		Color color =  strokeColor!=null? strokeColor:ROIColor;
		g.setColor(color);
		x1d=x+x1R; y1d=y+y1R; x2d=x+x2R; y2d=y+y2R;
		x1=(int)x1d; y1=(int)y1d; x2=(int)x2d; y2=(int)y2d;
		int sx1 = ic.screenXD(x1d);
		int sy1 = ic.screenYD(y1d);
		int sx2 = ic.screenXD(x2d);
		int sy2 = ic.screenYD(y2d);
		int sx3 = sx1 + (sx2-sx1)/2;
		int sy3 = sy1 + (sy2-sy1)/2;
		drawArrow((Graphics2D)g, sx1, sy1, sx2, sy2);
		if (state!=CONSTRUCTING && !overlay) {
			int size2 = HANDLE_SIZE/2;
			handleColor= strokeColor!=null? strokeColor:ROIColor; drawHandle(g, sx1-size2, sy1-size2); handleColor=Color.white;
			drawHandle(g, sx2-size2, sy2-size2);
			drawHandle(g, sx3-size2, sy3-size2);
		}
		if (state!=NORMAL)
			IJ.showStatus(imp.getLocationAsString(x2,y2)+", angle=" + IJ.d2s(getAngle(x1,y1,x2,y2)) + ", length=" + IJ.d2s(getLength()));
		if (updateFullWindow)
			{updateFullWindow = false; imp.draw();}
	}

	void drawArrow(Graphics2D g, double x1, double y1, double x2, double y2) {
		double mag = ic.getMagnification();
		double arrowWidth = getStrokeWidth();
		g.setStroke(new BasicStroke((float)(arrowWidth*mag)));
		double size = 8+10*arrowWidth*mag*0.5;
		size = size*(headSize/10.0);
		double dx = x2-x1;
		double dy = y2-y1;
		double ra = Math.sqrt(dx*dx + dy*dy);
		dx = dx/ra;
		dy = dy/ra;
		double x3 = x2-dx*size;
		double y3 = y2-dy*size;
		double ratio = style==OPEN?0.45:0.35;
		double r = ratio*size;
		double x4 = Math.round(x3+dy*r);
		double y4 = Math.round(y3-dx*r);
		double x5 = Math.round(x3-dy*r);
		double y5 = Math.round(y3+dx*r);
		double x6 = x2-dx*0.85*size;
		double y6 = y2-dy*0.85*size;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (ra>size) {
			double scale = style==OPEN?0.25:0.75;
			g.drawLine((int)x1, (int)y1, (int)(x2-scale*dx*size), (int)(y2-scale*dy*size));
		}
		GeneralPath path = new GeneralPath();
		path.moveTo((float)x4, (float)y4);
		path.lineTo((float)x2, (float)y2);
		path.lineTo((float)x5, (float)y5);
		if (style!=OPEN) {
			if (style==NOTCHED)
				path.lineTo((float)x6, (float)y6);
			path.lineTo((float)x4, (float)y4);
		}
		if (style==OPEN) {
			if (x1!=x2 || y1!=y2) g.draw(path);
		} else
			g.fill(path);
	}

	public void drawPixels(ImageProcessor ip) {
		int width = (int)Math.round(getStrokeWidth());
		ip.setLineWidth(width);
		double size = 8+10*width*0.5;
		double dx = x2-x1;
		double dy = y2-y1;
		double ra = Math.sqrt(dx*dx + dy*dy);
		dx = dx/ra;
		dy = dy/ra;
		double x3 = x2-dx*size;
		double y3 = y2-dy*size;
		double r = 0.35*size;
		double x4 = Math.round(x3+dy*r);
		double y4 = Math.round(y3-dx*r);
		double x5 = Math.round(x3-dy*r);
		double y5 = Math.round(y3+dx*r);
		ip.drawLine((int)x1, (int)y1, (int)(x2-dx*size), (int)(y2-dy*size));
		Polygon poly = new Polygon();
		poly.addPoint((int)x4,(int)y4);
		poly.addPoint((int)x2,(int)y2);
		poly.addPoint((int)x5,(int)y5);
		Roi roi = new PolygonRoi(poly, Roi.POLYGON);
		ip.fill(roi);
	}
	
	public boolean isDrawingTool() {
		return true;
	}
	
	public static void setDefaultWidth(double width) {
		defaultWidth = (float)width;
	}

	public static double getDefaultWidth() {
		return defaultWidth;
	}

	public void setStyle(int style) {
		this.style = style;
	}

	public static void setDefaultStyle(int style) {
		defaultStyle = style;
	}

	public static int getDefaultStyle() {
		return defaultStyle;
	}

	public void setHeadSize(double headSize) {
		this.headSize = headSize;
	}

	public static void setDefaultHeadSize(double size) {
		defaultHeadSize = size;
	}

	public static double getDefaultHeadSize() {
		return defaultHeadSize;
	}

}
