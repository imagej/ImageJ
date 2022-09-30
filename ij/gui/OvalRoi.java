package ij.gui;
import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;
import ij.*;
import ij.process.*;
import ij.measure.Calibration;

/** Oval region of interest */
public class OvalRoi extends Roi {

	/** Creates an OvalRoi.*/
	public OvalRoi(int x, int y, int width, int height) {
		super(x, y, width, height);
		type = OVAL;
	}

	/** Creates an OvalRoi using double arguments.*/
	public OvalRoi(double x, double y, double width, double height) {
		super(x, y, width, height);
		type = OVAL;
	}
	
	/** Creates an OvalRoi. */
	public static OvalRoi create(double x, double y, double width, double height) {
		return new OvalRoi(x, y, width, height);
	}

	/** Starts the process of creating a user-defined OvalRoi. */
	public OvalRoi(int x, int y, ImagePlus imp) {
		super(x, y, imp);
		type = OVAL;
	}

	/** @deprecated */
		public OvalRoi(int x, int y, int width, int height, ImagePlus imp) {
		this(x, y, width, height);
		setImage(imp);
	}

	/** Feret (caliper width) values, see ij.gui.Roi.getFeretValues().
	 *  The superclass method of calculating this via the convex hull is less accurate for the MinFeret
	 *  because it does not get the exact minor axis. */
	public double[] getFeretValues() {
		double[] a = new double[FERET_ARRAYSIZE];
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		boolean highAspect = ph*height > pw*width;
		a[0] = highAspect ? height*ph : width*pw;            // (max)Feret
		a[1] = highAspect ?    90.0   :   0.0;               // (max)Feret angle
		a[2] = highAspect ? width*pw  : height*ph;           //  MinFeret
		a[3] = (x + (highAspect ? 0.5*width : 0)) * pw;      //FeretX scaled
		a[4] = (y + (highAspect ? height : 0.5*height)) * ph;//FeretY scaled
		int i = FERET_ARRAY_POINTOFFSET;
		a[i++] = x + (highAspect ? 0.5*width : 0);           //MaxFeret start
		a[i++] = y + (highAspect ? height : 0.5*height);
		a[i++] = x + (highAspect ? 0.5*width : width);       //MaxFeret end
		a[i++] = y + (highAspect ? 0 : 0.5*height);
		a[i++] = x + (highAspect ? 0 : 0.5*width);           //MinFeret start
		a[i++] = y + (highAspect ? 0.5*height : height);
		a[i++] = x + (highAspect ? width : 0.5*width);       //MinFeret end
		a[i++] = y + (highAspect ? 0.5*height : 0);
		return a;
	}

	protected void moveHandle(int sx, int sy) {
		double asp;
		if (clipboard!=null) return;
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		//IJ.log("moveHandle: "+activeHandle+" "+ox+" "+oy);
		int x1=x, y1=y, x2=x+width, y2=y+height, xc=x+width/2, yc=y+height/2;
		int w2 = (int)(0.14645*width);
		int h2 = (int)(0.14645*height);
		if (width > 7 && height > 7) {
			asp = (double)width/(double)height;
			asp_bk = asp;
		} else
			asp = asp_bk;
		switch (activeHandle) {
			case 0: x=ox-w2; y=oy-h2; break;
			case 1: y=oy; break;
			case 2: x2=ox+w2; y=oy-h2; break;
			case 3: x2=ox; break;			
			case 4: x2=ox+w2; y2=oy+h2; break;
			case 5: y2=oy; break;
			case 6: x=ox-w2; y2=oy+h2; break;
			case 7: x=ox; break;
		}
		//if (x<0) x=0; if (y<0) y=0;
		if (x<x2)
		   width=x2-x;
		else
		  {width=1; x=x2;}
		if (y<y2)
		   height = y2-y;
		else
		   {height=1; y=y2;}
		if (center) {
			switch(activeHandle){
				case 0:
					width=(xc-x)*2;
					height=(yc-y)*2;
					break;
				case 1:
					height=(yc-y)*2;
					break;
				case 2:
					width=(x2-xc)*2;
					x=x2-width;
					height=(yc-y)*2;
					break;
				case 3:
					width=(x2-xc)*2;
					x=x2-width;
					break;
				case 4:
					width=(x2-xc)*2;
					x=x2-width;
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 5:
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 6:
					width=(xc-x)*2;
					height=(y2-yc)*2;
					y=y2-height;
					break;
				case 7:
					width=(xc-x)*2;
					break;
			}
			if (x>=x2) {
				width=1;
				x=x2=xc;
			}
			if (y>=y2) {
				height=1;
				y=y2=yc;
			}

		}

		if (constrain) {
			if (activeHandle==1 || activeHandle==5) width=height;
			else height=width;
			
			if (x>=x2) {
				width=1;
				x=x2=xc;
			}
			if (y>=y2) {
				height=1;
				y=y2=yc;
			}
			switch(activeHandle){
				case 0:
					x=x2-width;
					y=y2-height;
					break;
				case 1:
					x=xc-width/2;
					y=y2-height;
					break;
				case 2:
					y=y2-height;
					break;
				case 3:
					y=yc-height/2;
					break;
				case 5:
					x=xc-width/2;
					break;
				case 6:
					x=x2-width;
					break;
				case 7:
					y=yc-height/2;
					x=x2-width;
					break;
			}
			if (center){
				x=xc-width/2;
				y=yc-height/2;
			}
		}

		if (aspect && !constrain) {
			if (activeHandle==1 || activeHandle==5) width=(int)Math.rint((double)height*asp);
			else height=(int)Math.rint((double)width/asp);

			switch (activeHandle) {
				case 0:
					x=x2-width;
					y=y2-height;
					break;
				case 1:
					x=xc-width/2;
					y=y2-height;
					break;
				case 2:
					y=y2-height;
					break;
				case 3:
					y=yc-height/2;
					break;
				case 5:
					x=xc-width/2;
					break;
				case 6:
					x=x2-width;
					break;
				case 7:
					y=yc-height/2;
					x=x2-width;
					break;
			}
			if (center) {
				x=xc-width/2;
				y=yc-height/2;
			}
			// Attempt to preserve aspect ratio when roi very small:
			if (width<8) {
				if (width<1) width = 1;
				height=(int)Math.rint((double)width/asp_bk);
			}
			if (height<8) {
				if (height<1) height =1;
				width=(int)Math.rint((double)height*asp_bk);
			}
		}

		updateClipRect();
		imp.draw(clipX, clipY, clipWidth, clipHeight);
		oldX=x; oldY=y;
		oldWidth=width; oldHeight=height;
		cachedMask = null;
		bounds = null;
	}

	public void draw(Graphics g) {
		Color color =  strokeColor!=null? strokeColor:ROIColor;
		if (fillColor!=null) color = fillColor;
		g.setColor(color);
		mag = getMagnification();
		int sw = (int)(width*mag);
		int sh = (int)(height*mag);
		int sx1 = screenX(x);
		int sy1 = screenY(y);
		if (subPixelResolution() && bounds!=null) {
			sw = (int)(bounds.width*mag);
			sh = (int)(bounds.height*mag);
			sx1 = screenXD(bounds.x);
			sy1 = screenYD(bounds.y);
		}
		int sw2 = (int)(0.14645*width*mag);
		int sh2 = (int)(0.14645*height*mag);
		int sx2 = sx1+sw/2;
		int sy2 = sy1+sh/2;
		int sx3 = sx1+sw;
		int sy3 = sy1+sh;
		Graphics2D g2d = (Graphics2D)g;
		if (stroke!=null) 
			g2d.setStroke(getScaledStroke());
		setRenderingHint(g2d);
		if (fillColor!=null) {
			if (!overlay && isActiveOverlayRoi()) {
				g.setColor(Color.cyan);
				g.drawOval(sx1, sy1, sw, sh);
			} else
				g.fillOval(sx1, sy1, sw, sh);
		} else
			g.drawOval(sx1, sy1, sw, sh);
		if (clipboard==null && !overlay) {
			drawHandle(g, sx1+sw2, sy1+sh2);
			drawHandle(g, sx3-sw2, sy1+sh2);
			drawHandle(g, sx3-sw2, sy3-sh2);
			drawHandle(g, sx1+sw2, sy3-sh2);
			drawHandle(g, sx2, sy1);
			drawHandle(g, sx3, sy2);
			drawHandle(g, sx2, sy3);
			drawHandle(g, sx1, sy2);
		}
		drawPreviousRoi(g);
		if (updateFullWindow)
			{updateFullWindow = false; imp.draw();}
		if (state!=NORMAL) showStatus();
	}

	/** Draws an outline of this OvalRoi on the image. */
	public void drawPixels(ImageProcessor ip) {
		Polygon p = getPolygon();
		if (p.npoints>0) {
			int saveWidth = ip.getLineWidth();
			if (getStrokeWidth()>1f)
				ip.setLineWidth((int)Math.round(getStrokeWidth()));
			ip.drawPolygon(p);
			ip.setLineWidth(saveWidth);
		}
		if (Line.getWidth()>1 || getStrokeWidth()>1)
			updateFullWindow = true;
	}		

	/** Returns this OvalRoi as a Polygon that outlines the mask, in image pixel coordinates. */
	public Polygon getPolygon() {
		return getPolygon(true);
	}

	/** Returns this OvalRoi as a Polygon that outlines the mask.
	 *  @param absoluteCoordinates determines whether to use image pixel coordinates
	 *         instead of coordinates relative to roi origin. */
	Polygon getPolygon(boolean absoluteCoordinates) {
		ImageProcessor mask = getMask();
		Wand wand = new Wand(mask);
		wand.autoOutline(width/2,height/2, 255, 255);
        if (absoluteCoordinates)
			for (int i=0; i<wand.npoints; i++) {
				wand.xpoints[i] += x;
				wand.ypoints[i] += y;
			}
		return new Polygon(wand.xpoints, wand.ypoints, wand.npoints);
	}		

	/** Returns this OvalRoi as a FloatPolygon approximating the ellipse. */
	public FloatPolygon getFloatPolygon() {
		ShapeRoi sr = new ShapeRoi(new java.awt.geom.Ellipse2D.Double(x-0.0004, y-0.0004, width+0.0008, height+0.0008));  //better accuracy with slightly increased size
		return sr.getFloatPolygon();
	}

	/** Returns this OvalRoi as a 4 point FloatPolygon (x,y,w,h). */
	public FloatPolygon getFloatPolygon4() {
		return super.getFloatPolygon();
	}

	/** Returns the number of corner points in the mask of this selection; equivalent to getPolygon().npoints. */
	public int size() {
		return getPolygon().npoints;
	}

	/** Tests whether the center of the specified pixel is inside the boundary of this OvalRoi.
	 *  Authors: Barry DeZonia and Michael Schmid
	 */
	public boolean contains(int ox, int oy) {
		double a = width*0.5;
		double b = height*0.5;
		double cx = x + a - 0.5;
		double cy = y + b - 0.5;
		double dx = ox - cx;
		double dy = oy - cy;
		return ((dx*dx)/(a*a) + (dy*dy)/(b*b)) <= 1.0;
	}

	/** Returns whether coordinate (x,y) is contained in the Roi.
	 *  Note that the coordinate (0,0) is the top-left corner of pixel (0,0).
	 *  Use contains(int, int) to determine whether a given pixel is contained in the Roi. */
	public boolean containsPoint(double x, double y) {
		if (!super.containsPoint(x, y))
			return false;
		Ellipse2D.Double e = new Ellipse2D.Double(this.x, this.y, width, height);
		return e.contains(x, y);
	}

	/** Returns a handle number if the specified screen coordinates are  
		inside or near a handle, otherwise returns -1. */
	public int isHandle(int sx, int sy) {
		if (clipboard!=null || ic==null) return -1;
		double mag = ic.getMagnification();
		int size = getHandleSize()+3;
		int halfSize = size/2;
		int sx1 = screenX(x) - halfSize;
		int sy1 = screenY(y) - halfSize;
		int sx3 = screenX(x+width) - halfSize;
		int sy3 = screenY(y+height) - halfSize;
		int sx2 = sx1 + (sx3 - sx1)/2;
		int sy2 = sy1 + (sy3 - sy1)/2;
		
		int sw2 = (int)(0.14645*(sx3-sx1));
		int sh2 = (int)(0.14645*(sy3-sy1));
		
		if (sx>=sx1+sw2&&sx<=sx1+sw2+size&&sy>=sy1+sh2&&sy<=sy1+sh2+size) return 0;
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy1&&sy<=sy1+size) return 1;		
		if (sx>=sx3-sw2&&sx<=sx3-sw2+size&&sy>=sy1+sh2&&sy<=sy1+sh2+size) return 2;		
		if (sx>=sx3&&sx<=sx3+size&&sy>=sy2&&sy<=sy2+size) return 3;		
		if (sx>=sx3-sw2&&sx<=sx3-sw2+size&&sy>=sy3-sh2&&sy<=sy3-sh2+size) return 4;		
		if (sx>=sx2&&sx<=sx2+size&&sy>=sy3&&sy<=sy3+size) return 5;		
		if (sx>=sx1+sw2&&sx<=sx1+sw2+size&&sy>=sy3-sh2&&sy<=sy3-sh2+size) return 6;
		if (sx>=sx1&&sx<=sx1+size&&sy>=sy2&&sy<=sy2+size) return 7;
		return -1;
	}

	public ImageProcessor getMask() {
		ImageProcessor mask = cachedMask;
		if (mask!=null && mask.getPixels()!=null && mask.getWidth()==width && mask.getHeight()==height)
			return mask;
		mask = new ByteProcessor(width, height);
		double a=width/2.0, b=height/2.0;
		double a2=a*a, b2=b*b;
        a -= 0.5; b -= 0.5;
		double xx, yy;
        int offset;
        byte[] pixels = (byte[])mask.getPixels();
		for (int y=0; y<height; y++) {
            offset = y*width;
			for (int x=0; x<width; x++) {
				xx = x - a;
				yy = y - b;   
				if ((xx*xx/a2+yy*yy/b2)<=1.0)
					pixels[offset+x] = -1;
			}
		}
		cachedMask = mask;
		return mask;
	}

	/** Returns the perimeter length using Ramanujan's approximation for the circumference of an ellipse */
	public double getLength() {
		double pw=1.0, ph=1.0;
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			pw = cal.pixelWidth;
			ph = cal.pixelHeight;
		}
		double a = 0.5*width*pw;
		double b = 0.5*height*ph;
		return Math.PI*(3*(a + b) - Math.sqrt((3*a + b)*(a + 3*b)));
	}
		
}
