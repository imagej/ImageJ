package ij.plugin.filter;
import ij.*;
import ij.process.*;

/**
	This plugin mplements the Euclidean Distance Map, Ultimate Eroded Points and
	Watershed Segmentation commands in the Process/Morphology submenu.
*/
public class EDM implements PlugInFilter {

	ImagePlus imp;
	String arg;
	int maxEDM;
	short[] xCoordinate, yCoordinate;
	int[] levelStart;
	int[] levelOffset;
	int[] histogram;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		this.arg = arg;
		return DOES_8G+NO_CHANGES;
	}
	
	public void run(ImageProcessor ip) {
		ImageStatistics stats = imp.getStatistics();
		if (stats.histogram[0]+stats.histogram[255]!=stats.pixelCount) {
			IJ.error("8-bit binary image (0 and 255) required.");
			return;
		}
		ImageProcessor ip2 = makeEDM(ip);
		if (arg.equals("points")) {
			findUltimatePoints(ip2);
			new ImagePlus("Ultimate Points", ip2).show();
		} else
			new ImagePlus("EDM", ip2).show();
	}



	/**	Converts a binary image into a grayscale Euclidean Distance Map
		(EDM). Each foreground (black) pixel in the binary image is
		assigned a value equal to its distance from the nearest
		background (white) pixel.  Uses the two-pass EDM algorithm
		from the "Image Processing Handbook" by John Russ.
	*/
	public ImageProcessor makeEDM (ImageProcessor ip) {
		int  one = 41;
		int  sqrt2 = 58; // ~ 41 * sqrt(2)
		int  sqrt5 = 92; // ~ 41 * sqrt(5)
		int xmax, ymax;
		int offset, rowsize;

		imp.killRoi();
		int width = imp.getWidth();
		int height = imp.getHeight();
		rowsize = width;
		xmax    = width - 3;
		ymax    = height - 3;
		ImageProcessor ip16 = ip.convertToShort(false);
		ip16.multiply(one);
		short[] image16 = (short[])ip16.getPixels();
 
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				offset = x + y * rowsize;
				if (image16[offset] > 0) {
					if ((x<2) || (x>xmax) || (y<2) || (y>ymax))
						setEdgeValue(offset, rowsize, image16, x, y, xmax, ymax);
					else
 						setValue(offset, rowsize, image16);
				}
			} // for x
		} // for y

		for (int y=height-1; y>=0; y--) {
			for (int x=width-1; x>=0; x--) {
 				offset = x + y * rowsize;
				if (image16[offset] > 0) {
					if ((x<2) || (x>xmax) || (y<2) || (y>ymax))
						setEdgeValue (offset, rowsize, image16, x, y, xmax, ymax);
					else
						setValue (offset, rowsize, image16);
				}
			} // for x
 		} // for y

		ImageProcessor ip2 = ip.createProcessor(width, height);
		byte[] image8 = (byte[])ip2.getPixels();
		convertToBytes(width, height, image16, image8);
		return ip2;
 	} // makeEDM()

	void setValue (int offset, int rowsize, short[] image16) {
 		int  one = 41;
		int  sqrt2 = 58; // ~ 41 * sqrt(2)
		int  sqrt5 = 92; // ~ 41 * sqrt(5)
		int  v;
		int r1  = offset - rowsize - rowsize - 2;
		int r2  = r1 + rowsize;
		int r3  = r2 + rowsize;
		int r4  = r3 + rowsize;
		int r5  = r4 + rowsize;
		int min = 32767;

		v = image16[r2 + 2] + one;
		if (v < min)
			min = v;
		v = image16[r3 + 1] + one;
		if (v < min)
			min = v;
		v = image16[r3 + 3] + one;
		if (v < min)
			min = v;
		v = image16[r4 + 2] + one;
		if (v < min)
			min = v;
			
		v = image16[r2 + 1] + sqrt2;
		if (v < min)
			min = v;
		v = image16[r2 + 3] + sqrt2;
		if (v < min)
			min = v;
		v = image16[r4 + 1] + sqrt2;
		if (v < min)
			min = v;
 		v = image16[r4 + 3] + sqrt2;
		if (v < min)
			min = v;

		v = image16[r1 + 1] + sqrt5;
		if (v < min)
			min = v;
		v = image16[r1 + 3] + sqrt5;
		if (v < min)
			min = v;
		v = image16[r2 + 4] + sqrt5;
		if (v < min)
			min = v;
		v = image16[r4 + 4] + sqrt5;
		if (v < min)
			min = v;
		v = image16[r5 + 3] + sqrt5;
		if (v < min)
			min = v;
		v = image16[r5 + 1] + sqrt5;
		if (v < min)
			min = v;
		v = image16[r4] + sqrt5;
		if (v < min)
			min = v;
		v = image16[r2] + sqrt5;
		if (v < min)
			min = v;

		image16[offset] = (short)min;

	} // setValue()

	void setEdgeValue (int offset, int rowsize, short[] image16, int x, int y, int xmax, int ymax) {
		int  one   = 41;
		int  sqrt2 = 58; // ~ 41 * sqrt(2)
		int  sqrt5 = 92; // ~ 41 * sqrt(5)
		int  v;
		int r1 = offset - rowsize - rowsize - 2;
		int r2 = r1 + rowsize;
		int r3 = r2 + rowsize;
		int r4 = r3 + rowsize;
		int r5 = r4 + rowsize;
		int min = 32767;
		int offimage = image16[r3 + 2];

		if (y<2)
			v = offimage + one;
		else
			v = image16[r2 + 2] + one;
		if (v < min)
			min = v;

		if (x<2)
			v = offimage + one;
		else
			v = image16[r3 + 1] + one;
		if (v < min)
			min = v;

		if (x>xmax)
			v = offimage + one;
		else
			v = image16[r3 + 3] + one;
		if (v < min)
			min = v;

		if (y>ymax)
			v = offimage + one;
		else
 			v = image16[r4 + 2] + one;
		if (v < min)
			min = v;

		if ((x<2) || (y<2))
			v = offimage + sqrt2;
		else
			v = image16[r2 + 1] + sqrt2;
		if (v < min)
			min = v;

		if ((x>xmax) || (y<2))
			v = offimage + sqrt2;
		else
			v = image16[r2 + 3] + sqrt2;
		if (v < min)
			min = v;

		if ((x<2) || (y>ymax))
 			v = offimage + sqrt2;
		else
			v = image16[r4 + 1] + sqrt2;
		if (v < min)
			min = v;

		if ((x>xmax) || (y>ymax))
			v = offimage + sqrt2;
		else
			v = image16[r4 + 3] + sqrt2;
		if (v < min)
			min = v;

		if ((x<2) || (y<2))
			v = offimage + sqrt5;
		else
			v = image16[r1 + 1] + sqrt5;
		if (v < min)
			min = v;

		if ((x>xmax) || (y<2))
			v = offimage + sqrt5;
		else
			v = image16[r1 + 3] + sqrt5;
		if (v < min)
			min = v;

		if ((x>xmax) || (y<2))
			v = offimage + sqrt5;
 		else
 			v = image16[r2 + 4] + sqrt5;
		if (v < min)
 			min = v;

		if ((x>xmax) || (y>ymax))
			v = offimage + sqrt5;
		else
			v = image16[r4 + 4] + sqrt5;
		if (v < min)
			min = v;

		if ((x>xmax) || (y>ymax))
			v = offimage + sqrt5;
		else
 			v = image16[r5 + 3] + sqrt5;
		if (v < min)
			min = v;

		if ((x<2) || (y>ymax))
			v = offimage + sqrt5;
		else
			v = image16[r5 + 1] + sqrt5;
		if (v < min)
 			min = v;

		if ((x<2) || (y>ymax))
			v = offimage + sqrt5;
		else
			v = image16[r4] + sqrt5;
		if (v < min)
			min = v;

		if ((x<2) || (y<2))
			v = offimage + sqrt5;
		else
			v = image16[r2] + sqrt5;
		if (v < min)
			min = v;

		image16[offset] = (short)min;
  
	} // setEdgeValue()
	
	void convertToBytes (int width, int height, short[] image16, byte[] image8) {
		int one = 41;
		int v, offset;
		int round = one / 2;
		
		maxEDM = 0;
 		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				offset = x + y * width;
				v = (image16[offset] + round) / one;
				if (v > 255)
					v = 255;
				if (v>maxEDM)
					maxEDM = v;
				image8[offset] = (byte) v;
			} //  end for x
		} // for y
	} // end ConvertToBytes()

	/**	Finds peaks in the EDM that contain pixels equal to or greater than all of their neighbors. */
	public void findUltimatePoints (ImageProcessor ip) {
		int   rowsize, offset, count, x, y;
		int CoordOffset, xmax, ymax;
		boolean setPixel;

		smoothEDM(ip);
		makeCoordinateArrays (ip);
		byte[] image = (byte[])ip.getPixels();
		ImageProcessor ip2 = ip.duplicate();
		byte[] image2 = (byte[])ip2.getPixels();
		int width = ip.getWidth();
		int height = ip.getHeight();
 		rowsize = width;
		xmax = width - 1;
		ymax = height - 1;
		for (int level=maxEDM-1; level>=1; level--) {
			do {
				count = 0;
				for (int i=0; i<histogram[level]; i++) {
					CoordOffset = levelStart[level] + i;
					x = xCoordinate[CoordOffset];
					y = yCoordinate[CoordOffset];
					offset = x + y * rowsize;
					if ((image[offset]&255) != 255) {
						setPixel = false;
						if ((x>0) && (y>0) && ((image[offset-rowsize-1]&255) > level))
							setPixel = true;
						if ((y>0) && ((image[offset-rowsize]&255) > level))
							setPixel = true;
						if ((x<xmax) && (y>0) && ((image[offset-rowsize+1]&255) > level))
							setPixel = true;
						if ((x<xmax) && ((image[offset+1]&255) > level))
							setPixel = true;
						if ((x<xmax) && (y<ymax) && ((image[offset+rowsize+1]&255) > level))
							setPixel = true;
						if ((y<ymax) && ((image[offset+rowsize]&255) > level))
							setPixel = true;
						if ((x>0) && (y<ymax) && ((image[offset+rowsize-1]&255) > level))
							setPixel = true;
						if ((x>0) && ((image[offset-1]&255) > level))
							setPixel = true;
						if (setPixel) {
							image[offset] = (byte)255;
							count++;
          					}
					} // if pixel not 255 */
				} //  for i
			} while (count != 0);
		} //  for

		if (false) {
			for (int i=0; i<width*height; i++) {
				if (((image[i]&255)>0) && ((image[i]&255)<255))
					image2[i] = (byte)0xff;
			}
			//CopyMemory (image, image2, Info->BytesPerRow*Info->nlines);
		} else {
			for (int i=0; i<width*height; i++) {
				if ((image[i]&255)==255)
					image[i] = (byte)0;
			}
		}
	} // findUltimatePoints()


	void smoothEDM (ImageProcessor edm) {
		int rowsize, offset, sum;
		int xmax, ymax;

		byte[] image = (byte[])edm.getPixels();
		ImageProcessor ip2 = edm.duplicate();
		byte[] image2 = (byte[])ip2.getPixels();
		int width = edm.getWidth();
		int height = edm.getHeight();
		rowsize = width;
		xmax = width - 1;
		ymax = height - 1;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				offset = x + y*rowsize;
				if (image2[offset] != 1) {
					sum = image2[offset] * 2;
					if ((x>0) && (y>0))
						sum += image2[offset-rowsize-1];
					if (y > 0)
						sum += image2[offset-rowsize];
					if ((x<xmax) && (y>0))
						sum += image2[offset-rowsize+1];
					if (x<xmax)
						sum += image2[offset+1];
					if ((x<xmax) && (y<ymax))
						sum += image2[offset+rowsize+1];
					if (y<ymax)
						sum += image2[offset+rowsize];
					if ((x>0) && (y<ymax))
						sum += image2[offset+rowsize-1];
					if (x>0)
						sum += image2[offset-1];
					image[offset] = (byte)(sum/10);
				} // if not 1
    		} // for x
		} // for y
	} // SmoothEDM()

	/**
	Generates the xy coordinate arrays that allow pixels at each
	level to be accessed directly without searching through the
	entire image.  This method, suggested by Stein Roervik
	(stein@kjemi.unit.no), greatly speeds up the watershed
	segmentation routine.
	*/
	void makeCoordinateArrays (ImageProcessor edm) {
		int rowsize, offset, v, ArraySize;
 		int width = edm.getWidth();
		int height = edm.getHeight();
		histogram = edm.getHistogram();
		
		ArraySize = 0;
		for (int i=0; i<maxEDM-1; i++)
			ArraySize += histogram[i];
		xCoordinate = new short[ArraySize];
 		yCoordinate = new short[ArraySize];
		byte[] image = (byte[])edm.getPixels();
		offset = 0;
		levelStart = new int[256];
		for (int i=0; i<256; i++) {
			levelStart[i] = offset;
			if ((i>0) && (i<maxEDM))
				offset += histogram[i];
		}

		levelOffset = new int[256];
		rowsize = width;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				v = image[x + y * rowsize]&255;
				if ((v>0) && (v<maxEDM)) {
					offset = levelStart[v] + levelOffset[v];
					xCoordinate[offset] = (short) x;
					yCoordinate[offset] = (short) y;
					levelOffset[v] += 1;
				}
			}
		}
	} // makeCoordinateArrays()

}

