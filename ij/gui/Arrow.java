package ij.gui;
import ij.*;
import ij.process.*;
import java.awt.*;
import java.awt.geom.*;


/** This is an Roi subclass for creating and displaying arrows. */
public class Arrow extends Line {
	public static final String STYLE_KEY = "arrow.style";
	public static final String WIDTH_KEY = "arrow.width";
	public static final String SIZE_KEY = "arrow.size";
	public static final String DOUBLE_HEADED_KEY = "arrow.double";
	public static final String OUTLINE_KEY = "arrow.outline";
	public static final int FILLED=0, NOTCHED=1, OPEN=2, HEADLESS=3;
	public static final String[] styles = {"Filled", "Notched", "Open", "Headless"};
	private static int defaultStyle = (int)Prefs.get(STYLE_KEY, FILLED);
	private static float defaultWidth = (float)Prefs.get(WIDTH_KEY, 2);
	private static double defaultHeadSize = (int)Prefs.get(SIZE_KEY, 10);  // 0-30;
	private static boolean defaultDoubleHeaded = Prefs.get(DOUBLE_HEADED_KEY, false);
	private static boolean defaultOutline = Prefs.get(OUTLINE_KEY, false);
	private int style;
	private double headSize = 10;  // 0-30
	private boolean doubleHeaded;
	private boolean outline;
	private float[] points = new float[2*5];
	private GeneralPath path = new GeneralPath();
	private static Stroke defaultStroke = new BasicStroke();
	private int sx1, sy1, sx2, sy2, sx3, sy3;
	private boolean drawing;
	
	static {
		if (defaultStyle<FILLED || defaultStyle>HEADLESS)
			defaultStyle = FILLED;
	}

	public Arrow(double ox1, double oy1, double ox2, double oy2) {
		super(ox1, oy1, ox2, oy2);
		setStrokeWidth(2);
	}

	public Arrow(int sx, int sy, ImagePlus imp) {
		super(sx, sy, imp);
		setStrokeWidth(defaultWidth);
		style = defaultStyle;
		headSize = defaultHeadSize;
		doubleHeaded = defaultDoubleHeaded;
		outline = defaultOutline;
	}

	/** Draws this arrow on the image. */
	public void draw(Graphics g) {
		if (ic==null) return;
		drawing = true;
		Shape shape2 = null;
		if (doubleHeaded) {
			double tmp = x1R;
			x1R=x2R; x2R=tmp; tmp=y1R; y1R=y2R; y2R=tmp;
			shape2 = getShape();
			tmp=x1R; x1R=x2R; x2R=tmp; tmp=y1R; y1R=y2R; y2R=tmp;
		}
		Shape shape = getShape();
		drawing = false;
		Color color =  strokeColor!=null? strokeColor:ROIColor;
		if (fillColor!=null) color = fillColor;
		g.setColor(color);
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (outline) {
			float lineWidth = getOutlineWidth();
			g2d.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
			g2d.draw(shape);
			g2d.setStroke(defaultStroke);
		} else  {
			g2d.fill(shape);
			//if (doubleHeaded) g2d.fill(shape2);
		}
		if (state!=CONSTRUCTING && !overlay) {
			int size2 = HANDLE_SIZE/2;
			handleColor=Color.white;
			drawHandle(g, sx1-size2, sy1-size2);
			drawHandle(g, sx2-size2, sy2-size2);
			drawHandle(g, sx3-size2, sy3-size2);
		}
		if (imp!=null&&imp.getRoi()!=null) showStatus();
		if (updateFullWindow) 
			{updateFullWindow = false; imp.draw();}
	}
	
	private Shape getPath() {
		path.reset();
		path = new GeneralPath();
		calculatePoints();
		path.moveTo(points[0], points[1]); // tail
		path.lineTo(points[2 * 1], points[2 * 1 + 1]); // head back
		path.moveTo(points[2 * 1], points[2 * 1 + 1]); // head back
		if (style==OPEN)
			path.moveTo(points[2 * 2], points[2 * 2 + 1]);
		else
			path.lineTo(points[2 * 2], points[2 * 2 + 1]); // left point
		path.lineTo(points[2 * 3], points[2 * 3 + 1]); // head tip
		path.lineTo(points[2 * 4], points[2 * 4 + 1]); // right point
		path.lineTo(points[2 * 1], points[2 * 1 + 1]); // back to the head back
		return path;
	}

	 /** Based on the method with the same name in Fiji's Arrow plugin,
	 	written by Jean-Yves Tinevez and Johannes Schindelin. */
	 private void calculatePoints() {
		double tip = 0.0;
		double base;
		double shaftWidth = getStrokeWidth();
		double mag = drawing?ic.getMagnification():1.0;
		double length = 8+10*shaftWidth*mag*0.5;
		length = length*(headSize/10.0);
		length -= shaftWidth*1.42;
		if (style==NOTCHED) length*=0.74;
		if (style==OPEN) length*=1.3;
		if (length<0.0 || style==HEADLESS) length=0.0;
		x1d=x+x1R; y1d=y+y1R; x2d=x+x2R; y2d=y+y2R;
		sx1 = ic.screenXD(x1d);
		sy1 = ic.screenYD(y1d);
		sx2 = ic.screenXD(x2d);
		sy2 = ic.screenYD(y2d);
		sx3 = sx1 + (sx2-sx1)/2;
		sy3 = sy1 + (sy2-sy1)/2;
		double dx1=sx1, dy1=sy1, dx2=sx2, dy2=sy2;
		if (!drawing)
			{dx1=x1d; dy1=y1d; dx2=x2d; dy2=y2d;}
		x1=(int)x1d; y1=(int)y1d; x2=(int)x2d; y2=(int)y2d;
		points[0] = (float)dx1;
		points[1] = (float)dy1;
        if (length>0) {
			double dx=dx2-dx1, dy=dy2-dy1;
			double arrowLength = Math.sqrt(dx*dx+dy*dy);
			dx=dx/arrowLength; dy=dy/arrowLength;
			double factor = style==OPEN?1.3:1.42;
			points[2*3] = (float)(dx2-dx*shaftWidth*mag*factor);
			points[2*3+1] = (float)(dy2-dy*shaftWidth*mag*factor);
		} else {
			points[2*3] = (float)dx2;
			points[2*3+1] = (float)dy2;
		}
		final double alpha = Math.atan2(points[2*3+1]-points[1], points[2*3]-points[0]);
		double SL = 0.0;
		switch (style) {
			case FILLED: case HEADLESS:
				tip = Math.toRadians(20.0);
				base = Math.toRadians(90.0);
				points[1*2]   = (float) (points[2*3]	- length*Math.cos(alpha));
				points[1*2+1] = (float) (points[2*3+1] - length*Math.sin(alpha));
				SL = length*Math.sin(base)/Math.sin(base+tip);;
				break;
			case NOTCHED:
				tip = Math.toRadians(20);
				base = Math.toRadians(120);
				points[1*2]   = (float) (points[2*3] - length*Math.cos(alpha));
				points[1*2+1] = (float) (points[2*3+1] - length*Math.sin(alpha));
				SL = length*Math.sin(base)/Math.sin(base+tip);;
				break;
			case OPEN:
				tip = Math.toRadians(25); //30
				points[1*2] = points[2*3];
				points[1*2+1] = points[2*3+1];
				SL = length;
				break;
		}
		// P2 = P3 - SL*alpha+tip
		points[2*2] = (float) (points[2*3]	- SL*Math.cos(alpha+tip));
		points[2*2+1] = (float) (points[2*3+1] - SL*Math.sin(alpha+tip));
		// P4 = P3 - SL*alpha-tip
		points[2*4]   = (float) (points[2*3]	- SL*Math.cos(alpha-tip));
		points[2*4+1] = (float) (points[2*3+1] - SL*Math.sin(alpha-tip));
 	}
 	
	private Shape getShape() {
		Shape arrow = getPath();
		float mag = (float)(getStrokeWidth()*(drawing?ic.getMagnification():1.0));
		BasicStroke stroke = new BasicStroke(mag, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
		Shape outlineShape = stroke.createStrokedShape(arrow);
		Area a1 = new Area(arrow);
		Area a2 = new Area(outlineShape);
		try {a1.add(a2);} catch(Exception e) {};
		return a1;
	}

	private ShapeRoi getShapeRoi() {
		Shape arrow = getPath();
		BasicStroke stroke = new BasicStroke(getStrokeWidth(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
		ShapeRoi sroi = new ShapeRoi(arrow);
		Shape outlineShape = stroke.createStrokedShape(arrow);
		sroi.or(new ShapeRoi(outlineShape));
		return sroi;
	}

	//public ImageProcessor getMask() {
	//	return getShapeRoi().getMask();
	//}

	private float getOutlineWidth() {
		double width = getStrokeWidth()/8.0;
		if (width<1.0) width = 1.0;
		double head = headSize/8.0;
		if (head<1.0) head = 1.0;
		double mag = ic.getMagnification();
		if (mag<0.5) mag = 0.5;
		double lineWidth = width*head*mag;
		if (lineWidth<1.0) lineWidth = 1.0;
		return (float)lineWidth;
	}

	public void drawPixels(ImageProcessor ip) {
		if (outline) {
			int lineWidth = ip.getLineWidth();
			ip.setLineWidth((int)Math.round(getOutlineWidth()));
			getShapeRoi().drawPixels(ip);
			ip.setLineWidth(lineWidth);
		} else
			ip.fill(getShapeRoi());
	}
	
	public boolean contains(int x, int y) {
		return getShapeRoi().contains(x, y);
	}

	protected void handleMouseDown(int sx, int sy) {
		super.handleMouseDown(sx, sy);
		startxd = ic.offScreenXD(sx);
		startyd = ic.offScreenYD(sy);
	}

	protected int clipRectMargin() {
		double mag = ic!=null?ic.getMagnification():1.0;
		double arrowWidth = getStrokeWidth();
		double size = 8+10*arrowWidth*mag*0.5;
		return (int)Math.max(size*2.0, headSize);
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

	public int getStyle() {
		return style;
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

	public double getHeadSize() {
		return headSize;
	}

	public static void setDefaultHeadSize(double size) {
		defaultHeadSize = size;
	}

	public static double getDefaultHeadSize() {
		return defaultHeadSize;
	}

	public void setDoubleHeaded(boolean b) {
		doubleHeaded = b;
	}

	public boolean getDoubleHeaded() {
		return doubleHeaded;
	}

	public static void setDefaultDoubleHeaded(boolean b) {
		defaultDoubleHeaded = b;
	}

	public static boolean getDefaultDoubleHeaded() {
		return defaultDoubleHeaded;
	}

	public void setOutline(boolean b) {
		outline = b;
	}

	public boolean getOutline() {
		return outline;
	}

	public static void setDefaultOutline(boolean b) {
		defaultOutline = b;
	}

	public static boolean getDefaultOutline() {
		return defaultOutline;
	}

}
