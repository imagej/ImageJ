package ij.process;
import java.awt.Rectangle;

/** Used by the Roi classes to return float coordinate arrays and to
	determine if a point is inside or outside of spline fitted selections. */
public class FloatPolygon {
	Rectangle bounds;

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
		
	/** Returns 'true' if the point (x,y) is inside this polygon. This is a Java
	version of the remarkably small C program by W. Randolph Franklin at
	http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html#The%20C%20Code
	*/
	public boolean contains(float x, float y) {
		boolean inside = false;
		for (int i=0, j=npoints-1; i<npoints; j=i++) {
			if (((ypoints[i]>y)!=(ypoints[j]>y)) &&
			(x<(xpoints[j]-xpoints[i])*(y-ypoints[i])/(ypoints[j]-ypoints[i])+xpoints[i]))
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

	void calculateBounds(float[] xpoints, float[] ypoints, int npoints) {
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = Float.MIN_VALUE;
		float maxY = Float.MIN_VALUE;
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

}
