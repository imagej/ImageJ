package ij.process;
import ij.*;
import ij.gui.*;
import java.awt.Rectangle;


/** This class fills polygons using the scan-line filling algorithm
	described at "http://www.cs.rit.edu/~icss571/filling/".

	Note that by ImageJ convention, outline and pixel coordinates are shifted by 0.5:
	Pixel (0,0) is enclosed by the rectangle between (0,0) and (1,1); thus all 0.5
	is added to all polygon coordinates when comparing to pixel coordinates.

	After applying this offset, rounding is done such that points
	exactly on the left boundary are considered outside, points
	exactly on the right boundary inside.
	Points exactly on a horizontal boundary are considered ouside for
	the boundary with lower y and inside for the boundary with the higher y.
	(actually, the boundary is slightly shifted to the left (in x) to ensure
	correct rounding in spite of the final numeric accuracy)
*/
public class PolygonFiller {
	int BLACK=0xff000000, WHITE=0xffffffff;
	int edges; // number of edges
	int activeEdges; // number of  active edges

	// the polygon
	int[] x;                 // x coordinates of polygon vertices (null when using float values)
	int[] y;                 // y coordinates
	float[] xf, yf;          // floating-point coordinates, may be given instead of integer 'x'
	double xOffset, yOffset; // offset that has to be added to xf, yf
	int n;                   // number of coordinates (polygon vertices)

	// edge table
	double[] ex;       // x coordinates
	int[] ey1;         // upper y coordinates
	int[] ey2;         // lower y coordinates
	double[] eslope;   // inverse slopes (1/m)
	int yMin, yMax;    // lowest and highest of all ey1, ey2;

	// sorted edge table (indexes into edge table) (currently not used)
	int[] sedge;

	// active edge table (indexes into edge table)
	int[] aedge;

	/** Constructs a PolygonFiller. */
	public PolygonFiller() {
	}

	/** Constructs a PolygonFiller using the specified polygon with integer coordinates. */
	public PolygonFiller(int[] x, int[] y, int n) {
		setPolygon(x, y, n);
	}

	/** Constructs a PolygonFiller using the specified polygon with floating-point coordinates. */
	public PolygonFiller(float[] xf, float[] yf, int n, double xOffset, double yOffset) {
		setPolygon(xf, yf, n, xOffset, yOffset);
	}

	/** Specifies the polygon to be filled. */
	public void setPolygon(int[] x, int[] y, int n) {
		this.x = x;
		this.y = y;
		this.n = n;
	}

	/** Specifies the polygon to be filled in case of float coordinates.
	 *  In this case, multiple polygons separated by one set of NaN coordinates each.
	 */
	public void setPolygon(float[] xf, float[] yf, int n, double xOffset, double yOffset) {
		x = y = null;
		this.xf = xf;
		this.yf = yf;
		this.n = n;
		this.xOffset = xOffset;
		this.yOffset = yOffset;
	}

	 void allocateArrays(int n) {
		if (ex==null || n>ex.length) {
			ex = new double[n];
			ey1 = new int[n];
			ey2 = new int[n];
			sedge = new int[n];
			aedge = new int[n];
			eslope = new double[n];
		}
	}

	/** Generates the edge table for all non-horizontal lines:
	 *  ey1, ey2: min & max y value
	 *  eslope: inverse slope dx/dy
	 *  ex: x value at ey1, corrected for half-pixel shift between outline&pixel coordinates
	 *  sedge: list of sorted edges is prepared (not sorted yet) */
	void buildEdgeTable() {
		yMin = Integer.MAX_VALUE;
		yMax = Integer.MIN_VALUE;
		edges = 0;
		int polyStart = 0;	  //index where the polygon has started (i.e., 0 unless we have multiple ploygons separated by NaN)
		for (int i=0; i<n; i++) {
			int iplus1 = i==n-1 ? polyStart : i+1;
			if (x != null) {  //using int arrays
				int y1 = y[i];  int y2 = y[iplus1];
				int x1 = x[i];  int x2 = x[iplus1];
				if (y1==y2)
					continue; //ignore horizontal lines
				if (y1>y2) {  //swap ends to ensure y1<y2
					int tmp = y1;
					y1 = y2;  y2 = tmp;
					tmp = x1;
					x1 = x2;  x2 = tmp;
				}
				double slope = (double)(x2 - x1)/(y2 - y1);
				ex[edges] = x1 + 0.5*slope + 1e-8;  // x at the y1 pixel coordinate
				ey1[edges] = y1;
				ey2[edges] = y2;
				eslope[edges] = slope;
				if (y1 < yMin) yMin = y1;
				if (y2 > yMax) yMax = y2;
			} else {          //using float arrays
				if (Float.isNaN(xf[iplus1])) //after the last point, close the polygon
					iplus1 = polyStart;
				if (Float.isNaN(xf[i])) {    //when a new polygon follows, remember the start point for closing it
					polyStart = i + 1;
					continue;
				}
				double y1f = yf[i] + yOffset;  double y2f = yf[iplus1] + yOffset;
				double x1f = xf[i] + xOffset;  double x2f = xf[iplus1] + xOffset;
				int y1 = (int)Math.round(y1f);
				int y2 = (int)Math.round(y2f);
				//IJ.log("x, y="+xf[i]+","+yf[i]+"+ offs="+xOffset+","+yOffset+"->"+x1f+","+y1f+" int="+y1);
				if (y1==y2 || (y1<=0 && y2<=0))
					continue; //ignore horizontal lines or lines that don't reach the first row of pixels
				if (y1>y2) {  //swap ends to ensure y1<y2
					int tmp = y1;
					y1 = y2;  y2 = tmp;
					double ftmp = y1f;
					y1f = y2f;  y2f = ftmp;
					ftmp = x1f;
					x1f = x2f;  x2f = ftmp;
				}
				double slope = (x2f - x1f)/(y2f - y1f);
				ex[edges] = x1f + (y1 - y1f + 0.5)*slope + 1e-8; // x at the y1 pixel coordinate
				ey1[edges] = y1;
				ey2[edges] = y2;
				eslope[edges] = slope;
				if (y1 < yMin) yMin = y1;
				if (y2 > yMax) yMax = y2;
			}
			edges++;
		}
		for (int i=0; i<edges; i++)
			sedge[i] = i;
		activeEdges = 0;
	}

	/** Fills the polygon using the ImageProcessor's current drawing color. */
	public void fill(ImageProcessor ip, Rectangle r) {
		ip.fill(getMask(r.width, r.height));
	}

	/** Returns a byte mask containing a filled version of the polygon. */
	public ImageProcessor getMask(int width, int height) {
		ByteProcessor mask = new ByteProcessor(width, height);
		fillByteProcessorMask(mask);
		return mask;
	}

	/** Fills the ByteProcessor with 255 inside the polygon */
	public void fillByteProcessorMask(ByteProcessor mask) {
		int width = mask.getWidth();
		int height = mask.getHeight();
		byte[] pixels = (byte[])mask.getPixels();
		allocateArrays(n);
		buildEdgeTable();
		//printEdges();
		int x1, x2, offset, index;
		int yStart = yMin>0 ? yMin : 0;
		if (yMin != 0)
			shiftXValuesAndActivate(yStart);
		//IJ.log("yMin="+yMin+" yStart="+yStart+" nActive="+activeEdges);
		for (int y=yStart; y<Math.min(height, yMax+1); y++) {
			removeInactiveEdges(y);
			activateEdges(y);
			offset = y*width;
			for (int i=0; i<activeEdges; i+=2) {
				x1 = (int)(ex[aedge[i]]+0.5);
				if (x1<0) x1=0;
				if (x1>width) x1 = width;
				x2 = (int)(ex[aedge[i+1]]+0.5);
				if (x2<0) x2=0;
				if (x2>width) x2 = width;
				for (int x=x1; x<x2; x++)
					pixels[offset+x] = -1; // 255 (white)
			}
			updateXCoordinates();
		}
	}

	/** Shifts the x coordinates of all edges according to their slopes
	 *  as required for starting at the given y value and prepares the
	 *  list of active edges as it would have resulted from procesing
	 *  the previous lines */
	void shiftXValuesAndActivate(int yStart) {
		for (int i=0; i<edges; i++) {
			int index = sedge[i];
			if (ey1[index] < yStart && ey2[index] >= yStart) {
				ex[index] += eslope[index] * (yStart - ey1[index]);
				aedge[activeEdges++] = index;
			}
		}
		sortActiveEdges();
	}

	/** Updates the x coordinates in the active edges list and sorts the list if necessary. */
	void updateXCoordinates() {
		int index;
		double x1=-Double.MAX_VALUE, x2;
		boolean sorted = true;
		for (int i=0; i<activeEdges; i++) {
			index = aedge[i];
			x2 = ex[index] + eslope[index];
			ex[index] = x2;
			if (x2<x1) sorted = false;
			x1 = x2;
		}
		if (!sorted)
			sortActiveEdges();
	}

	/** Sorts the active edges list by x coordinate using a selection sort. */
	void sortActiveEdges() {
		int min, tmp;
		for (int i=0; i<activeEdges; i++) {
			min = i;
			for (int j=i; j<activeEdges; j++)
				if (ex[aedge[j]] <ex[aedge[min]]) min = j;
			tmp=aedge[min];
			aedge[min] = aedge[i];
			aedge[i]=tmp;
		}
	}

	/** Removes edges from the active edge table that are no longer needed. */
	void removeInactiveEdges(int y) {
		int i = 0;
		while (i<activeEdges) {
			int index = aedge[i];
			if (y<ey1[index] || y>=ey2[index]) {
				for (int j=i; j<activeEdges-1; j++)
					aedge[j] = aedge[j+1];
				activeEdges--;
			} else
				i++;
		}
	}

	/** Adds edges to the active edge table. */
	void activateEdges(int y) {
		for (int i=0; i<edges; i++) {
			int edge =sedge[i];
			if (y==ey1[edge]) {
				int index = 0;
				while (index<activeEdges && ex[edge]>ex[aedge[index]])
					index++;
				for (int j=activeEdges-1; j>=index; j--)
					aedge[j+1] = aedge[j];
				aedge[index] = edge;
				activeEdges++;
			}
		}
	}

	/** Display the contents of the edge table*/
	void printEdges() {
		for (int i=0; i<edges; i++) {
			int index = i;
			IJ.log(i+": x="+IJ.d2s(ex[index])+" y="+ey1[index]+" to "+ey2[index] + " sl=" + IJ.d2s(eslope[index],2) );
		}
	}

	/** Display the contents of the active edge table*/
	void printActiveEdges() {
		for (int i=0; i<activeEdges; i++) {
			int index =aedge[i];
			IJ.log(i+": x="+IJ.d2s(ex[index])+" y="+ey1[index]+" to "+ey2[index] + " sl=" + IJ.d2s(eslope[index],2) );
		}
	}

}
