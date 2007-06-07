import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.Rectangle;
import ij.plugin.filter.*;


public class Polygon_Filler implements PlugInFilter {
	ImagePlus imp;
   
	public int setup(String arg, ImagePlus imp) {
		 this.imp = imp;
		 return DOES_ALL;
	}

	public void run(ImageProcessor ip) {
		ImagePlus imp = IJ.getImage();
		Roi roi = imp.getRoi();
		if (roi==null || !(roi instanceof PolygonRoi)) {
			IJ.showMessage("Polygon Filler", "This plugin requires a polygonal selection.");
			return;
		}
		PolygonRoi proi = (PolygonRoi)roi;
		PolygonFiller pf = new PolygonFiller (proi.getXCoordinates(), proi.getYCoordinates(), proi.getNCoordinates());
		Rectangle r = roi.getBounds();
		//new ImagePlus("Mask",  new ColorProcessor(r.width, r.height, pf.createMask(r))).show();
		for (int i=0; i<2500; i++)
			pf.fill(ip, r);
	 }
}

class PolygonFiller {
	int BLACK=0xff000000, WHITE=0xffffffff;
	int edges; // number of edges
	int activeEdges; // number of  active edges

	// the polygon
	int[] x; // x coordinates
	int[] y; // y coordinates
	int n;  // number of coordinates

	// edge table
	double[] ex;	// x coordinates
	int[] ey1;	// upper y coordinates
	int[] ey2;	// lower y coordinates
	double[] eslope;   // inverse slopes (1/m)

	// sorted edge table (indexes into edge table) (currently not used)
	int[] sedge;

	// active edge table (indexes into edge table)
	int[] aedge; 

	/** Constructs a PolygonFiller. */
	PolygonFiller() {
	 }

	/** Constructs a PolygonFiller using the specified polygon. */
	PolygonFiller(int[] x, int[] y, int n) {
		setPolygon(x, y, n);
	 }

	/** Specifies the polygon to be filled. */
	public void setPolygon(int[] x, int[] y, int n) {
		this.x = x;
		this.y = y;
		this.n = n;
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

	void buildEdgeTable(int[] x, int[] y, int n) {
		int length, iplus1, x1, x2, y1, y2;
		edges = 0;
		for (int i=0; i<n; i++) {
			iplus1 = i==n-1?0:i+1;
			y1 = y[i];	y2 = y[iplus1];
			x1 = x[i];	x2 = x[iplus1];
			if (y1==y2)
				continue; //ignore horizontal lines
			if (y1>y2) { // swap ends
				int tmp = y1;
				y1=y2; y2=tmp;
				tmp=x1;
				x1=x2; x2=tmp;
			}
			ex[edges] = x1;
			ey1[edges] = y1;
			ey2[edges] = y2;
			eslope[edges] = (double)(x2-x1)/(y2-y1);
			edges++;   
		}
		for (int i=0; i<edges; i++)
			sedge[i] = i;
		activeEdges = 0;
		//quickSort(sedge);
	}


	void addToSortedTable(int edge) {
		int index = 0;
		while (index<edges && ey1[edges]>ey1[sedge[index]]) {
			index++;
		}
		for (int i=edges-1; i>=index; i--) {
			sedge[i+1] = sedge[i];
			//IJ.log((i+1)+"="+i);
		}
		sedge[index] = edges;
	}

	public void fill(ImageProcessor ip, Rectangle r) {
		ip.fill(createMask(r));
	}

	public int[] createMask(Rectangle r) {
		allocateArrays(n);
		buildEdgeTable(x, y, n);
		//printEdges();
		int x1, x2, offset, index;
		int width = r.width, height = r.height;
		int size = width*height;
		int[] mask = new int[size];
		for (int i=0; i<size; i++) 
			mask[i] = WHITE;
		for (int y=0; y<height; y++) {
			removeInactiveEdges(y);
			activateEdges(y);
			offset = y*width;
			for (int i=0; i<activeEdges; i+=2) {
				x1 = (int)Math.round(ex[aedge[i]]);
				//if (x1<0) x1=0;
				//if (x1>=width) x1 = width;
				x2 = (int)Math.round(ex[aedge[i+1]]);
				//if (x2<0) x2=0; 
				//if (x2>=width) x2 = width;
				//IJ.log(y+" "+x1+"  "+x2);
				 for (int x=x1; x<x2; x++)
					 if (offset+x>0 && offset+x<mask.length) mask[offset+x] = BLACK;
			}			
			updateXCoordinates(y);
		}
		return mask;
	}	

	/** Updates the x coordinates in the active edges list and sorts the list if necessary. */
	void updateXCoordinates(int y) {
		int index;
		double x1=-Double.MAX_VALUE, x2;
		boolean sorted = true;
		for (int i=0; i<activeEdges; i++) {
			index = aedge[i];
			 x2 = ex[index] + eslope[index];
			ex[index] = x2;
			if (x2<x1) sorted = false;
			//IJ.log(y+" "+IJ.d2s(x1,2)+"  "+IJ.d2s(x2,2)+" "+sorted);
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

	void printEdges() {
		for (int i=0; i<edges; i++) {
			int index = sedge[i];
			IJ.log(i+"	"+ex[index]+"  "+ey1[index]+"  "+ey2[index] + "  " + IJ.d2s(eslope[index],2) );
		}
	}

	void printActiveEdges() {
		for (int i=0; i<activeEdges; i++) {
			int index =aedge[i];
			IJ.log(i+"	"+ex[index]+"  "+ey1[index]+"  "+ey2[index] );
		}
	}

	void quickSort(int[] a) {
		quickSort(a, 0, a.length-1);
	}
	
	void quickSort(int[] a, int from, int to) {
		int i=from, j=to;
		int center = a[(from+to)/2];
		do {
			//while ( i < to && center.compareTo(a[i]) > 0 ) i++;
			while (i<to && ey1[center]>ey1[a[i]]) j--;
			//while ( j > from && center.compareTo(a[j]) < 0 ) j--;
			while (j>from && ey1[center]<ey1[a[j]]) j--;
			if (i < j) {int temp = a[i]; a[i] = a[j]; a[j] = temp;}
			if (i <= j) { i++; j--; }
		} while(i <= j);
		if (from < j) quickSort(a, from, j);
		if (i < to) quickSort(a,  i, to);
	}

}
