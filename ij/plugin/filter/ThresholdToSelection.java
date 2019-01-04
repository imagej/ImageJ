/*
 * This plugin implements the Edit/Selection/Create Selection command.
 * It is based on a proposal by Tom Larkworthy.
 * Written and public domained in June 2006 by Johannes E. Schindelin
*/
package ij.plugin.filter;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.process.*;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;

public class ThresholdToSelection implements PlugInFilter {
	ImagePlus image;
	ImageProcessor ip;
	float min, max;
	int w, h;
	boolean showStatus;
	final static double PROGRESS_FRACTION_OUTLINING = 0.9;  //fraction of progress bar for the first phase (tracing outlines)
	
	public void run(ImageProcessor ip) {
		showStatus = true;
		image.setRoi(convert(ip));
	}
	
	/** Returns a selection created from the thresholded pixels in the
		specified image, or null if there are no thresholded pixels. */
	public static Roi run(ImagePlus imp) {
		ThresholdToSelection tts = new ThresholdToSelection();
		return tts.convert(imp.getProcessor());
	}
	
	/** Returns a selection created from the thresholded pixels in the
		specified image, or null if there are no thresholded pixels. */
	public Roi convert(ImageProcessor ip) {
		this.ip = ip;
		min = (float)ip.getMinThreshold();
		max = (float)ip.getMaxThreshold();
		w = ip.getWidth();
		h = ip.getHeight();
		return getRoi();
	}

	final boolean selected(int x, int y) {
		float v = ip.getf(x,y);
		return v>=min && v<=max;
	}

	/*
	 * This class implements a Cartesian polygon in progress.
	 * The edges are supposed to be parallel to the x or y axis.
	 * It is implemented as a deque to be able to add points to both
	 * sides.
	 */
	static class Outline {
		int[] x, y;
		int first, last, reserved;
		final int GROW = 10;  // default extra (spare) space when enlarging arrays (similar performance with 6-20)

		public Outline() {
			reserved = GROW;
			x = new int[reserved];
			y = new int[reserved];
			first = last = GROW / 2;
		}

		/** Makes sure that enough free space is available at the beginning and end of the list, by enlarging the arrays if required */
		private void needs(int neededAtBegin, int neededAtEnd) {
			if (neededAtBegin > first || neededAtEnd > reserved - last) {
				int extraSpace = Math.max(GROW, Math.abs(x[last-1] - x[first])); //reserve more space for outlines that span large x range
				int newSize = reserved + neededAtBegin + neededAtEnd + extraSpace;
				int newFirst = neededAtBegin + extraSpace/2;
				int[] newX = new int[newSize];
				int[] newY = new int[newSize];
				System.arraycopy(x, first, newX, newFirst, last-first);
				System.arraycopy(y, first, newY, newFirst, last-first);
				x = newX;
				y = newY;
				last += newFirst - first;
				first = newFirst;
				reserved = newSize;
			}
		}

		/** Adds point x, y at the end of the list */
		public void push(int x, int y) {
			if (last-first>=2 && collinear(this.x[last-2], this.y[last-2], this.x[last-1], this.y[last-1], x , y)) {
				this.x[last-1] = x; //replace previous point
				this.y[last-1] = y;
			} else {
				needs(0, 1); //new point
				this.x[last] = x;
				this.y[last] = y;
				last++;
			}
		}

		/** Adds point x, y at the beginning of the list */
		public void shift(int x, int y) {
			if (last-first>=2 && collinear(this.x[first+1], this.y[first+1], this.x[first], this.y[first], x , y)) {
				this.x[first] = x; //replace previous point
				this.y[first] = y;
			} else {
				needs(1, 0); //new point
				first--;
				this.x[first] = x;
				this.y[first] = y;
			}
		}

		/** Merge with another Outline by adding it at the end. Thereafter, the other outline must not be used any more. */
		public void push(Outline o) {
			int size = last - first;
			int oSize = o.last - o.first;
			if (size <= o.first && oSize > reserved - last) { // we don't have enough space in our own array but in that of 'o'
				System.arraycopy(x, first, o.x, o.first - size, size); // so prepend our own data to that of 'o'
				System.arraycopy(y, first, o.y, o.first - size, size);
				x = o.x;
				y = o.y;
				first = o.first - size;
				last = o.last;
				reserved = o.reserved;
			} else {  // append to our own array
				needs(0, oSize);
				System.arraycopy(o.x, o.first, x, last, oSize);
				System.arraycopy(o.y, o.first, y, last, oSize);
				last += oSize;
			}
		}

		/** Merge with another Outline by adding it at the beginning. Thereafter, the other outline must not be used any more. */
		public void shift(Outline o) {
			int size = last - first;
			int oSize = o.last - o.first;
			if (size <= o.reserved - o.last && oSize > first) { // we don't have enough space in our own array but in that of 'o'
				System.arraycopy(x, first, o.x, o.last, size);  // so append our own data to that of 'o'
				System.arraycopy(y, first, o.y, o.last, size);
				x = o.x;
				y = o.y;
				first = o.first;
				last = o.last + size;
				reserved = o.reserved;
			} else {  // prepend to our own array
				needs(oSize, 0);
				first -= oSize;
				System.arraycopy(o.x, o.first, x, first, oSize);
				System.arraycopy(o.y, o.first, y, first, oSize);
			}
		}

		public Polygon getPolygon() {
			// optimize out intermediate points of straight lines (created, e.g., by merging outlines)
			int i, j=first+1;
			for (i=first+1; i+1<last; j++) {
				if (collinear(x[j - 1], y[j - 1], x[j], y[j], x[j + 1], y[j + 1])) {
					// merge i + 1 into i
					last--;
					continue;
				}
				if (i != j) {
					x[i] = x[j];
					y[i] = y[j];
				}
				i++;
			}
			// wraparound
			if (collinear(x[j - 1], y[j - 1], x[j], y[j], x[first], y[first]))
				last--;
			else {
				x[i] = x[j];
				y[i] = y[j];
			}
			if (last - first > 2 && collinear(x[last - 1], y[last - 1], x[first], y[first], x[first + 1], y[first + 1]))
				first++;

			int count = last - first;
			int[] xNew = new int[count];
			int[] yNew = new int[count];
			System.arraycopy(x, first, xNew, 0, count);
			System.arraycopy(y, first, yNew, 0, count);
			return new Polygon(xNew, yNew, count);
		}

		/** Returns whether three points are on one straight line */
		boolean collinear (int x1, int y1, int x2, int y2, int x3, int y3) {
			return (x2-x1)*(y3-y2) == (y2-y1)*(x3-x2);
		}

		public String toString() {
			String res = "[first:" + first + ",last:" + last +
				",reserved:" + reserved + ":";
			if (last > x.length) System.err.println("ERROR!");
			int nmax = 10;	//don't print more coordinates than this
			for (int i = first; i < last && i < x.length; i++) {
				if (last-first > nmax && i-first > nmax/2) {
					i = last - nmax/2;
					res += "...";
					nmax = last-first; //dont check again
				} else
					res += "(" + x[i] + "," + y[i] + ")";
				}
			return res + "]";
		}
	}

	/*
	 * Construct all outlines simultaneously by traversing the rows from top to bottom.
	 * The points are added such that for each pair of consecutive points, the inner
	 * part is on the left, i.e., the outline encloses filled areas in the
	 * counterclockwise direction. The outline of empty areas (holes) runs clockwise.
	 *
	 * thisRow[x + 1] indicates whether the pixel at (x, y) is selected (inside threshold bounds).
	 * prevRow[x + 1] indicates whether the pixel at (x, y - 1) is selected.
	 *
	 * outline[x] is the outline that is currently unclosed at the top-left corner of pixel(x, y);
	 * outline[x + 1] is at the top-right corner of pixel(x, y).
	 *
	 * If the pixel (x, y - 1) has an outline at its bottom and right sides (merging in its
	 * lower right corner) and this outline should continue as left & top edges of pixel (x + 1, y),
	 * <code>xAfterLowerRightCorner</code> is set to x + 1 (the pixel coordinate where this
	 * has to be taken into account), and <code>oAfterLowerRightCorner</code> is the outline that
	 * should continue at the left side of pixel (xAfterLowerRightCorner, y) to higher y.
	 * (Without the code with xAfterLowerRightCorner, etc., this case of 8-connected pixels
	 * would result in disjunct outlines, e.g. a one-pixel-wide line with angle between
	 * 0 and -90 deg would be converted to many separate rectangular segments).
	 */
	Roi getRoi() {
		if (showStatus)
			IJ.showStatus("Converting threshold to selection");
		boolean[] prevRow, thisRow;
		ArrayList polygons = new ArrayList();
		Outline[] outline;
		int progressInc = Math.max(h/50, 1);

		prevRow = new boolean[w + 2];
		thisRow = new boolean[w + 2];
		outline = new Outline[w + 1];

		for (int y = 0; y <= h; y++) {
			boolean[] b = prevRow; prevRow = thisRow; thisRow = b;
			int xAfterLowerRightCorner = -1;	   //x at right of 8-connected (not 4-connected) pixels NW-SE
			Outline oAfterLowerRightCorner = null; //there, continue this outline towards south
			thisRow[1] = y < h ? selected(0, y) : false;
			for (int x = 0; x <= w; x++) {
				if (y < h && x < w - 1)
					thisRow[x + 2] = selected(x + 1, y);  //we need to read one pixel ahead
				else if (x < w - 1)
					thisRow[x + 2] = false;
				//IJ.log(x+","+y+": "+thisRow[x + 1]+(x==xAfterLowerRightCorner ? " Corner" : "")+" left="+outline[x]+(x < w ? " right="+outline[x+1] : ""));
				if (thisRow[x + 1]) {  // i.e., pixel (x,y) is selected
					if (!prevRow[x + 1]) {
						// Upper edge of selected area:
						// - left and right outlines are null: new outline
						// - left null: push (line to left)
						// - right null: shift (line to right), or shift&push (after lower right corner, two borders from one corner)
						// - left == right: close (end of hole above) unless we can continue at the right
						// - left != right: merge (shift) unless we can continue at the right
						if (outline[x] == null) {
							if (outline[x + 1] == null) {
								outline[x + 1] = outline[x] = new Outline();
								outline[x].push(x + 1, y);
								outline[x].push(x, y);
							} else {
								outline[x] = outline[x + 1];  // line from top-right to top-left
								outline[x + 1] = null;
								outline[x].push(x, y);
							}
						} else if (outline[x + 1] == null) {
							if (x == xAfterLowerRightCorner) {
								outline[x + 1] = outline[x];
								outline[x] = oAfterLowerRightCorner;
								outline[x].push(x, y);
								outline[x + 1].shift(x + 1, y);
							} else {
								outline[x + 1] = outline[x];
								outline[x] = null;
								outline[x + 1].shift(x + 1, y);
							}
						} else if (outline[x + 1] == outline[x]) {
							if (x < w - 1 && y < h && x != xAfterLowerRightCorner
									&& !thisRow[x + 2] && prevRow[x + 2]) { //at lower right corner & next pxl deselected
								outline[x] = null;
								//outline[x+1] unchanged
								outline[x + 1].shift(x + 1,y);
								xAfterLowerRightCorner = x + 1;
								oAfterLowerRightCorner = outline[x + 1];
							} else {
								//System.err.println("subtract " + outline[x]);
								polygons.add(outline[x].getPolygon()); // MINUS (add inner hole)
								outline[x + 1] = null;
								outline[x] = (x == xAfterLowerRightCorner) ? oAfterLowerRightCorner : null;
							}
						} else {
							outline[x].shift(outline[x + 1]);		// merge
							for (int x1 = 0; x1 <= w; x1++)
								if (x1 != x + 1 && outline[x1] == outline[x + 1]) {
									outline[x1] = outline[x];        // after merging, replace old with merged
									outline[x + 1] = null;           // no line continues at the right
									outline[x] = (x == xAfterLowerRightCorner) ? oAfterLowerRightCorner : null;
									break;
								}
							if (outline[x + 1] != null)
								throw new RuntimeException("assertion failed");							
						}
					}
					if (!thisRow[x]) {
						// left edge
						if (outline[x] == null)
							throw new RuntimeException("assertion failed");
						outline[x].push(x, y + 1);
					}
				} else {  // !thisRow[x + 1], i.e., pixel (x,y) is deselected
					if (prevRow[x + 1]) {
						// Lower edge of selected area:
						// - left and right outlines are null: new outline
						// - left == null: shift
						// - right == null: push, or push&shift (after lower right corner, two borders from one corner)
						// - right == left: close unless we can continue at the right
						// - right != left: merge (push) unless we can continue at the right
						if (outline[x] == null) {
							if (outline[x + 1] == null) {
								outline[x] = outline[x + 1] = new Outline();
								outline[x].push(x, y);
								outline[x].push(x + 1, y);
							} else {
									outline[x] = outline[x + 1];
									outline[x + 1] = null;
									outline[x].shift(x, y);
							}
						} else if (outline[x + 1] == null) {
							if (x == xAfterLowerRightCorner) {
								outline[x + 1] = outline[x];
								outline[x] = oAfterLowerRightCorner;
								outline[x].shift(x, y);
								outline[x + 1].push(x + 1, y);
							} else {
								outline[x + 1] = outline[x];
								outline[x] = null;
								outline[x + 1].push(x + 1, y);
							}
						} else if (outline[x + 1] == outline[x]) {
							//System.err.println("add " + outline[x]);
							if (x < w - 1 && y < h && x != xAfterLowerRightCorner
									&& thisRow[x + 2] && !prevRow[x + 2]) { //at lower right corner & next pxl selected
								outline[x] = null;
								//outline[x+1] unchanged
								outline[x + 1].push(x + 1,y);
								xAfterLowerRightCorner = x + 1;
								oAfterLowerRightCorner = outline[x + 1];
							} else {
								polygons.add(outline[x].getPolygon());   // PLUS (add filled outline)
								outline[x + 1] = null;
								outline[x] = x == xAfterLowerRightCorner ? oAfterLowerRightCorner : null;
							}
						} else {
							if (x < w - 1 && y < h && x != xAfterLowerRightCorner
									&& thisRow[x + 2] && !prevRow[x + 2]) { //at lower right corner && next pxl selected
								outline[x].push(x + 1, y);
								outline[x + 1].shift(x + 1,y);
								xAfterLowerRightCorner = x + 1;
								oAfterLowerRightCorner = outline[x];
								// outline[x + 1] unchanged (the one at the right-hand side of (x, y-1) to the top)
								outline[x] = null;
							} else {
								outline[x].push(outline[x + 1]);         // merge
								for (int x1 = 0; x1 <= w; x1++)
									if (x1 != x + 1 && outline[x1] == outline[x + 1]) {
										outline[x1] = outline[x];        // after merging, replace old with merged
										outline[x + 1] = null;           // no line continues at the right
										outline[x] = (x == xAfterLowerRightCorner) ? oAfterLowerRightCorner : null;
										break;
									}
								if (outline[x + 1] != null)
									throw new RuntimeException("assertion failed");
							}
						}
					}
					if (thisRow[x]) {
						// right edge
						if (outline[x] == null)
							throw new RuntimeException("assertion failed");
						outline[x].shift(x, y + 1);
					}
				}
			}
			if (y%progressInc==0) {
				if (Thread.currentThread().isInterrupted()) return null;
				if (showStatus)
					IJ.showProgress(y*(PROGRESS_FRACTION_OUTLINING/h));
			}
		}
		
		if (polygons.size()==0)
			return null;
		if (showStatus) IJ.showStatus("Converting threshold to selection...");
		GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
		progressInc = Math.max(polygons.size()/10, 1);
		for (int i = 0; i < polygons.size(); i++) {
			path.append((Polygon)polygons.get(i), false);
			if (Thread.currentThread().isInterrupted()) return null;
			if (showStatus && i%progressInc==0)
				IJ.showProgress(PROGRESS_FRACTION_OUTLINING + i*(1.-PROGRESS_FRACTION_OUTLINING)/polygons.size());
		}

		ShapeRoi shape = new ShapeRoi(path);
		Roi roi = shape!=null?shape.shapeToRoi():null; // try to convert to non-composite ROI
		if (showStatus)
			IJ.showProgress(1.0);
		if (roi!=null)
			return roi;
		else
			return shape;
	}

	public int setup(String arg, ImagePlus imp) {
		image = imp;
		return DOES_8G | DOES_16 | DOES_32 | NO_CHANGES;
	}

	/** Determines whether to show status messages and a progress bar */
	public void showStatus(boolean showStatus) {
		this.showStatus = showStatus;
	}
}
