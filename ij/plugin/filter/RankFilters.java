package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.ContrastEnhancer;
import java.awt.*;
import java.util.*;

/** This plugin implements the Median, Mean, Minimum, Maximum, Variance and Despeckle commands. */
public class RankFilters implements PlugInFilter {

        public static final int MEDIAN=0, MEAN=1, MIN=2, MAX=3, VARIANCE=4, DESPECKLE=5;
        static final int BYTE=0, SHORT=1, FLOAT=2, RGB=3;
        
        ImagePlus imp;
        int filterType = MEDIAN;
        String title;
        int kw, kh;
        int slice;
        boolean canceled;
        private static final String[] typeStrings = {"Median","Mean","Minimum","Maximum","Variance","Median"};
        boolean isLineRoi;
        
        static double radius = 2.0;
        static boolean separable = true;


        public int setup(String arg, ImagePlus imp) {
                IJ.register(RankFilters.class);
                this.imp = imp;
                if (arg.equals("min"))
                        filterType = MIN;
                else if (arg.equals("max"))
                        filterType = MAX;
                else if (arg.equals("mean"))
                        filterType = MEAN;
                else if (arg.equals("variance"))
                        filterType = VARIANCE;
                else if (arg.equals("despeckle"))
                        filterType = DESPECKLE;
                else if (arg.equals("masks")) {
                	showMasks();
                	return DONE;
                }
                slice = 0;
                canceled = false;
                if (imp!=null) {
                        IJ.resetEscape();
						Roi roi = imp.getRoi();
						isLineRoi= roi!=null && roi.isLine();
                }
                title = typeStrings[filterType];
                IJ.showStatus(title+", radius="+radius+" (esc to abort)");
                if (imp!=null && !showDialog())
                        return DONE;
                else
                        return IJ.setupDialog(imp, DOES_ALL);
        }

        public void run(ImageProcessor ip) {
                if (canceled)
                        return;
                slice++;
                if (IJ.escapePressed())
                        {canceled=true; IJ.beep(); return;}

				if (isLineRoi)
					ip.resetRoi();
				if (filterType==MEAN && separable)
					blur(ip, (int)radius);
				else
               		rank(ip, radius, filterType);
                if (slice>1)
                        IJ.showStatus(title+": "+slice+"/"+imp.getStackSize());
                if (imp!=null && slice==imp.getStackSize())
                        ip.resetMinAndMax();
        }


		public void blur(ImageProcessor ip, int radius) {
			float[] kernel = new float[radius*2+1];
			for (int i=0; i<kernel.length; i++)
				kernel[i] = 1f;
			ImageProcessor mask = ip.getMask();
			if (mask!=null) ip.snapshot();
			ip.convolve(kernel, kernel.length, 1);
			ip.convolve(kernel, 1, kernel.length);
			if (mask!=null) ip.reset(mask);
		}
    
     	void showMasks() {
 			int w=150, h=150;
			ImageStack stack = new ImageStack(w, h);
			//for (double r=0.1; r<3; r+=0.01) {
			for (double r=0.5; r<50; r+=0.5) {
				int d = ((int)(r+0.5))*2 + 1;
				int[] mask = createCircularMask(d,r);
				ImageProcessor ip2 = new FloatProcessor(w,h,new int[w*h]);
				ip2.insert(new FloatProcessor(d,d,mask),w/2-d/2,h/2-d/2);
				stack.addSlice("radius="+r+", size="+d, ip2);
			}
			new ImagePlus("Masks", stack).show();
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
                boolean scale = filterType==VARIANCE;
                switch (type) {
                        case BYTE:
                                ip2 = ip2.convertToByte(scale);
                                byte[] pixels = (byte[])ip.getPixels();
                                byte[] pixels2 = (byte[])ip2.getPixels();
                                System.arraycopy(pixels2, 0, pixels, 0, pixels.length);
                                break;
                        case SHORT:
                                ip2 = ip2.convertToShort(scale);
                                short[] pixels16 = (short[])ip.getPixels();
                                short[] pixels16b = (short[])ip2.getPixels();
                                System.arraycopy(pixels16b, 0, pixels16, 0, pixels16.length);
                                break;
                        case FLOAT:
                                break;
                }
        }

        boolean showDialog() {
                if (filterType==DESPECKLE) {
                    radius = 1.0;
                    filterType = MEDIAN;
                    imp.startTiming();
                    return true;                    
                }
                GenericDialog gd = new GenericDialog(title+"...");
                int digits = filterType==MEAN?0:1;
                gd.addNumericField("Radius:", radius, digits, 5, "pixels");
                if (filterType==MEAN)
                	gd.addCheckbox("Separable Square Mask", separable);
                gd.showDialog();
                if (gd.wasCanceled()) {
                  canceled = true;
                  return false;
                }
                radius = gd.getNextNumber();
                if (filterType==MEAN)
                	separable = gd.getNextBoolean();
                if (radius<0.5) radius=0.5;
                imp.startTiming();
                return true;
        }

        public void rank(ImageProcessor ip, double radius, int rankType) {
                int type = getType(ip);
                if (type==RGB) {
                        rankRGB(ip, radius, rankType);
                        return;
                }
                ip.setCalibrationTable(null);
                ImageProcessor ip2 = ip.convertToFloat();
                if (imp!=null)
                	ip2.setRoi(imp.getRoi());
                else
                	ip2.setRoi(ip.getRoi());
                rankFloat(ip2, radius, rankType);
                convertBack(ip2, ip, type);
        }

        public void rankRGB(ImageProcessor ip, double radius, int rankType) {
                int width = ip.getWidth();
                int height = ip.getHeight();
                Roi roi = imp!=null?imp.getRoi():new Roi(ip.getRoi());
                int size = width*height;
                if (slice==1) IJ.showStatus(title+" (red)");
                byte[] r = new byte[size];
                byte[] g = new byte[size];
                byte[] b = new byte[size];
                ((ColorProcessor)ip).getRGB(r,g,b);
                ImageProcessor rip = new ByteProcessor(width, height, r, null);
                ImageProcessor gip = new ByteProcessor(width, height, g, null);
                ImageProcessor bip = new ByteProcessor(width, height, b, null);
                ImageProcessor ip2 = rip.convertToFloat();
                ip2.setRoi(roi);
                rankFloat(ip2, radius, rankType);
                boolean scale = filterType==VARIANCE;
                if (canceled) return;
                ImageProcessor r2 = ip2.convertToByte(scale);
                if (slice==1) IJ.showStatus(title+" (green)");
                ip2 = gip.convertToFloat();
                ip2.setRoi(roi); 
                rankFloat(ip2, radius, rankType);
                if (canceled) return;
                ImageProcessor g2 = ip2.convertToByte(scale);
                if (slice==1) IJ.showStatus(title+" (blue)");
                ip2 = bip.convertToFloat();
                ip2.setRoi(roi); 
                rankFloat(ip2, radius, rankType);
                if (canceled) return;
                ImageProcessor b2 = ip2.convertToByte(scale);
                ((ColorProcessor)ip).setRGB((byte[])r2.getPixels(), (byte[])g2.getPixels(), (byte[])b2.getPixels());
        }

        public void rankFloat(ImageProcessor ip, double radius, int rankType) {
                int width = ip.getWidth();
                int height = ip.getHeight();
				Rectangle r = ip.getRoi();
				boolean isRoi = r.width!=width||r.height!=height;
				boolean nonRectRoi = ip.getMask()!=null;
				int x1=0, y1=0, x2=width-1, y2=height-1;
        		if (isRoi) {
        			x1 = r.x;
        			y1 = r.y;
        			x2 = x1 + r.width - 1;
        			y2 = y1 + r.height - 1;
        		}
        		if (nonRectRoi)
					ip.snapshot();
        		int kw = ((int)(radius+0.5))*2 + 1;
                int kh = kw;
                int[] mask = createCircularMask(kw,radius);
                int maskSize = 0;
                for (int i=0; i<kw*kw; i++)
                        if (mask[i]!=0)
                                maskSize++;
                float values[] = new float[maskSize];
                int uc = kw/2;
                int vc = kh/2;
                float[] pixels = (float[])ip.getPixels();
                float[] pixels2 = (float[])ip.getPixelsCopy();
                int progress = Math.max((y2-y1)/50,1);
                double sum;
                int offset, i, count;
                boolean edgePixel;
				int xedge = width-uc;
				int yedge = height-vc;
                for(int y=y1; y<=y2; y++) {
                        if (y%progress ==0) {
                                IJ.showProgress((double)y/height);
                                canceled = IJ.escapePressed();
                                if (canceled)
                                        break;
                        }
                        for(int x=x1; x<=x2; x++) {
                                sum = 0.0;
                                i = 0;
                                count = 0;
								edgePixel = y<vc || y>=yedge || x<uc || x>=xedge;
                                for(int v=-vc; v <= vc; v++) {
                                        offset = x+(y+v)*width;
                                        for(int u = -uc; u <= uc; u++) {
                                                if (mask[i++]!=0) {
                                                        if (edgePixel)
                                                               values[count] = getPixel(x+u, y+v, pixels2, width, height);
                                                        else
                                                                values[count] = pixels2[offset+u];
                                                        count++;
                                                }
                                        }
                                }
                                switch (rankType) {
                                	case MEDIAN:
                                    	pixels[x+y*width] = findMedian(values);
                                    	break;
                                	case MEAN:
                                   		pixels[x+y*width] = findMean(values);
                                   		break;
                               		case MIN:
                                    	pixels[x+y*width] = findMin(values);
                                    	break;
                               		case MAX:  
                                    	pixels[x+y*width] = findMax(values);
                                    	break;
                               		case VARIANCE:  
                                    	pixels[x+y*width] = findVariance(values);
                                    	break;
                                    default:
                                    	break;
                                }
               		    }
        		}
				if (nonRectRoi)
					ip.reset(ip.getMask());
                IJ.showProgress(1.0);
                if (canceled) {
                        //ip.reset();
                        ip.insert(new FloatProcessor(width,height,pixels2,null), 0, 0);
                        IJ.beep();
                } else if (rankType==VARIANCE) {
                	ContrastEnhancer ce = new ContrastEnhancer();
                	ce.stretchHistogram(ip, 0.5);
                }
         }

        private float getPixel(int x, int y, float[] pixels, int width, int height) {
                if (x<=0) x = 0;
                if (x>=width) x = width-1;
                if (y<=0) y = 0;
                if (y>=height) y = height-1;
                return pixels[x+y*width];
        }

		/*
		public int[] createCircularMask(int width, double radius) {
			int[] mask = new int[width*width];
			double r = width/2.0-0.5;
			double r2 = radius*radius + 1;
			for (double x=-r; x<=r; x++)
				for (double y=-r; y<=r; y++) {
					int index= (int)((r+x)+width*(r+y));
					if (((x*x+y*y)<r2) && (index<width*width))
						mask[index]=1;
				}
			return mask;
		}
		*/

        int[] createCircularMask(int width, double radius) {
        		if (radius>=1.5 && radius<1.75)
        			radius = 1.75;
        		else if (radius>=2.5 && radius<2.85)
        			radius = 2.85;
                int[] mask = new int[width*width];
                int r = width/2;
                int r2 = (int) (radius*radius) + 1;
                for (int x=-r; x<=r; x++)
                        for (int y=-r; y<=r; y++)
                                if ((x*x+y*y)<=r2)
                                        mask[r+x+(r+y)*width]=1;

                return mask;
        }
 
		// Modified algorithm  according to http://www.geocities.com/zabrodskyvlada/3alg.html
		// Contributed by Heinz Klar.
        private final float findMedian(float[] a) {
                final int nValues = a.length;
                final int nv1b2 = (nValues-1)/2;
                int i,j;
                int l=0;
                int m=nValues-1;
                float med=a[nv1b2];
                float dum ;

                while (l<m) {
                   i=l ;
                   j=m ;
                   do {
                     while (a[i]<med) i++ ;
                     while (med<a[j]) j-- ;
                     dum=a[j];
                     a[j]=a[i];
                     a[i]=dum;
                     i++ ; j-- ;
                   } while ((j>=nv1b2) && (i<=nv1b2)) ;
                  if (j<nv1b2) l=i ;
                  if (nv1b2<i) m=j ;
                  med=a[nv1b2] ;
               }
               return med ;
        }

        private final float findMin(float[] values) {
                float min = values[0];
                for (int i=1; i<values.length; i++)
                        if (values[i]<min)
                                min = values[i];
                return min;
        }

        private final float findMax(float[] values) {
                float max = values[0];
                for (int i=1; i<values.length; i++)
                        if (values[i]>max)
                                max = values[i];
                return max;
        }

        private final float findMean(float[] values) {
                float sum = values[0];
                for (int i=1; i<values.length; i++)
                        sum += values[i];
                return (float)(sum/values.length);
        }

		private final float findVariance(float[] values) {
			double v, sum=0.0, sum2=0.0;
			float min = findMin(values);
			int n = values.length;
			for (int i=1; i<n; i++) {
				v = values[i] - min;
				sum += v;
				sum2 += v*v;
			} 
			double variance = (n*sum2-sum*sum)/n;
			return (float)variance;			
		}

}


