package ij.process;
import java.awt.Rectangle;
import java.awt.Polygon;
import java.awt.geom.Rectangle2D;

/** Used by the Roi classes to return float coordinate arrays and to
	determine if a point is inside or outside of spline fitted selections. */
public class FloatPolygon {
	private Rectangle bounds;
	private float minX, minY, maxX, maxY;

	/** The number of points. */
	public int npoints;

	/* The array of x coordinates. */
	public float xpoints[];

	/* The array of y coordinates. */
	public float ypoints[];

	/** Constructs an empty FloatPolygon. */ 
	public FloatPolygon() {
		npoints = 0;
		xpoints = new float[10];
		ypoints = new float[10];
	}

	/** Constructs a FloatPolygon from x and y arrays. */ 
	public FloatPolygon(float xpoints[], float ypoints[]) {
		if (xpoints.length!=ypoints.length)
			throw new IllegalArgumentException("xpoints.length!=ypoints.length");
		this.npoints = xpoints.length;
		this.xpoints = xpoints;
		this.ypoints = ypoints;
	}

	/** Constructs a FloatPolygon from x and y arrays. */ 
	public FloatPolygon(float xpoints[], float ypoints[], int npoints) {
		this.npoints = npoints;
		this.xpoints = xpoints;
		this.ypoints = ypoints;
	}
		
	/* Constructs a FloatPolygon from a Polygon. 
	public FloatPolygon(Polygon polygon) {
		npoints = polygon.npoints;
		xpoints = new float[npoints];
		ypoints = new float[npoints];
		for (int i=0; i<npoints; i++) {
			xpoints[i] = polygon.xpoints[i];
			ypoints[i] = polygon.ypoints[i];
		}
	}
	*/

	/** Returns 'true' if the point (x,y) is inside this polygon. This is a Java
	 *  version of the remarkably small C program by W. Randolph Franklin at
	 *  http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html#The%20C%20Code
	 * 
	 *  In the absence of numerical errors, x coordinates exactly at a boundary are taken as if
	 *  in the area immediately at the left of the boundary. For horizontal boundaries,
	 *  y coordinates exactly at the border are taken as if in the area immediately above it
	 *  (i.e., in decreasing y direction).
	 *  The ImageJ convention of an offset of 0.5 pixels between pixel centers and outline
	 *  coordinates is not taken into consideration; this method returns the purely
	 *  geometrical relationship.
	 */
	public boolean contains(double x, double y) {
		boolean inside = false;
		for (int i=0, j=npoints-1; i<npoints; j=i++) {
			if (((ypoints[i]>=y)!=(ypoints[j]>=y)) &&
					(x>((double)xpoints[j]-xpoints[i])*((double)y-ypoints[i])/((double)ypoints[j]-ypoints[i])+(double)xpoints[i]))
				inside = !inside;
		}
		return inside;
	}

	public Rectangle getBounds() {
		if (npoints==0)
			return new Rectangle();
		if (bounds==null)
			calculateBounds(xpoints, ypoints, npoints);
		return bounds.getBounds();
	}

	public Rectangle2D.Double getFloatBounds() {
		if (npoints==0)
			return new Rectangle2D.Double();
		if (bounds==null)
			calculateBounds(xpoints, ypoints, npoints);
		return new Rectangle2D.Double(minX, minY, maxX-minX, maxY-minY);
	}

	void calculateBounds(float[] xpoints, float[] ypoints, int npoints) {
		minX = Float.MAX_VALUE;
		minY = Float.MAX_VALUE;
		maxX = Float.MIN_VALUE;
		maxY = Float.MIN_VALUE;
		for (int i=0; i<npoints; i++) {
			float x = xpoints[i];
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			float y = ypoints[i];
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}
		int iMinX = (int)Math.floor(minX);
		int iMinY = (int)Math.floor(minY);
		bounds = new Rectangle(iMinX, iMinY, (int)(maxX-iMinX+0.5), (int)(maxY-iMinY+0.5));
	}

	public void addPoint(float x, float y) {
		if (npoints==xpoints.length) {
			float[] tmp = new float[npoints*2];
			System.arraycopy(xpoints, 0, tmp, 0, npoints);
			xpoints = tmp;
			tmp = new float[npoints*2];
			System.arraycopy(ypoints, 0, tmp, 0, npoints);
			ypoints = tmp;
		}
		xpoints[npoints] = x;
		ypoints[npoints] = y;
		npoints++;
		bounds = null;
	}

	public void addPoint(double x, double y) {
		addPoint((float)x, (float)y);
	}
	
	public FloatPolygon duplicate() {
		int n = this.npoints;
		float[] xpoints = new float[n];
		float[] ypoints = new float[n];
		System.arraycopy(this.xpoints, 0, xpoints, 0, n);
		System.arraycopy(this.ypoints, 0, ypoints, 0, n);	
		return new FloatPolygon(xpoints, ypoints, n);
	}
	
	/* Returns the length of this polygon or line. */
	public double getLength(boolean isLine) {
		double dx, dy;
		double length = 0.0;
		for (int i=0; i<(npoints-1); i++) {
			dx = xpoints[i+1]-xpoints[i];
			dy = ypoints[i+1]-ypoints[i];
			length += Math.sqrt(dx*dx+dy*dy);
		}
		if (!isLine) {
			dx = xpoints[0]-xpoints[npoints-1];
			dy = ypoints[0]-ypoints[npoints-1];
			length += Math.sqrt(dx*dx+dy*dy);
		}
		return length;
	}

	/** Uses the gift wrap algorithm to find the convex hull of all points in
	 *  this FloatPolygon and returns it as a new FloatPolygon.
	 *  The sequence of the points on the convex hull is counterclockwise. */
	public FloatPolygon getConvexHull() {
		float[] xx = new float[npoints];
		float[] yy = new float[npoints];
		int n2 = 0;
		float smallestY = Float.MAX_VALUE;
		for (int i=0; i<npoints; i++) {
			float y = ypoints[i];
			if (y<smallestY)
			smallestY = y;
		}
		float smallestX = Float.MAX_VALUE;
		int p1 = 0;               // find the starting point: among all points with the smallest y, the one with the smallest x
		for (int i=0; i<npoints; i++) {
			float x = xpoints[i];
			float y = ypoints[i];
			if (y==smallestY && x<smallestX) {
				smallestX = x;
				p1 = i;
			}
		}
		int pstart = p1;          // the start point is always on the convex hull
		int count = 0;
		do {
			double x1 = xpoints[p1];
			double y1 = ypoints[p1];
			int p2 = p1;
			double x2=0, y2=0;
			do {
				p2++; if (p2==npoints) p2 = 0;
				if (p2 == p1) break;           // all points have the same coordinates as p1
				x2 = xpoints[p2];              // p2 is a candidate for the convex hull
				y2 = ypoints[p2];
			} while (x2 == x1 && y2 == y1);    // make sure p2 is not identical to p1

			int p3 = p2+1; if (p3==npoints) p3 = 0;
			if (p2 != p1) do {
				double x3 = xpoints[p3];
				double y3 = ypoints[p3];
				//The following is the cross product r12 x r23 = (x2-x1)*(y3-y2) - (y2-y1)*(x3-x2),
				//where r12 and r23 are the vectors from p1 to p2 and p2 to p3, respectively.
				//It is positive if the path from p1 via p2 to p3 bends clockwise at p2
				double determinate = x1*(y2-y3)-y1*(x2-x3)+(y3*x2-y2*x3);
				boolean collinearAndFurther = false;
				if (determinate == 0 && p3 != p2) { //avoid collinear points on the hull: take longest possible side length
					double d2sqr = sqr(x2-x1) + sqr(y2-y1);
					double d3sqr = sqr(x3-x1) + sqr(y3-y1);
					collinearAndFurther = d3sqr > d2sqr;
				}
				if (determinate > 0 || collinearAndFurther) {
					x2=x3; y2=y3; p2=p3;       // p2 is not on the convex hull, p3 becomes the new candidate
				}
				p3 ++; if (p3==npoints) p3 = 0;
			} while (p3 != p1);                // all points have been checked whether they are the next one on the convex hull

			xx[n2] = (float)x1;                // save p1 as a point on the convex hull
			yy[n2] = (float)y1;
			n2++;

			if (p2 == p1) break;               // happens only if there was only one unique point
			p1 = p2;
			if (n2 > 1 && xpoints[p1]==xx[0] && ypoints[p1]==yy[0]) break; //all done but pstart was missed because of duplicate points
		} while (p1!=pstart);
		return new FloatPolygon(xx, yy, n2);
	}

	double sqr(double x) {return x*x;}
	
    double crossProduct(double x1, double y1, double x2, double y2) {
        return (double)x1*y2 - (double)x2*y1;
    }

	public String toString() {
		return "Polygon[npoints="+npoints+"]";
	}
	
}
