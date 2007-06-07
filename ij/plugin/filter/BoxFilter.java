package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.util.*;

/** This plugin implements the Process/Filters/Blur command. It uses the box filter
algorithm described at "http://www.gamasutra.com/features/20010209/evans_01.htm". 
This algorithm's speed is independent of kernel size. It takes the same
time to blur an image no matter how much blurring is required. */
public class BoxFilter implements PlugInFilter {

        static final int BYTE=0, SHORT=1, FLOAT=2, RGB=3;
        
        ImagePlus imp;
        int slice;
        boolean canceled;
        ImageWindow win;
        boolean isLineRoi;
        
        static double radius = 2.0;
        static int boxWidth = 5;
        static int boxHeight = 5;


        public int setup(String arg, ImagePlus imp) {
                IJ.register(BoxFilter.class);
                this.imp = imp;
                slice = 0;
                canceled = false;
                if (imp!=null) {
                        win = imp.getWindow();
                        win.running = true;
						Roi roi = imp.getRoi();
						isLineRoi= roi!=null && roi.getType()>=Roi.LINE;
                }
                if (imp!=null && !showDialog())
                        return DONE;
                else
                        return IJ.setupDialog(imp, DOES_ALL);
        }

        public void run(ImageProcessor ip) {
                if (canceled)
                        return;
                slice++;
                if (win!=null && win.running!=true)
                        {canceled=true; IJ.beep(); return;}

				if (isLineRoi)
					ip.resetRoi();
				int type = getType(ip);
				if (type==RGB)
					blurRGB(ip, boxWidth, boxHeight);
				else {
                	ImageProcessor ip2 = blur(ip, boxWidth, boxHeight);
                	convertBack(ip2, ip, type);
					//new ImagePlus("ip2", ip2).show();
				}
                if (slice>1)
                        IJ.showStatus("Mean: "+": "+slice+"/"+imp.getStackSize());
                if (imp!=null && slice==imp.getStackSize())
                        ip.resetMinAndMax();
        }

	    int getType(ImageProcessor ip) {
                int type;
                if (ip instanceof ByteProcessor)
                        type = BYTE;
                else if (ip instanceof ShortProcessor)
                        type = SHORT;
                else if (ip instanceof FloatProcessor)
                        type = FLOAT;
                else
                        type = RGB;
                return type;
        }

        public void convertBack(ImageProcessor ip2, ImageProcessor ip, int type) {
                switch (type) {
                        case BYTE:
                                ip2 = ip2.convertToByte(false);
                                byte[] pixels = (byte[])ip.getPixels();
                                byte[] pixels2 = (byte[])ip2.getPixels();
                                System.arraycopy(pixels2, 0, pixels, 0, pixels.length);
                                break;
                        case SHORT:
                                ip2 = ip2.convertToShort(false);
                                short[] pixels16 = (short[])ip.getPixels();
                                short[] pixels16b = (short[])ip2.getPixels();
                                System.arraycopy(pixels16b, 0, pixels16, 0, pixels16.length);
                                break;
                        case FLOAT:
                                break;
                }
        }

        boolean showDialog() {
                GenericDialog gd = new GenericDialog("Blur...");
                gd.addNumericField("Kernel Size:", boxWidth, 0);
                gd.showDialog();
                if (gd.wasCanceled()) {
                  canceled = true;
                  return false;
                }
                boxWidth = (int)gd.getNextNumber();
                if (boxWidth<1) boxWidth = 1;
                boxHeight = boxWidth;
                imp.startTiming();
                return true;
        }

		ImageProcessor blur(ImageProcessor ip1, int boxw, int boxh) {
			ip1 = preCalculateSums(ip1);
			//new ImagePlus("sum", ip1).show();
			int width = ip1.getWidth();
			int height = ip1.getHeight();
			float[] pixels1 = (float[])ip1.getPixels();
			float[] pixels2 = new float[width*height];
			double scale = 1.0/((boxw*2.0)*(boxh*2.0));
			double sum;
			for (int y=0; y<height; y++) {
 				for (int x=0; x<width; x++) {
					sum = getPixel(pixels1, width, height, x+boxw, y+boxh)
						+ getPixel(pixels1, width, height, x-boxw, y-boxh)
						- getPixel(pixels1, width, height, x-boxw, y+boxh)
						- getPixel(pixels1, width, height, x+boxw, y-boxh);
					pixels2[x+y*width] = (float)(sum*scale);
				}
			}
			return new FloatProcessor(width, height, pixels2, null);
		}

		/**	Generates a version of the image in which each pixel holds
			the total of all the pixels above and to the left of it. */
		ImageProcessor preCalculateSums(ImageProcessor ip1) {
			int width = ip1.getWidth();
			int height = ip1.getHeight();
			ip1 = ip1.convertToFloat();
			ImageProcessor ip2 = ip1.duplicate();
			float[] pixels1 = (float[])ip1.getPixels();
			float[] pixels2 = (float[])ip2.getPixels();
			float sum;
			int offset;			
			for (int y=0; y<height; y++) {
				offset = y*width;
				for (int x=0; x<width; x++) {
					sum = pixels1[offset];
					if (x>0) sum += pixels2[offset-1];
					if (y>0) sum += pixels2[offset-width];
					if (x>0 && y>0) sum -= pixels2[offset-width-1];
					pixels2[offset] = sum;
					offset++;
				}
			}
			return ip2;
		}

		double getPixel(float[] pixels, int w, int h, int x, int y) {
			if (x<0) x=0; else if (x>=w) x=w-1;
			if (y<0) y=0; else if (y>=h) y=h-1;
			return pixels[x+y*w];
		}

        public void blurRGB(ImageProcessor ip, int boxw, int boxh) {
                int width = ip.getWidth();
                int height = ip.getHeight();
                Rectangle roi = ip.getRoi();
                int size = width*height;
                if (slice==1) IJ.showStatus("Blur... (red)");
                byte[] r = new byte[size];
                byte[] g = new byte[size];
                byte[] b = new byte[size];
                ((ColorProcessor)ip).getRGB(r,g,b);
                ImageProcessor rip = new ByteProcessor(width, height, r, null);
                ImageProcessor gip = new ByteProcessor(width, height, g, null);
                ImageProcessor bip = new ByteProcessor(width, height, b, null);
                ImageProcessor ip2 = blur(rip, boxWidth, boxHeight);
                if (canceled) return;
                ImageProcessor r2 = ip2.convertToByte(false);
                if (slice==1) IJ.showStatus("Blur... (green)");
                ip2 = blur(gip, boxWidth, boxHeight);
                if (canceled) return;
                ImageProcessor g2 = ip2.convertToByte(false);
                if (slice==1) IJ.showStatus("Blur... (blue)");
                ip2 = blur(bip, boxWidth, boxHeight);
                if (canceled) return;
                ImageProcessor b2 = ip2.convertToByte(false);
                ((ColorProcessor)ip).setRGB((byte[])r2.getPixels(), (byte[])g2.getPixels(), (byte[])b2.getPixels());
        }

}