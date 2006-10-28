import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.*;

import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import java.lang.Math;

/** 
 *
 * @Author Berin Martini
 * @Version 19-10-06
 */

public class Arrow_Curved implements PlugInFilter {
	private ImagePlus	imp;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL;
	}

	public void run(ImageProcessor ip) {
		CustomCanvas cc = new CustomCanvas(imp);
		ImageWindow win = new ImageWindow(imp, cc);
	}

	class Bezier {
		public int p0_x = 0, p1_x = 0, p2_x = 0, p3_x = 0;
		public int p0_y = 0, p1_y = 0, p2_y = 0, p3_y = 0;
		public int p01_x = 0, p12_x = 0, p23_x = 0;
		public int p01_y = 0, p12_y = 0, p23_y = 0;
		public int p012_x = 0, p123_x = 0, p0123_x = 0;
		public int p012_y = 0, p123_y = 0, p0123_y = 0;

		public boolean 	split = true;
		private int	flags = 0;
		private int 	pix = 5; //If curve segment is less then this, then keep. Value chosen by eye.

		Bezier() {}
		Bezier(int x0, int y0, int x1, int y1, int x2, int y2, int x3, int y3) {
			setP0(x0, y0);
			setP1(x1, y1);
			setP2(x2, y2);
			setP3(x3, y3);
			makeAveragedPoints();
		}

		public void setP0(int x, int y) {
			this.p0_x = x;
			this.p0_y = y;
			flags |= 1;
			makeAveragedPoints();
		}
		public void setP1(int x, int y) {
			this.p1_x = x;
			this.p1_y = y;
			flags |= 2;
			makeAveragedPoints();
		}
		public void setP2(int x, int y) {
			this.p2_x = x;
			this.p2_y = y;
			flags |= 4;
			makeAveragedPoints();
		}
		public void setP3(int x, int y) {
			this.p3_x = x;
			this.p3_y = y;
			flags |= 8;
			makeAveragedPoints();
		}

		public int average(int x, int y) {
			int n = (x + y) >> 1;
			return(n);
		}

		private void makeAveragedPoints() {

			if ((flags & 3) == 3) {
				p01_x = average(p0_x, p1_x) ;
				p01_y = average(p0_y, p1_y) ;
			}
			if ((flags & 6) == 6) {
				p12_x = average(p1_x, p2_x) ;
				p12_y = average(p1_y, p2_y) ;
			}
			if ((flags & 12) == 12) {
				p23_x = average(p2_x, p3_x) ;
				p23_y = average(p2_y, p3_y) ;
			}
			if ((flags & 7) == 7){
				p012_x = average(p01_x, p12_x) ;
				p012_y = average(p01_y, p12_y) ;
			}
			if ((flags & 14) == 14) {	
				p123_x = average(p12_x, p23_x) ;
				p123_y = average(p12_y, p23_y) ;
			}
			if ((flags & 15) == 15){
				p0123_x = average(p012_x, p123_x) ;
				p0123_y = average(p012_y, p123_y) ;

				if (	( Math.abs(p0123_x - p0_x) < pix && 
					Math.abs(p0123_y - p0_y) < pix ) || 
					( Math.abs(p0123_x - p3_x) < pix && 
					Math.abs(p0123_y - p3_y) < pix )) {

					split = false;
				}
			}
		}
	} //end class Bezier

	class CustomCanvas extends ImageCanvas {
		private Bezier		bezier = new Bezier();
		private int		state = 0;
		private int 		pointMoved = -1;
		private int		pW = 8; // point width

		CustomCanvas(ImagePlus imp) {
			super(imp);
		}

		public void update(Graphics g) //method called by repaint().  //overriding original update method                
		{
			paint(g);
		}

		public void paint(Graphics g) {
			super.paint(g); //this passes to image to ImageCanvas super
			g.setColor(Color.red);
			updatePointGraphics(g);
		}
		
		public void mouseDragged(MouseEvent e) {
			int x = offScreenX(e.getX());
			int y = offScreenY(e.getY());

			if (state == 1) {
				bezier.setP1(x, y);
				repaint();
			} else if (state == 2) {
				bezier.setP2(x, y);
				repaint();
			} else if (state == 4) {
				if (pointMoved == 0) {
					int dx = (bezier.p0_x - x);
					int dy = (bezier.p0_y - y);
					bezier.setP0(x, y);
					bezier.setP1((bezier.p1_x - dx) , (bezier.p1_y - dy));
					repaint();
				} else if (pointMoved == 1) {
					bezier.setP1(x, y);
					repaint();
				} else if (pointMoved == 2) {
					bezier.setP2(x, y);
					repaint();
				} else if (pointMoved == 3) {
					int dx = (bezier.p3_x - x);
					int dy = (bezier.p3_y - y);
					bezier.setP3(x, y);
					bezier.setP2((bezier.p2_x - dx) , (bezier.p2_y - dy));
					repaint();
				}
			}
		}
		public void mouseClicked(MouseEvent e){
			// Once contral points are set then a click off one of the control points ends the plugin.
			if (state == 4) {
				int x = offScreenX(e.getX());
				int y = offScreenY(e.getY());

				Roi p0 = new Roi((bezier.p0_x - (2 * pW)), (bezier.p0_y - (2 * pW)), (4 * pW), (4 * pW));
				Roi p1 = new Roi((bezier.p1_x - (2 * pW)), (bezier.p1_y - (2 * pW)), (4 * pW), (4 * pW));
				Roi p2 = new Roi((bezier.p2_x - (2 * pW)), (bezier.p2_y - (2 * pW)), (4 * pW), (4 * pW));
				Roi p3 = new Roi((bezier.p3_x - (2 * pW)), (bezier.p3_y - (2 * pW)), (4 * pW), (4 * pW));

				if (p0.contains(x, y) || p1.contains(x, y) || p2.contains(x, y) || p3.contains(x, y)) {
				} else {
					new ImageWindow(super.imp);
				}
			}
		}
		public void mousePressed(MouseEvent e){
			int x = offScreenX(e.getX());
			int y = offScreenY(e.getY());

			if (state == 0) {
				bezier.setP0(x,y);
				state = 1;
			} else if (state == 3) {
				bezier.setP3(x,y);
				state = 2;
			} else if (state == 4) {
				Roi p0 = new Roi((bezier.p0_x - (2 * pW)), (bezier.p0_y - (2 * pW)), (4 * pW), (4 * pW));
				Roi p1 = new Roi((bezier.p1_x - (2 * pW)), (bezier.p1_y - (2 * pW)), (4 * pW), (4 * pW));
				Roi p2 = new Roi((bezier.p2_x - (2 * pW)), (bezier.p2_y - (2 * pW)), (4 * pW), (4 * pW));
				Roi p3 = new Roi((bezier.p3_x - (2 * pW)), (bezier.p3_y - (2 * pW)), (4 * pW), (4 * pW));

				if (p1.contains(x, y)) {
					pointMoved = 1;
				} else if (p0.contains(x, y)) {
					pointMoved = 0;
				} else if (p2.contains(x, y)) {
					pointMoved = 2;
				} else if (p3.contains(x, y)) {
					pointMoved = 3;
				} else {
				}
			}
		}
		public void mouseReleased(MouseEvent e){
			int x = offScreenX(e.getX());
			int y = offScreenY(e.getY());

			if (state == 1) {
				bezier.setP1(x, y);
				state = 3;
				repaint();
			} else if (state == 2) {
				bezier.setP2(x, y);
				state = 4;
				repaint();
			} else if (state == 4) {
				pointMoved = -1;
			}
		}

		public void updatePointGraphics(Graphics g) {

			if (state == 0) {
			} else if (state == 1) {
				g.fillOval((screenX(bezier.p0_x) - (pW/2)), (screenY(bezier.p0_y) - (pW/2)), pW, pW);
				g.drawOval((screenX(bezier.p1_x) - (pW/2)), (screenY(bezier.p1_y) - (pW/2)), pW, pW);
				g.drawLine(screenX(bezier.p0_x), screenY(bezier.p0_y), screenX(bezier.p1_x), screenY(bezier.p1_y));
			} else if (state == 2) {
				g.fillOval((screenX(bezier.p0_x) - (pW/2)), (screenY(bezier.p0_y) - (pW/2)), pW, pW);
				g.drawOval((screenX(bezier.p1_x) - (pW/2)), (screenY(bezier.p1_y) - (pW/2)), pW, pW);
				g.drawLine(screenX(bezier.p0_x), screenY(bezier.p0_y), screenX(bezier.p1_x), screenY(bezier.p1_y));
				g.drawOval((screenX(bezier.p2_x) - (pW/2)), (screenY(bezier.p2_y) - (pW/2)), pW, pW);
				g.fillOval((screenX(bezier.p3_x) - (pW/2)), (screenY(bezier.p3_y) - (pW/2)), pW, pW);
				g.drawLine(screenX(bezier.p3_x), screenY(bezier.p3_y), screenX(bezier.p2_x), screenY(bezier.p2_y));
			} else if (state == 3) {
				g.fillOval((screenX(bezier.p0_x) - (pW/2)), (screenY(bezier.p0_y) - (pW/2)), pW, pW);
				g.drawOval((screenX(bezier.p1_x) - (pW/2)), (screenY(bezier.p1_y) - (pW/2)), pW, pW);
				g.drawLine(screenX(bezier.p0_x), screenY(bezier.p0_y), screenX(bezier.p1_x), screenY(bezier.p1_y));
			} else if (state == 4) {
				g.fillOval((screenX(bezier.p0_x) - (pW/2)), (screenY(bezier.p0_y) - (pW/2)), pW, pW);
				g.drawOval((screenX(bezier.p1_x) - (pW/2)), (screenY(bezier.p1_y) - (pW/2)), pW, pW);
				g.drawLine(screenX(bezier.p0_x), screenY(bezier.p0_y), screenX(bezier.p1_x), screenY(bezier.p1_y));
				g.drawOval((screenX(bezier.p2_x) - (pW/2)), screenY((bezier.p2_y) - (pW/2)), pW, pW);
				g.fillOval((screenX(bezier.p3_x) - (pW/2)), screenY((bezier.p3_y) - (pW/2)), pW, pW);
				g.drawLine(screenX(bezier.p2_x), screenY(bezier.p2_y), screenX(bezier.p3_x), screenY(bezier.p3_y));
				drawCurve();
			}
		}
	
	        private int[] addArrayElement(int[] array, int d) {
	
	                int[] newArray = new int[(array.length + 1)];
	                System.arraycopy(array, 0, newArray, 0, array.length ) ;
	                newArray[array.length] = d;
	
	                return newArray;
	        }
	
		private void drawCurve() {
			Vector curve = new Vector(0,1);
			curve.add(bezier);
			int[][] curveXY = {{},{}};
			curveXY = arrowHead(curveXY, bezier.p1_x, bezier.p1_y, bezier.p0_x, bezier.p0_y);
	
			int ii = 0;
			while (ii < curve.capacity()) {
	
				Bezier subC = null;
				try {
					subC = (Bezier) curve.get(ii); 
				} catch (ArrayIndexOutOfBoundsException e) {IJ.error("can't put bezier curve into subcurve");}
	
				if (subC.split) {
					try {
						curve.set(ii, new Bezier(	subC.p0123_x, subC.p0123_y,
										subC.p123_x, subC.p123_y,
										subC.p23_x, subC.p23_y, subC.p3_x, subC.p3_y));
						curve.add(ii, new Bezier(	subC.p0_x, subC.p0_y,
										subC.p01_x, subC.p01_y,
										subC.p012_x, subC.p012_y, subC.p0123_x, subC.p0123_y));

					} catch (ArrayIndexOutOfBoundsException e) {IJ.error("can't put new subcurves into curve");}
	
				} else {
					curveXY[0] = addArrayElement(curveXY[0], subC.p12_x);
					curveXY[0] = addArrayElement(curveXY[0], subC.p3_x);
					curveXY[1] = addArrayElement(curveXY[1], subC.p12_y);
					curveXY[1] = addArrayElement(curveXY[1], subC.p3_y);
					ii++;
				}
			}

			curveXY = arrowHead(curveXY, bezier.p2_x, bezier.p2_y, bezier.p3_x, bezier.p3_y);
			PolygonRoi curveROI = new PolygonRoi(curveXY[0], curveXY[1], curveXY[0].length, Roi.FREELINE);
			this.imp.setRoi(curveROI);
		} //end drawCurev()

		/** Draws an arrow. Based on code posted to comp.lang.java.gui by Marcel Nijman. */
		// arrowHead(int x1, int y1, int x2, int y2) requires as input the xy coordinates of an end point and its control point.
		public int[][] arrowHead(int[][] curveXY, int x1, int y1, int x2, int y2) {
			int HEAD_SIZE = 12;
			double size = HEAD_SIZE + HEAD_SIZE*Line.getWidth()*0.25;
			double dx = x2-x1;
			double dy = y2-y1;
			double ra = Math.sqrt(dx*dx + dy*dy);
			dx /= ra;
			dy /= ra;
			int x3 = (int)Math.round(x2-dx*size);
			int y3 = (int)Math.round(y2-dy*size);
			double r = 0.3*size;
			int x4 = (int)Math.round(x3+dy*r);
			int y4 = (int)Math.round(y3-dx*r);
			int x5 = (int)Math.round(x3-dy*r);
			int y5 = (int)Math.round(y3+dx*r);

			curveXY[0] = addArrayElement(curveXY[0], x4);
			curveXY[0] = addArrayElement(curveXY[0], x2);
			curveXY[0] = addArrayElement(curveXY[0], x5);
			curveXY[0] = addArrayElement(curveXY[0], x2);

			curveXY[1] = addArrayElement(curveXY[1], y4);
			curveXY[1] = addArrayElement(curveXY[1], y2);
			curveXY[1] = addArrayElement(curveXY[1], y5);
			curveXY[1] = addArrayElement(curveXY[1], y2);

			return curveXY;
		}

		public void mouseMoved(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {}

	}
	
}
	
		
