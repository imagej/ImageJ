package ij.gui;
import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.awt.event.KeyEvent;
import java.util.*;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.util.Tools;
import ij.util.FloatArray;

/**A subclass of <code>ij.gui.Roi</code> (2D Regions Of Interest) implemented in terms of java.awt.Shape.
 * A ShapeRoi is constructed from a <code>ij.gui.Roi</code> object, or as a result of logical operators
 * (i.e., union, intersection, exclusive or, and subtraction) provided by this class. These operators use the package
 * <code>java.awt.geom</code> as a backend. <br>
 * This code is in the public domain.
 * @author Cezar M.Tigaret <c.tigaret@ucl.ac.uk>
 */
public class ShapeRoi extends Roi {

	/***/
	static final int NO_TYPE = 128;
	
	/**The maximum tolerance allowed in calculating the length of the curve segments of this ROI's shape.*/
	static final double MAXERROR = 1.0e-3;
	
	/** Coefficient used to obtain a flattened version of this ROI's shape. A flattened shape is the
	*   closest approximation of the original shape's curve segments with line segments.
	*   The FLATNESS is an indication of the maximum deviation between the flattened and the original shape. */
	static final double FLATNESS = 0.1;

	/** Flatness used for filling the shape when creating a mask. Lower values result in higher accuracy
	 *  (determining which pixels near the border are filled), but lower speed when filling shapes with
	 *  curved borders. */
	static final double FILL_FLATNESS = 0.01;
	
	/**Parsing a shape composed of linear segments less than this value will result in Roi objects of type
	 * {@link ij.gui.Roi#POLYLINE} and {@link ij.gui.Roi#POLYGON} for open and closed shapes, respectively.
	 * Conversion of shapes open and closed with more than MAXPOLY line segments will result,
	 * respectively, in {@link ij.gui.Roi#FREELINE} and {@link ij.gui.Roi#FREEROI} (or
	 * {@link ij.gui.Roi#TRACED_ROI} if {@link #forceTrace} flag is <strong><code>true</code></strong>.
	 */
	private static final int MAXPOLY = 10; // I hate arbitrary values !!!!

    private static final int OR=0, AND=1, XOR=2, NOT=3;

	/**The <code>java.awt.Shape</code> encapsulated by this object.*/
	private Shape shape;
	
	/**The instance value of the maximum tolerance (MAXERROR) allowed in calculating the 
	 * length of the curve segments of this ROI's shape.
	 */
	private double maxerror = ShapeRoi.MAXERROR;
	
	/**The instance value of the coefficient (FLATNESS) used to 
	 * obtain a flattened version of this ROI's shape.
	 */
	private double flatness = ShapeRoi.FLATNESS;
	
	/**The instance value of MAXPOLY.*/
	private int maxPoly = ShapeRoi.MAXPOLY;
    
	/**If <strong></code>true</code></strong> then methods that manipulate this ROI's shape will work on
	 * a flattened version of the shape. */
	private boolean flatten;
	
	/**Flag which specifies how Roi objects will be constructed from closed (sub)paths having more than
	 * <code>MAXPOLY</code> and composed exclusively of line segments.
	 * If <strong><code>true</code></strong> then (sub)path will be parsed into a
	 * {@link ij.gui.Roi#TRACED_ROI}; else, into a {@link ij.gui.Roi#FREEROI}. */
	private boolean forceTrace = false;

	/**Flag which specifies if Roi objects constructed from open (sub)paths composed of only two line segments
	 * will be of type {@link ij.gui.Roi#ANGLE}.
	 * If <strong><code>true</code></strong> then (sub)path will be parsed into a {@link ij.gui.Roi#ANGLE};
	 * else, into a {@link ij.gui.Roi#POLYLINE}. */
	private boolean forceAngle = false;
	
	private Vector savedRois; //not really used any more
	private static Stroke defaultStroke = new BasicStroke();


	/** Constructs a ShapeRoi from an Roi. */
	public ShapeRoi(Roi r) {
		this(r, ShapeRoi.FLATNESS, ShapeRoi.MAXERROR, false, false, false, ShapeRoi.MAXPOLY);
	}

	/** Constructs a ShapeRoi from a Shape. */
	public ShapeRoi(Shape s) {
		super(s.getBounds());
		AffineTransform at = new AffineTransform();
		at.translate(-x, -y);
		shape = new GeneralPath(at.createTransformedShape(s));
		type = COMPOSITE;
	}

	/** Constructs a ShapeRoi from a Shape. */
	public ShapeRoi(int x, int y, Shape s) {
		super(x, y, s.getBounds().width, s.getBounds().height);
		shape = new GeneralPath(s);
		type = COMPOSITE;
	}

	/**Creates a ShapeRoi object from a "classical" ImageJ ROI.
	 * @param r An ij.gui.Roi object
	 * @param flatness The flatness factor used in convertion of curve segments into line segments.
	 * @param maxerror Error correction for calculating length of Bezeir curves.
	 * @param forceAngle flag used in the conversion of Shape objects to Roi objects (see {@link #shapeToRois()}.
	 * @param forceTrace flag for conversion of Shape objects to Roi objects (see {@link #shapeToRois()}.
	 * @param flatten if <strong><code>true</code></strong> then the shape of this ROI will be flattened
	 * (i.e., curve segments will be aproximated by line segments).
	 * @param maxPoly Roi objects constructed from shapes composed of linear segments fewer than this
	 * value will be of type {@link ij.gui.Roi#POLYLINE} or {@link ij.gui.Roi#POLYGON}; conversion of
	 * shapes with linear segments more than this value will result in Roi objects of type
	 * {@link ij.gui.Roi#FREELINE} or {@link ij.gui.Roi#FREEROI} unless the average side length
	 * is large (see {@link #shapeToRois()}).
	 */
	ShapeRoi(Roi r, double flatness, double maxerror, boolean forceAngle, boolean forceTrace, boolean flatten, int maxPoly) {
		super(r.startX, r.startY, r.width, r.height);
		this.type = COMPOSITE;
		this.flatness = flatness;
		this.maxerror = maxerror;
		this.forceAngle = forceAngle;
		this.forceTrace = forceTrace;
		this.maxPoly= maxPoly;
		this.flatten = flatten;
		shape = roiToShape((Roi)r.clone());
	}

	/** Constructs a ShapeRoi from an array of variable length path segments. Each
		segment consists of the segment type followed by 0-6 coordintes (0-3 end points and control
		points). Depending on the type, a segment uses from 1 to 7 elements of the array. */
	public ShapeRoi(float[] shapeArray) {
		super(0,0,null);
		shape = makeShapeFromArray(shapeArray);
		Rectangle r = shape.getBounds();
		x = r.x;
		y = r.y;
		width = r.width;
		height = r.height;		
		state = NORMAL;
		oldX=x; oldY=y; oldWidth=width; oldHeight=height;				
		AffineTransform at = new AffineTransform();
		at.translate(-x, -y);
		shape = new GeneralPath(at.createTransformedShape(shape));
		flatness = ShapeRoi.FLATNESS;
		maxerror = ShapeRoi.MAXERROR;
		maxPoly = ShapeRoi.MAXPOLY;
		flatten = false;
		type = COMPOSITE;
	}
	
	/**Returns a deep copy of this. */
	public synchronized Object clone() { // the equivalent of "operator=" ?
		ShapeRoi sr = (ShapeRoi)super.clone();
		sr.type = COMPOSITE;
		sr.flatness = flatness;
		sr.maxerror = maxerror;
		sr.forceAngle = forceAngle;
		sr.forceTrace = forceTrace;
		//sr.setImage(imp); //wsr
		sr.setShape(ShapeRoi.cloneShape(shape));
		return sr;
	}
	
	/** Returns a deep copy of the argument. */
	static Shape cloneShape(Shape rhs) {
		if (rhs==null) return null;
		else if (rhs instanceof Rectangle2D.Double)
			return (Rectangle2D.Double)((Rectangle2D.Double)rhs).clone();
		else if (rhs instanceof Ellipse2D.Double)
			return (Ellipse2D.Double)((Ellipse2D.Double)rhs).clone();
		else if (rhs instanceof Line2D.Double)
			return (Line2D.Double)((Line2D.Double)rhs).clone();
		else if (rhs instanceof Polygon)
			return new Polygon(((Polygon)rhs).xpoints, ((Polygon)rhs).ypoints, ((Polygon)rhs).npoints);
		else if (rhs instanceof GeneralPath)
			return (GeneralPath)((GeneralPath)rhs).clone();
		else
			return makeShapeFromArray(getShapeAsArray(rhs, 0, 0));
	}

	/**********************************************************************************/
	/***                  Logical operations on shaped rois                        ****/
	/**********************************************************************************/

	/**Unary union operator.
	 * The caller is set to its union with the argument.
	 * @return the union of <strong><code>this</code></strong> and <code>sr</code>
	 */
	public ShapeRoi or(ShapeRoi sr) {return unaryOp(sr, OR);}

	/**Unary intersection operator.
	 * The caller is set to its intersection with the argument (i.e., the overlapping regions between the
	 * operands).
	 * @return the overlapping regions between <strong><code>this</code></strong> and <code>sr</code>
	 */
	public ShapeRoi and(ShapeRoi sr) {return unaryOp(sr, AND);}

	/**Unary exclusive or operator.
	 * The caller is set to the non-overlapping regions between the operands.
	 * @return the union of the non-overlapping regions of <code>this</code> and <code>sr</code>
	 * @see ij.gui.Roi#xor(Roi[])
	 * @see ij.gui.Overlay#xor(int[])
	 */
	public ShapeRoi xor(ShapeRoi sr) {return unaryOp(sr, XOR);}

	/**Unary subtraction operator.
	 * The caller is set to the result of the operation between the operands.
	 * @return <strong><code>this</code></strong> subtracted from <code>sr</code>
	 */
	public ShapeRoi not(ShapeRoi sr) {return unaryOp(sr, NOT);}

	ShapeRoi unaryOp(ShapeRoi sr, int op) {
		AffineTransform at = new AffineTransform();
		at.translate(x, y);
		Area a1 = new Area(at.createTransformedShape(getShape()));
		at = new AffineTransform();
		at.translate(sr.x, sr.y);
		Area a2 = new Area(at.createTransformedShape(sr.getShape()));
		try {
			switch (op) {
				case OR: a1.add(a2); break;
				case AND: a1.intersect(a2); break;
				case XOR: a1.exclusiveOr(a2); break;
				case NOT: a1.subtract(a2); break;
			}
		} catch(Exception e) {}
		Rectangle r = a1.getBounds();
		at = new AffineTransform();
		at.translate(-r.x, -r.y);
		setShape(new GeneralPath(at.createTransformedShape(a1)));
		x = r.x;
		y = r.y;
		cachedMask = null;
		return this;
	}

	/**********************************************************************************/
	/***         Interconversions between "regular" rois and shaped rois           ****/
	/**********************************************************************************/

	/**Converts the Roi argument to an instance of java.awt.Shape.
	 * Currently, the following conversions are supported:<br>
		<table><col><col><col><col><col><col><col>
			<thead>
				<tr><th scope=col> Roi class </th><th scope=col> Roi type </th><th scope=col> Shape </th><</tr>
			</thead>
			<tbody>
				<tr><td> ij.gui.Roi </td>       <td> Roi.RECTANGLE </td><td> java.awt.geom.Rectangle2D.Double </td></tr>
				<tr><td> ij.gui.OvalRoi </td>   <td> Roi.OVAL </td>     <td> java.awt.Polygon of the corresponding traced roi </td></tr>
				<tr><td> ij.gui.Line </td>      <td> Roi.LINE </td>     <td> java.awt.geom.Line2D.Double </td></tr>
				<tr><td> ij.gui.PolygonRoi </td><td> Roi.POLYGON </td>  <td> java.awt.Polygon or (if subpixel resolution) closed java.awt.geom.GeneralPath </td></tr>
				<tr><td> ij.gui.PolygonRoi </td><td> Roi.FREEROI </td>  <td> java.awt.Polygon or (if subpixel resolution) closed java.awt.geom.GeneralPath </td></tr>
				<tr><td> ij.gui.PolygonRoi </td><td> Roi.TRACED_ROI</td><td> java.awt.Polygon or (if subpixel resolution) closed java.awt.geom.GeneralPath </td></tr>
				<tr><td> ij.gui.PolygonRoi </td><td> Roi.POLYLINE </td> <td> open java.awt.geom.GeneralPath  </td></tr>
				<tr><td> ij.gui.PolygonRoi </td><td> Roi.FREELINE </td> <td> open java.awt.geom.GeneralPath  </td></tr>
				<tr><td> ij.gui.PolygonRoi </td><td> Roi.ANGLE </td>    <td> open java.awt.geom.GeneralPath  </td></tr>
				<tr><td> ij.gui.ShapeRoi </td>  <td> Roi.COMPOSITE </td><td> shape of argument  </td></tr>
				<tr><td> ij.gui.ShapeRoi </td>  <td> ShapeRoi.NO_TYPE</td><td> null </td></tr>
			</tbody>
		</table>
	 *
	 * @return A java.awt.geom.* object that inherits from java.awt.Shape interface.
	 *
	 */
	private Shape roiToShape(Roi roi) {
		if (roi.isLine())
			roi = Roi.convertLineToArea(roi);
		Shape shape = null;
		Rectangle r = roi.getBounds();
		boolean closeShape = true;
		int roiType = roi.getType();
		switch(roiType) {
			case Roi.LINE:
				Line line = (Line)roi;				
				shape = new Line2D.Double ((double)(line.x1-r.x), (double)(line.y1-r.y), (double)(line.x2-r.x), (double)(line.y2-r.y) );
				break;
			case Roi.RECTANGLE:
				int arcSize = roi.getCornerDiameter();
				if (arcSize>0)
					shape = new RoundRectangle2D.Double(0, 0, r.width, r.height, arcSize, arcSize);
				else
					shape = new Rectangle2D.Double(0.0, 0.0, (double)r.width, (double)r.height);
				break;
			case Roi.POLYLINE: case Roi.FREELINE: case Roi.ANGLE:
				closeShape = false;
			case Roi.POLYGON: case Roi.FREEROI: case Roi.TRACED_ROI: case Roi.OVAL:
				if (roiType == Roi.OVAL) {
					//shape = new Ellipse2D.Double(-0.001, -0.001, r.width+0.002, r.height+0.002); //inaccurate (though better with increased diameter) 
					shape = ((OvalRoi)roi).getPolygon(false);
				} else if (closeShape && !roi.subPixelResolution()) {
					int nPoints =((PolygonRoi)roi).getNCoordinates();
					int[] xCoords = ((PolygonRoi)roi).getXCoordinates();
					int[] yCoords = ((PolygonRoi)roi).getYCoordinates();
					shape = new Polygon(xCoords, yCoords, nPoints);
				} else {
					FloatPolygon floatPoly = roi.getFloatPolygon();
					if (floatPoly.npoints <=1) break;
					shape = new GeneralPath(closeShape ? GeneralPath.WIND_EVEN_ODD : GeneralPath.WIND_NON_ZERO, floatPoly.npoints);
					((GeneralPath)shape).moveTo(floatPoly.xpoints[0] - r.x, floatPoly.ypoints[0] - r.y);
					for (int i=1; i<floatPoly.npoints; i++)
						((GeneralPath)shape).lineTo(floatPoly.xpoints[i] - r.x, floatPoly.ypoints[i] - r.y);
					if (closeShape)
						((GeneralPath)shape).closePath();
				}
				break;
			case Roi.POINT:
				ImageProcessor mask = roi.getMask();
				byte[] maskPixels = (byte[])mask.getPixels();
				int maskWidth = mask.getWidth();
				Area area = new Area();
				for (int y=0; y<mask.getHeight(); y++) {
					int yOffset = y*maskWidth;
					for (int x=0; x<maskWidth; x++) {
						if (maskPixels[x+yOffset]!=0)
							area.add(new Area(new Rectangle(x, y, 1, 1)));
					}
				}
				shape = area;
				break;
			case Roi.COMPOSITE: shape = ShapeRoi.cloneShape(((ShapeRoi)roi).getShape());
				break;
			default:
				throw new IllegalArgumentException("Roi type not supported");
		}

		if (shape!=null) {
			setLocation(r.x, r.y);
            Rectangle2D shapeBounds = shape.getBounds2D();
            Rectangle2D.Double sBounds = null;
            if (shapeBounds instanceof Rectangle2D.Double)
				sBounds = (Rectangle2D.Double)shapeBounds;
			else {
				sBounds = new Rectangle2D.Double();
				sBounds.setRect(shapeBounds);  //convert to Rectangle2D.Double
			}
			this.width  = (int)(Math.max(sBounds.x, 0) + sBounds.width + 0.5);
			this.height = (int)(Math.max(sBounds.y, 0) + sBounds.height+ 0.5);
			if (bounds != null) {
				bounds.width = width;
				bounds.height = height;
			}
			this.startX = x;
			this.startY = y;
			//IJ.log("RoiToShape: "+x+" "+y+" "+width+" "+height+" "+bounds);
		}
		return shape;
	}

	/** Constructs a GeneralPath from a float array of segment type+coordinates for each segment. 
	 *  The resulting GeneralPath has winding rule WIND_EVEN_ODD, which is appropriate for closed shapes */
	static GeneralPath makeShapeFromArray(float[] array) {
		if(array==null) return null;
		GeneralPath s = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
		int index=0;
		float[] seg = new float[7];
		while (true) {
			//if(index<array.length)IJ.log(index+" type="+array[index]);
			index = getSegment(array, seg, index);
			if (index<0) break;
			int type = (int)seg[0];
			switch(type) {
				case PathIterator.SEG_MOVETO:
					((GeneralPath)s).moveTo(seg[1], seg[2]);
					break;
				case PathIterator.SEG_LINETO:
					((GeneralPath)s).lineTo(seg[1], seg[2]);
					break;
				case PathIterator.SEG_QUADTO:
					((GeneralPath)s).quadTo(seg[1], seg[2],seg[3], seg[4]);
					break;
				case PathIterator.SEG_CUBICTO:
					((GeneralPath)s).curveTo(seg[1], seg[2], seg[3], seg[4], seg[5], seg[6]);
					break;
				case PathIterator.SEG_CLOSE:
					((GeneralPath)s).closePath();
					break;
				default: break;
			}
		}
		return s;
	}

	/** Reads the data of a segment from an array describing the shape at as in a PathIterator. Reading starts at 'index'.
	 *  The position in the array for the next segment is returned, -1 if the array ends.
	 *  The segment type and coordinates of the segment are stored into 'seg' (which must have a length of at least 7) */
	private static int getSegment(float[] array, float[] seg, int index) {
		int len = array.length;
		if (index>=len) return -1;
		seg[0] = array[index++];
		int type = (int)seg[0];
		int nCoords = nCoords(type);
		if (index+nCoords > len) return -1;
		for (int i=1; i<=nCoords; i++)
			seg[i] = array[index++];
		return index;
	}

	/** Returns the number of coordinates for the given PathIterator segment type */
	private static int nCoords(int segmentType) {
		switch (segmentType) {
				case PathIterator.SEG_MOVETO:
				case PathIterator.SEG_LINETO:
					return 2;
				case PathIterator.SEG_QUADTO:
					return 4;
				case PathIterator.SEG_CUBICTO:
					return 6;
				case PathIterator.SEG_CLOSE:
					return 0;
				default:
					throw new RuntimeException("Invalid Segment Type: "+segmentType);
		}
	}

	/** Saves an Roi so it can be retrieved later using getRois(). Not compatible with the other functions of this class.
	 * @deprecated  Use ShapeRoi(Roi) creator and merge with <code>or(ShapeRoi)</code>. */
	void saveRoi(Roi roi) {
		if (savedRois==null)
			savedRois = new Vector();
		savedRois.addElement(roi);
	}

	/**Converts a Shape into Roi object(s).
	 * <br>This method parses the shape into (possibly more than one) Roi objects 
	 * and returns them in an array.
	 * <br>A simple, &quot;regular&quot; path results in a single Roi following these simple rules:
		<table><col><col><col>
			<thead><tr><th scope=col> Shape type </th><th scope=col> Roi class </th><th scope=col> Roi type </th></tr></thead>
			<tbody>
				<tr><td> java.awt.geom.Rectangle2D.Double </td><td> ij.gui.Roi </td><td> Roi.RECTANGLE </td></tr>
				<tr><td> java.awt.geom.Ellipse2D.Double </td><td> ij.gui.OvalRoi</td><td> Roi.OVAL </td></tr>
				<tr><td> java.awt.geom.Line2D.Double </td><td> ij.gui.Line </td><td> Roi.LINE </td></tr>
				<tr><td> java.awt.Polygon </td>	<td> ij.gui.PolygonRoi </td><td> Roi.POLYGON </td></tr>
			</tbody>
		</table>
	 * <br><br>Each subpath of a <code>java.awt.geom.GeneralPath</code> is converted following these rules:
		<table frame="border"><col><col><col><col><col><col>
			<thead>
				<tr><th rowspan="2" scope=col> Segment<br> types </th><th rowspan="2" scope=col> Number of<br> segments </th>
					<th rowspan="2" scope=col> Closed<br> path </th><th rowspan="2" scope=col> Value of<br> forceAngle </th>
					<th rowspan="2" scope=col> Value of<br> forceTrace </th><th rowspan="2" scope=col> Roi type </th></tr>
			</thead>
			<tbody>
				<tr><td> lines only: </td><td align="center"> 0 </td><td>  </td><td>  </td><td>  </td><td> ShapeRoi.NO_TYPE </td></tr>
				<tr><td>  </td><td align="center"> 1 </td><td>  </td><td>  </td><td>  </td>	<td> ShapeRoi.NO_TYPE </td></tr>
				<tr><td>  </td><td align="center"> 2 </td><td align="center"> Y </td><td>  </td><td>  </td><td> ShapeRoi.NO_TYPE </td></tr>
				<tr><td>  </td><td>  </td><td align="center"> N </td><td>  </td><td>  </td><td> Roi.LINE </td></tr>
				<tr><td>  </td><td align="center"> 3 </td><td align="center"> Y </td><td align="center"> N </td><td>  </td><td> Roi.POLYGON </td></tr>
				<tr><td>  </td><td>  </td><td align="center"> N </td><td align="center"> Y </td><td>  </td><td> Roi.ANGLE </td></tr>
				<tr><td>  </td><td>  </td><td align="center"> N </td><td align="center"> N </td><td>  </td><td> Roi.POLYLINE </td></tr>
				<tr><td>  </td><td align="center"> 4 </td><td align="center"> Y </td><td>  </td<td>  </td><td> Roi.RECTANGLE </td></tr>
				<tr><td>  </td><td>  </td><td align="center"> N </td><td>  </td><td>  </td><td> Roi.POLYLINE </td></tr>
				<tr><td>  </td><td align="center"> &lt;= MAXPOLY </td>	<td align="center"> Y </td><td>  </td><td>  </td><td> Roi.POLYGON </td></tr>
				<tr><td>  </td><td>  </td><td align="center"> N </td><td>  </td><td>  </td><td> Roi.POLYLINE </td></tr>
				<tr><td>  </td><td align="center"> &gt; MAXPOLY </td><td align="center"> Y </td><td>  </td>	<td align="center"> Y </td><td> Roi.TRACED_ROI </td></tr>
				<tr><td>  </td><td>  </td><td>  </td><td>  </td><td align="center"> N </td><td> Roi.FREEROI </td></tr>
				<tr><td>  </td><td>  </td><td align="center"> N </td><td>  </td><td>  </td><td> Roi.FREELINE </td></tr>
				<tr><td> anything<br>else: </td><td align="center"> &lt;= 2 </td><td>  </td><td>  </td><td>  </td><td> ShapeRoi.NO_TYPE </td></tr>
				<tr><td>  </td><td align="center"> &gt; 2 </td><td>  </td><td>  </td><td>  </td><td> ShapeRoi.SHAPE_ROI </td></tr>
			</tbody>
		</table>
	 * @return an array of ij.gui.Roi objects.
	 */
	public Roi[] getRois () {
		if (shape==null)
			return new Roi[0];
		if (savedRois!=null)
			return (Roi[])savedRois.toArray(new Roi[savedRois.size()]);
		ArrayList rois = new ArrayList();
		if (shape instanceof Rectangle2D.Double) {
			Roi r = new Roi((int)((Rectangle2D.Double)shape).getX(), (int)((Rectangle2D.Double)shape).getY(), (int)((Rectangle2D.Double)shape).getWidth(), (int)((Rectangle2D.Double)shape).getHeight());
			rois.add(r);
		} else if (shape instanceof Ellipse2D.Double) {
			Roi r = new OvalRoi((int)((Ellipse2D.Double)shape).getX(), (int)((Ellipse2D.Double)shape).getY(), (int)((Ellipse2D.Double)shape).getWidth(), (int)((Ellipse2D.Double)shape).getHeight());
			rois.add(r);
		} else if (shape instanceof Line2D.Double) {
			Roi r = new ij.gui.Line((int)((Line2D.Double)shape).getX1(), (int)((Line2D.Double)shape).getY1(), (int)((Line2D.Double)shape).getX2(), (int)((Line2D.Double)shape).getY2());
			rois.add(r);
		} else if (shape instanceof Polygon) {
			Roi r = new PolygonRoi(((Polygon)shape).xpoints, ((Polygon)shape).ypoints, ((Polygon)shape).npoints, Roi.POLYGON);
			rois.add(r);
		} else {
			PathIterator pIter;
			if (flatten)
				pIter = getFlatteningPathIterator(shape,flatness);
			else
				pIter = shape.getPathIterator(new AffineTransform());
			parsePath(pIter, ALL_ROIS, rois);
		}
		return (Roi[])rois.toArray(new Roi[rois.size()]);
	}


	/**Attempts to convert this ShapeRoi into a single non-composite Roi.
	 * @return an ij.gui.Roi object or null if it cannot be simplified to become a non-composite roi.
	 */
	public Roi shapeToRoi() {
		if (shape==null || !(shape instanceof GeneralPath))
			return null;
		PathIterator pIter = shape.getPathIterator(new AffineTransform());
		ArrayList rois = new ArrayList();
		parsePath(pIter, ONE_ROI, rois);
		if (rois.size() == 1)
			return (Roi)rois.get(0);
		else
			return null;
	}

	/**Attempts to convert this ShapeRoi into a single non-composite Roi.
	 * For showing as a Roi, one should apply copyAttributes
	 * @return an ij.gui.Roi object, which is either the non-composite roi,
	 * or this ShapeRoi (if such a conversion is not possible) or null if
	 * this is an empty roi.
	 */
	public Roi trySimplify() {
		Roi roi = shapeToRoi();
		return (roi==null) ? this : roi;
	}
	
	/**Implements the rules of conversion from <code>java.awt.geom.GeneralPath</code> to <code>ij.gui.Roi</code>.
	 * @param nSegments The number of segments that compose the path (= number of vertices for a polygon)
	 * @param polygonLength length of polygon in pixels, or NaN if curved segments
	 * @param horizontalVerticalIntOnly Indicates whether the GeneralPath is composed of only vertical and horizontal lines with integer coordinates
	 * @param forceTrace Indicates that closed shapes with <code>horizontalVerticalIntOnly=true</code> should become TRACED_ROIs
	 * @param closed Indicates a closed GeneralPath
	 * @see #shapeToRois()
	 * @return a type flag like Roi.RECTANGLE or NO_TYPE if the type cannot be determined
	 */
	private int guessType(int nSegments, double polygonLength, boolean horizontalVerticalIntOnly, boolean forceTrace, boolean closed) {
		int roiType = Roi.RECTANGLE;
		if (Double.isNaN(polygonLength)) {
			roiType = Roi.COMPOSITE;
		} else {
			// For more segments, they should be longer to qualify for a polygon with handles:
			// The threshold for the average segment length is 4.0 for 4 segments, 16.0 for 64 segments, 32.0 for 256 segments
			boolean longEdges = polygonLength/(nSegments*Math.sqrt(nSegments)) >= 2;
			if (nSegments < 2)
				roiType = NO_TYPE;
			else if (nSegments == 2)
				roiType = closed ? NO_TYPE : Roi.LINE;
			else if (nSegments == 3 && !closed && forceAngle)
				roiType = Roi.ANGLE;
			else if (nSegments == 4 && closed && horizontalVerticalIntOnly && longEdges && !forceTrace && !this.forceTrace)
				roiType = Roi.RECTANGLE;
			else if (closed && horizontalVerticalIntOnly && (!longEdges || forceTrace || this.forceTrace))
				roiType = Roi.TRACED_ROI;
			else if (nSegments <= MAXPOLY || longEdges)
				roiType = closed ? Roi.POLYGON : Roi.POLYLINE;
			else
				roiType = closed ? Roi.FREEROI : Roi.FREELINE;
		}
		//IJ.log("guessType n= "+nSegments+" len="+polygonLength+" longE="+(polygonLength/(nSegments*Math.sqrt(nSegments)) >= 2)+" hvert="+horizontalVerticalIntOnly+" clos="+closed+" -> "+roiType);
		return roiType;
	}

	/**Creates a 'classical' (non-Shape) Roi object based on the arguments.
	 * @see #shapeToRois()
	 * @param xPoints the x coordinates
	 * @param yPoints the y coordinates
	 * @param type the type flag
	 * @return a ij.gui.Roi object or null
	 */
	private Roi createRoi(float[] xPoints, float[] yPoints, int roiType) {
		if (roiType == NO_TYPE || roiType == Roi.COMPOSITE) return null;
		Roi roi = null;
		if (xPoints == null || yPoints == null || xPoints.length != yPoints.length || xPoints.length==0) return null;

		Tools.addToArray(xPoints, (float)getXBase());
		Tools.addToArray(yPoints, (float)getYBase());

		switch(roiType) {
			case Roi.LINE: roi = new ij.gui.Line(xPoints[0],yPoints[0],xPoints[1],yPoints[1]); break;
			case Roi.RECTANGLE:
				double[] xMinMax = Tools.getMinMax(xPoints);
				double[] yMinMax = Tools.getMinMax(yPoints);
				roi = new Roi((int)xMinMax[0], (int)yMinMax[0],
						(int)xMinMax[1] - (int)xMinMax[0], (int)yMinMax[1] - (int)yMinMax[0]);
				break;
			case TRACED_ROI:
				roi = new PolygonRoi(toIntR(xPoints), toIntR(yPoints), xPoints.length, roiType);
				break;
			default:
				roi = new PolygonRoi(xPoints, yPoints, xPoints.length, roiType);
				break;
		}
		return roi;
	}

	/**********************************************************************************/
	/***                                   Geometry                                ****/
	/**********************************************************************************/

	/** Checks whether the center of the specified pixel inside of this ROI's shape boundaries.
	 *  Note the ImageJ convention of 0.5 pixel shift between outline and pixel center,
	 *  i.e., pixel (0,0) is enclosed by the rectangle spanned between (0,0) and (1,1).
	 *  The value slightly below 0.5 is for rounding according to the ImageJ convention
	 *  (which is opposite to that of the java.awt.Shape class):
	 *  In ImageJ, points exactly at the left (right) border are considered outside (inside);
	 *  points exactly on horizontal borders, are considered outside (inside) at the border
	 *  with the lower (higher) y.
	 */
	public boolean contains(int x, int y) {
		if (shape==null) return false;
		return shape.contains(x-this.x+0.494, y-this.y+0.49994);
	}

	/** Returns whether coordinate (x,y) is contained in the Roi.
	 *  Note that the coordinate (0,0) is the top-left corner of pixel (0,0).
	 *  Use contains(int, int) to determine whether a given pixel is contained in the Roi. */
	public boolean containsPoint(double x, double y) {
		if (!super.containsPoint(x, y))
			return false;
		return shape.contains(x-this.x+1e-3, y-this.y+1e-6); //adding a bit to reduce the likelyhood of numerical errors at integers
	}

	/** Returns the perimeter of this ShapeRoi. */
	public double getLength() {
		if (width==0 && height==0)
			return 0.0;
		return parsePath(shape.getPathIterator(new AffineTransform()), GET_LENGTH, null);
	}

	/** Returns a path iterator for this ROI's shape containing no curved (only straight) segments */
	PathIterator getFlatteningPathIterator(Shape s, double fl) {
		return s.getPathIterator(new AffineTransform(),fl);
	}

	/**Length of the control polygon of the cubic B&eacute;zier curve argument, in double precision.*/
	double cplength(CubicCurve2D.Double c) {
		return 	Math.sqrt(sqr(c.ctrlx1-c.x1) + sqr(c.ctrly1-c.y1)) +
				Math.sqrt(sqr(c.ctrlx2-c.ctrlx1) + sqr(c.ctrly2-c.ctrly1)) +
				Math.sqrt(sqr(c.x2-c.ctrlx2) + sqr(c.y2-c.ctrly2));
	}

	/**Length of the control polygon of the quadratic B&eacute;zier curve argument, in double precision.*/
	double qplength(QuadCurve2D.Double c) {
		return  Math.sqrt(sqr(c.ctrlx-c.x1) + sqr(c.ctrly-c.y1)) +
				Math.sqrt(sqr(c.x2-c.ctrlx) + sqr(c.y2-c.ctrly));
	}

	/**Length of the chord between the end points of the cubic B&eacute;zier curve argument, in double precision.*/
	double cclength(CubicCurve2D.Double c) {
		return Math.sqrt(sqr(c.x2-c.x1) + sqr(c.y2-c.y1));
	}

	/**Length of the chord between the end points of the quadratic B&eacute;zier curve argument, in double precision.*/
	double qclength(QuadCurve2D.Double c) {
		return Math.sqrt(sqr(c.x2-c.x1) + sqr(c.y2-c.y1));
	}

	/**Calculates the length of a cubic B&eacute;zier curve specified in double precision.
	 * The algorithm is based on the theory presented in paper <br>
	 * &quot;Jens Gravesen. Adaptive subdivision and the length and energy of B&eacute;zier curves. Computational Geometry <strong>8:</strong><em>13-31</em> (1997)&quot;
	 * implemented using <code>java.awt.geom.CubicCurve2D.Double</code>.
	 * Please visit {@link <a href="http://www.graphicsgems.org/gems.html#gemsiv">Graphics Gems IV</a>} for
	 * examples of other possible implementations in C and C++.
	 */
	double cBezLength(CubicCurve2D.Double c) {
		double l = 0.0;
		double cl = cclength(c);
		double pl = cplength(c);
		if((pl-cl)/2.0 > maxerror) {
			CubicCurve2D.Double[] cc = cBezSplit(c);
			for(int i=0; i<2; i++) l+=cBezLength(cc[i]);
			return l;
		}
		l = 0.5*pl+0.5*cl;
		return l;
	}

	/**Calculates the length of a quadratic B&eacute;zier curve specified in double precision.
	 * The algorithm is based on the theory presented in paper <br>
	 * &quot;Jens Gravesen. Adaptive subdivision and the length and energy of B&eacute;zier curves. Computational Geometry <strong>8:</strong><em>13-31</em> (1997)&quot;
	 * implemented using <code>java.awt.geom.CubicCurve2D.Double</code>.
	 * Please visit {@link <a href="http://www.graphicsgems.org/gems.html#gemsiv">Graphics Gems IV</a>} for
	 * examples of other possible implementations in C and C++.
	 */
	double qBezLength(QuadCurve2D.Double c) {
		double l = 0.0;
		double cl = qclength(c);
		double pl = qplength(c);
		if((pl-cl)/2.0 > maxerror)
		{
			QuadCurve2D.Double[] cc = qBezSplit(c);
			for(int i=0; i<2; i++) l+=qBezLength(cc[i]);
			return l;
		}
		l = (2.0*pl+cl)/3.0;
		return l;
	}

 /**Splits a cubic B&eacute;zier curve in half.
  * @param c A cubic B&eacute;zier curve to be divided
  * @return an array with the left and right cubic B&eacute;zier subcurves
  *
	*/
	CubicCurve2D.Double[] cBezSplit(CubicCurve2D.Double c) {
		CubicCurve2D.Double[] cc = new CubicCurve2D.Double[2];
		for (int i=0; i<2 ; i++) cc[i] = new CubicCurve2D.Double();
		c.subdivide(cc[0],cc[1]);
		return cc;
	}

 /**Splits a quadratic B&eacute;zier curve in half.
  * @param c A quadratic B&eacute;zier curve to be divided
  * @return an array with the left and right quadratic B&eacute;zier subcurves
  *
	*/
	QuadCurve2D.Double[] qBezSplit(QuadCurve2D.Double c) {
		QuadCurve2D.Double[] cc = new QuadCurve2D.Double[2];
		for(int i=0; i<2; i++) cc[i] = new QuadCurve2D.Double();
		c.subdivide(cc[0],cc[1]);
		return cc;
	}

	// c is an array of even length with x0, y0, x1, y1, ... ,xn, yn coordinate pairs
	/**Scales a coordinate array with the size calibration of a 2D image.
	 * The array is modified in place.
	 * @param c Array of coordinates in double precision with a <strong>fixed</strong> structure:<br>
	 * <code>x0,y0,x1,y1,....,xn,yn</code> and with even length of <code>2*(n+1)</code>.
	 * @param pw The x-scale of the image.
	 * @param ph The y-scale of the image.
	 * @param n  number of values in <code>c</code> that should be modified (must be less or equal to the size of <code>c</code>
	 *
	 */
	void scaleCoords(double[] c, int n, double pw, double ph) {
		for(int i=0; i<n;) {
			c[i++]*=pw;
			c[i++]*=ph;
		}
	}

	/** Applies an offset xBase, yBase to the n coordinates, which are supposed to be in x, y, x, y ... sequence */
	static void addOffset(float[] c, int n, float xBase, float yBase) {
		for(int i=0; i<n;) {
			c[i++] += xBase;
			c[i++] += yBase;
		}
	}

	/** Retrieves the end points and control points of the path as a float array. The array 
	 *  contains a sequence of variable length segments that use from from one to seven array elements.
	 *  The first element of a segment is the type as defined in the PathIterator interface. SEG_MOVETO 
	 *  and SEG_LINETO segments also include two coordinates (one end point), SEG_QUADTO segments include four 
	 *  coordinates and SEG_CUBICTO segments include six coordinates (three points).
	 *  Coordinates are with respect to the image bounds, not the Roi bounds. */
	public float[] getShapeAsArray() {
		return getShapeAsArray(shape, (float)getXBase(), (float)getYBase());
	}

	/** Converts a java.awt.Shape to an array of segment types and coordinates.
	 *  'xBase' and 'yBase' are added to the x and y coordinates, respectively. */
	static float[] getShapeAsArray(Shape shape, float xBase, float yBase) {
		if (shape==null) return null;
		PathIterator pIt = shape.getPathIterator(new AffineTransform());
		FloatArray shapeArray = new FloatArray();
		float[] coords = new float[6];
		while (!pIt.isDone()) {
			int segType = pIt.currentSegment(coords);
			shapeArray.add(segType);
			int nCoords = nCoords(segType);
			if (nCoords > 0) {
				addOffset(coords, nCoords, xBase, yBase);
				shapeArray.add(coords, nCoords);
			}
			pIt.next();
		}
		return shapeArray.toArray();
	}

	final static int ALL_ROIS=0, ONE_ROI=1, GET_LENGTH=2; //task types
	final static int NO_SEGMENT_ANY_MORE = -1; //pseudo segment type when closed
	/**Parses the geometry of this ROI's shape by means of the shape's PathIterator;
	 * Depending on the <code>task</code> and <code>rois</code> argument it will:
	 * <br>- create a single non-Shape Roi and add it to <code>rois</code> in case
	 *       there is only one subpath, otherwise add this Roi unchanged to <code>rois</code>
	 *       (task = ONE_ROI and rois non-null)
	 * <br>- add each subpath as a Roi to rois; curved subpaths will be flattened, i.e. converted to a
	 *       polygon approximation (task != ONE_ROI and rois non-null)
	 * <br>- measure the combined length of all subpaths/Rois and return it (task = GET_LENGTH, rois may be null)
	 * @param pIter the PathIterator to be parsed.
	 * @param params an array with one element that will hold the calculated total length of the rois if its initial value is 0.
	 *        If params holds the value SHAPE_TO_ROI, it will be tried to convert this ShapeRoi to a non-composite Roi. If this
	 *        is not possible and this ShapeRoi is not empty, a reference to this ShapeRoi will be returned.
	 * @param rois an ArrayList that will hold ij.gui.Roi objects constructed from subpaths of this path;
	 *        may be null only when <code>task = GET_LENGTH</code>
	 * (see @link #shapeToRois()} for details;
	 * @return Total length if task = GET_LENGTH.*/
	double parsePath(PathIterator pIter, int task, ArrayList rois) {
		if (pIter==null || pIter.isDone())
			return 0.0;
		double pw = 1.0, ph = 1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		float xbase = (float)getXBase();
		float ybase = (float)getYBase();

		FloatArray xPoints = new FloatArray();    //vertex coordinates of current subpath
		FloatArray yPoints = new FloatArray();
		FloatArray shapeArray = new FloatArray(); //values for creating a GeneralPath for the current subpath
		boolean getLength = task == GET_LENGTH;
		int nSubPaths = 0;           // the number of subpaths
		boolean horVertOnly = true; // subpath has only horizontal or vertical lines
		boolean closed = false;
		//boolean success = false;
		float[]  fcoords = new float[6];  // unscaled float coordinates of the path segment
		double[] coords  = new double[6]; // scaled (calibrated) coordinates of the path segment
		double startCalX = 0.0;   // start x of subpath (scaled)
		double startCalY = 0.0;   // start y of subpath (scaled)
		double lastCalX = 0.0;    // x of previous point in the subpath (scaled)
		double lastCalY = 0.0;    // y of previous point in the subpath (scaled)
		double pathLength = 0.0;  // calibrated pathLength/perimeter of current curve
		double totalLength = 0.0; // sum of all calibrated path lengths/perimeters
		double uncalLength = 0.0; // uncalibrated length of polygon, NaN in case of curves
		boolean done = false;
		while (true) {
			int segType = done ? NO_SEGMENT_ANY_MORE : pIter.currentSegment(fcoords);  //read segment (if there is one more)
			int nCoords = 0;               //will be number of coordinates supplied with the segment
			if (!done) {
				nCoords = nCoords(segType);
				if (getLength) {           //make scaled coodinates to calculate the length
					pIter.currentSegment(coords);
					scaleCoords(coords, nCoords, pw, ph);
				}
				pIter.next();
				done = pIter.isDone();
			}

			//IJ.log("segType="+segType+" nCoord="+nCoords+" done="+done+" nPoi="+nPoints+" len="+pathLength);
			if (segType == NO_SEGMENT_ANY_MORE || (segType == PathIterator.SEG_MOVETO && xPoints.size()>0)) {
				// subpath finished: analyze it & create roi if appropriate
				closed = closed || (xPoints.size()>0 && xPoints.get(0) == xPoints.getLast() && yPoints.get(0) == yPoints.getLast());
				float[] xpf = xPoints.toArray();
				float[] ypf = yPoints.toArray();
				if (Double.isNaN(uncalLength) || !allInteger(xpf) || !allInteger(ypf))
					horVertOnly = false;         //allow conversion to rectangle or traced roi only for integer coordinates
				boolean forceTrace = getLength && (!done || nSubPaths>0);  //when calculating the length for >1 subpath, assume traced rois if it can be such
				int roiType = guessType(xPoints.size(), uncalLength, horVertOnly, forceTrace, closed);
				Roi roi = null;
				if (roiType == COMPOSITE && rois != null) { //for ShapeRois with curves, we have the length from the path already, make roi only if needed
					Shape shape = makeShapeFromArray(shapeArray.toArray());  //the curved subpath (image pixel coordinates)
					FloatPolygon fp = getFloatPolygon(shape, FLATNESS, /*separateSubpaths=*/ false, /*addPointForClose=*/ false, /*absoluteCoord=*/ false);
					roi = new PolygonRoi(fp, FREEROI);
				} else if (roiType != NO_TYPE) { //NO_TYPE looks like an empty roi; only return non-empty rois
					roi = createRoi(xpf, ypf, roiType);
				}
				if (rois != null && roi != null)
					rois.add(roi);
				if (task == ONE_ROI) {
					if (rois.size() > 1) {       //we can't make a single roi from this; so we can only keep the roi as it is
						rois.clear();
						rois.add(this);
						return 0.0;
					}
				}
				if (getLength && roi != null && !Double.isNaN(uncalLength)) {
					roi.setImage(imp);           //calibration
					pathLength = roi.getLength();//we don't use the path length of the Shape; e.g. for traced rois ImageJ has a better algorithm
					roi.setImage(null);
				}
				totalLength += pathLength;
			}
			if (segType == NO_SEGMENT_ANY_MORE)  // b r e a k   t h e   l o o p
				return getLength ? totalLength : 0;

			closed = false;
			switch(segType) {
				case PathIterator.SEG_MOVETO:    //we start a new subpath
					xPoints.clear();
					yPoints.clear();
					shapeArray.clear();
					nSubPaths++;
					pathLength = 0;
					startCalX = coords[0];  
					startCalY = coords[1];
					closed = false;
					horVertOnly = true;
					break;
				case PathIterator.SEG_LINETO:
					pathLength += Math.sqrt(sqr(lastCalY-coords[1])+sqr(lastCalX-coords[0]));
					break;
				case PathIterator.SEG_QUADTO:
					if (getLength) {
						QuadCurve2D.Double curve = new QuadCurve2D.Double(lastCalX,lastCalY,coords[0],coords[2],coords[2],coords[3]);
						pathLength += qBezLength(curve);
					}
					uncalLength = Double.NaN;    // not a polygon
					break;
				case PathIterator.SEG_CUBICTO:
					if (getLength) {
						CubicCurve2D.Double curve = new CubicCurve2D.Double(lastCalX,lastCalY,coords[0],coords[1],coords[2],coords[3],coords[4],coords[5]);
						pathLength += cBezLength(curve);
					}
					uncalLength = Double.NaN;    // not a polygon
					break;
				case PathIterator.SEG_CLOSE:
					pathLength += Math.sqrt(sqr(lastCalX-startCalX) + sqr(lastCalY-startCalY));
					fcoords[0] = xPoints.get(0); //destination coordinates; with these we can handle it as SEG_LINETO
					fcoords[1] = yPoints.get(0);
					closed = true;
					break;
				default:
					break;
			}
			if (xPoints.size()>0 && (segType == PathIterator.SEG_LINETO || segType == PathIterator.SEG_CLOSE)) {
				float dx = fcoords[0] - xPoints.getLast();
				float dy = fcoords[1] - yPoints.getLast();
				uncalLength += Math.sqrt(sqr(dx) + sqr(dy));
				if (dx != 0f && dy != 0f) horVertOnly = false;
			}

			if (nCoords > 0) {
				xPoints.add(fcoords[nCoords - 2]); // the last coordinates are the end point of the segment
				yPoints.add(fcoords[nCoords - 1]);
				lastCalX = coords[nCoords - 2];
				lastCalY = coords[nCoords - 1];
			}
			shapeArray.add(segType);
			addOffset(fcoords, nCoords, xbase, ybase); // shift the shape to image origin
			shapeArray.add(fcoords, nCoords(segType));
		}
	}

	/** Non-destructively draws the shape of this object on the associated ImagePlus. */
	public void draw(Graphics g) {
		Color color =  strokeColor!=null? strokeColor:ROIColor;
		boolean isActiveOverlayRoi = !overlay && isActiveOverlayRoi();
		//IJ.log("draw: "+overlay+"  "+isActiveOverlayRoi);
		if (isActiveOverlayRoi) {
			if (color==Color.cyan)
				color = Color.magenta;
			else
				color = Color.cyan;
		}
		if (fillColor!=null) color = fillColor;
		g.setColor(color);
		AffineTransform aTx = (((Graphics2D)g).getDeviceConfiguration()).getDefaultTransform();
		Graphics2D g2d = (Graphics2D)g;
		if (stroke!=null && !isActiveOverlayRoi)
			g2d.setStroke((ic!=null&&ic.getCustomRoi())||isCursor()?stroke:getScaledStroke());
		mag = getMagnification();
		int basex=0, basey=0;
		if (ic!=null) {
			Rectangle r = ic.getSrcRect();
			basex=r.x; basey=r.y;
		}
		aTx.setTransform(mag, 0.0, 0.0, mag, -basex*mag, -basey*mag);
		aTx.translate(getXBase(), getYBase());
		if (fillColor!=null) {
			if (isActiveOverlayRoi) {
				g2d.setColor(Color.cyan);
				g2d.draw(aTx.createTransformedShape(shape));
			} else
				g2d.fill(aTx.createTransformedShape(shape));
		} else
			g2d.draw(aTx.createTransformedShape(shape));
		if (stroke!=null) g2d.setStroke(defaultStroke);
		if (Toolbar.getToolId()==Toolbar.OVAL)
			drawRoiBrush(g);
		if (state!=NORMAL && imp!=null && imp.getRoi()!=null)
			showStatus();
		if (updateFullWindow) 
			{updateFullWindow = false; imp.draw();}
	}

	public void drawRoiBrush(Graphics g) {
		g.setColor(ROIColor);
		int size = Toolbar.getBrushSize();
		if (size==0 || ic==null)
			return;
		int flags = ic.getModifiers();
		if ((flags&16)==0) return; // exit if mouse button up
		int osize = size;
		size = (int)(size*mag);
		Point p = ic.getCursorLoc();
		int sx = ic.screenX(p.x);
		int sy = ic.screenY(p.y);
		int offset = (int)Math.round(ic.getMagnification()/2.0);
		if ((osize&1)==0)
			offset=0; // not needed when brush width even
		g.drawOval(sx-size/2+offset, sy-size/2+offset, size, size);
	}
	
	/**Draws the shape of this object onto the specified ImageProcessor.
	 * <br> This method will always draw a flattened version of the actual shape
	 * (i.e., all curve segments will be approximated by line segments).
	 */
	public void drawPixels(ImageProcessor ip) {
		PathIterator pIter = shape.getPathIterator(new AffineTransform(), flatness);
		float[] coords = new float[6];
		float sx=0f, sy=0f;
		while (!pIter.isDone()) {
			int segType = pIter.currentSegment(coords);
			switch(segType) {
				case PathIterator.SEG_MOVETO:
					sx = coords[0];
					sy = coords[1];
					ip.moveTo(x+(int)sx, y+(int)sy);
					break;
				case PathIterator.SEG_LINETO:
					ip.lineTo(x+(int)coords[0], y+(int)coords[1]);
					break;
				case PathIterator.SEG_CLOSE:
					ip.lineTo(x+(int)sx, y+(int)sy);
					break;
				default: break;
			}
			pIter.next();
		}
	}

	/** Returns this ROI's mask pixels as a ByteProcessor with pixels "in" the mask
	 *  set to white (255) and pixels "outside" the mask set to black (0).
	 *  Takes into account the usual ImageJ convention of 0.5 pxl shift between the outline and pixel
	 *  coordinates; e.g., pixel (0,0) is surrounded by the rectangle spanned between (0,0) and (1,1).
	 *  Note that apart from the 0.5 pixel shift, ImageJ has different convention for the border points
	 *  than the java.awt.Shape class:
	 *  In ImageJ, points exactly at the left (right) border are considered outside (inside);
	 *  points exactly on horizontal borders, are considered outside (inside) at the border
	 *  with the lower (higher) y.
	 *  */
	public ImageProcessor getMask() {
		if (shape==null)
			return null;
		ImageProcessor mask = cachedMask;
		if (mask!=null && mask.getPixels()!=null && mask.getWidth()==width && mask.getHeight()==height)
			return mask;
		/* The following code using Graphics2D.fill would in principle work, but is very inaccurate
		 * at least with Oracle Java 8 or OpenJDK Java 10.
		 * For near-vertical polgon edges of 1000 pixels length, the deviation can be >0.8 pixels in x.
		 * Thus, approximating the shape by a polygon and using the PolygonFiller is more accurate
		 * (and roughly equally fast). --ms Jan 2018 */
		/*BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g2d = bi.createGraphics();
		g2d.setColor(Color.white);
		g2d.transform(AffineTransform.getTranslateInstance(-0.48, -0.49994)); //very inaccurate, only reasonable with "-0.48"
		g2d.fill(shape);
		Raster raster = bi.getRaster();
		DataBufferByte buffer = (DataBufferByte)raster.getDataBuffer();		
		byte[] mask = buffer.getData();
		cachedMask = new ByteProcessor(width, height, mask, null);
		cachedMask.setThreshold(255,255,ImageProcessor.NO_LUT_UPDATE);*/
		FloatPolygon fpoly = getFloatPolygon(FILL_FLATNESS, true, false, false);
		PolygonFiller pf = new PolygonFiller(fpoly.xpoints, fpoly.ypoints, fpoly.npoints, (float)(getXBase()-x), (float)(getYBase()-y));
		mask = pf.getMask(width, height);
		cachedMask = mask;
        return mask;
	}

	/**Returns a reference to the Shape object encapsulated by this ShapeRoi. */
	public Shape getShape() {
		return shape;
	}

	/**Sets the <code>java.awt.Shape</code> object encapsulated by <strong><code>this</code></strong>
	 * to the argument.
	 * <br>This object will hold a (shallow) copy of the shape argument. If a deep copy
	 * of the shape argumnt is required, then a clone of the argument should be passed
	 * in; a possible example is <code>setShape(ShapeRoi.cloneShape(shape))</code>.
	 * @return <strong><code>false</code></strong> if the argument is null.
	 */
	boolean setShape(Shape rhs) {
		boolean result = true;
		if (rhs==null) return false;
		if (shape.equals(rhs)) return false;
		shape = rhs;
		type = Roi.COMPOSITE;
		Rectangle rect = shape.getBounds();
		width = rect.width;
		height = rect.height;
		return true;
	}

	/**Returns the element with the smallest value in the array argument.*/
	private int min(int[] array) {
		int val = array[0];
		for (int i=1; i<array.length; i++) val = Math.min(val,array[i]);
		return val;
	}

	/**Returns the element with the largest value in the array argument.*/
	private int max(int[] array) {
		int val = array[0];
		for (int i=1; i<array.length; i++) val = Math.max(val,array[i]);
		return val;
	}
	
	static ShapeRoi getCircularRoi(int x, int y, int width) {
		return new ShapeRoi(new OvalRoi(x - width / 2, y - width / 2, width, width));
	}

	/** Always returns -1 since ShapeRois do not have handles. */
	public int isHandle(int sx, int sy) {
		   return -1;
	}

	/** Used by the getSelectionCoordinates macro function */
	public FloatPolygon getSelectionCoordinates() {
		return getFloatPolygon(FLATNESS, true, false, true);
	}

	/** Returns a FloatPolygon with all vertices of the flattened shape, i.e., the shape approximated by
	 *  straight line segments. This method is for listing the coordinates and creating the convex hull.
	 *  @param flatness Roughly the maximum allowable distance between the shape and the approximate polygon
	 *  @param separateSubpaths whether individual subpaths should be separated by NaN coordinates
	 *  @param addPointForClose whether the starting point of a closed subpath should be repeated at its end.
	 *   Note that with <code>addPointForClose = false</code>, there is no distinction between open and closed subpaths.
	 *  @param absoluteCoord specifies whether the coordinates should be with respect to image bounds, not Roi bounds. */
	public FloatPolygon getFloatPolygon(double flatness, boolean separateSubpaths, boolean addPointForClose, boolean absoluteCoord) {
		return getFloatPolygon(shape, flatness, separateSubpaths, addPointForClose, absoluteCoord);
	}

	public FloatPolygon getFloatPolygon(Shape shape, double flatness, boolean separateSubpaths, boolean addPointForClose, boolean absoluteCoord) {
		if (shape == null) return null;
		PathIterator pIter = getFlatteningPathIterator(shape, flatness);
		FloatArray xp = new FloatArray();
		FloatArray yp = new FloatArray();
		float[] coords = new float[6];
		int subPathStart = 0;
		while (!pIter.isDone()) {
			int segType = pIter.currentSegment(coords);
			switch(segType) {
				case PathIterator.SEG_MOVETO:
					if (separateSubpaths && xp.size()>0 && !Float.isNaN(xp.get(xp.size()-1))) {
						xp.add(Float.NaN);
						yp.add(Float.NaN);
					}
					subPathStart = xp.size();
				case PathIterator.SEG_LINETO:
					xp.add(coords[0]);
					yp.add(coords[1]);
					break;
				case PathIterator.SEG_CLOSE:
					boolean isClosed = xp.getLast() == xp.get(subPathStart) && yp.getLast() == yp.get(subPathStart);
					if (addPointForClose && !isClosed) {
						xp.add(xp.get(subPathStart));
						yp.add(yp.get(subPathStart));
					} else if (isClosed) {
						xp.removeLast(1);  //remove duplicate point if we should not add point to close the shape
						yp.removeLast(1);
					}
					if (separateSubpaths && xp.size()>0 && !Float.isNaN(xp.get(xp.size()-1))) {
						xp.add(Float.NaN);
						yp.add(Float.NaN);
					}
					break;
				default:
					throw new RuntimeException("Invalid Segment Type: "+segType);
			}
			pIter.next();
		}
		float[] xpf = xp.toArray();
		float[] ypf = yp.toArray();
		if (absoluteCoord) {
			Tools.addToArray(xpf, (float)getXBase());
			Tools.addToArray(ypf, (float)getYBase());
		}
		int n = xpf.length;
		if (n>0 && Float.isNaN(xpf[n-1])) n--; //omit NaN at the end
		return new FloatPolygon(xpf, ypf, n);
	}

	public FloatPolygon getFloatConvexHull() {
		FloatPolygon fp = getFloatPolygon(FLATNESS, /*separateSubpaths=*/ false, /*addPointForClose=*/ false, /*absoluteCoord=*/ true);
		return fp == null ? null : fp.getConvexHull();
	}
	
	public Polygon getPolygon() {
		FloatPolygon fp = getFloatPolygon();
		return new Polygon(toIntR(fp.xpoints), toIntR(fp.ypoints), fp.npoints);
	}

	/** Returns all vertex points of the shape as approximated by polygons,
	 *  in image pixel coordinates */
	public FloatPolygon getFloatPolygon() {
		return getFloatPolygon(FLATNESS, /*separateSubpaths=*/ false, /*addPointForClose=*/ false, /*absoluteCoord=*/ true);
	}

	/** Returns all vertex points of the shape as approximated by polygons,
	 *  where options may include "close" to add points to close each subpath, and
	 *  "separate" to insert NaN values between subpaths (= individual polygons) */
	public FloatPolygon getFloatPolygon(String options) {
		options = options.toLowerCase();
		boolean separateSubpaths = options.indexOf("separate") >= 0;
		boolean addPointForClose = options.indexOf("close") >= 0;
		return getFloatPolygon(FLATNESS, separateSubpaths, addPointForClose, /*absoluteCoord=*/ true);
	}

	/** Retuns the number of vertices, of this shape as approximated by straight lines.
	 *  Note that points might be counted twice where the shape gets closed. */
	public int size() {
		return getPolygon().npoints;
	}

	boolean allInteger(float[] a) {
		for (int i=0; i<a.length; i++)
			if (a[i] != (int)a[i]) return false;
		return true;
	}
}
