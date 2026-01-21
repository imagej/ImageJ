/* 
 * This plugin implements the "Selection Rotator" tool, which 
 * can be used to interactively rotate selections.
 *
 * @author: 	Peter Haub, Oct. 2015, phaub@dipsystems.de
 */

package ij.plugin.tool;
import ij.*;
import ij.gui.*;
import ij.plugin.RoiRotator;
import ij.plugin.tool.PlugInTool;
import ij.plugin.frame.Recorder;
import java.awt.*;
import java.awt.event.*;

public class RoiRotationTool extends PlugInTool {
	ImageCanvas ic = null;
	int startX=0, startY=0;
	Roi roi, newRoi;
	int centerX, centerY, xNew, yNew, dx1, dy1, dx2, dy2;
	double l, l1, l2, dx, dy, phi, phi1, phi2;
	boolean isImageRoi;
	Rectangle bounds;
	ImagePlus imp2;
	
	static final int UPDOWNROTATION=0, CIRCULARROTATION=1;
	int defaultRotationMode = CIRCULARROTATION;
	
	public void mousePressed(ImagePlus imp, MouseEvent e) {
		if (imp == null) return;
		imp2 = imp;
		ic = imp.getCanvas();
		if (ic == null) return;
		roi = imp.getRoi();
		if (roi==null) {
			IJ.beep();
			IJ.showStatus("No selection");
			return;
		}
		
		startX = ic.offScreenX(e.getX());
		startY = ic.offScreenY(e.getY());
		
		if (defaultRotationMode == UPDOWNROTATION){
			centerX = imp.getWidth()/2;
			centerY = imp.getHeight()/2;
		} else {
			double[] centroid = roi.getContourCentroid();
			centerX = (int)Math.round(centroid[0]);
			centerY = (int)Math.round(centroid[1]);
		}
	}
	
	public void mouseDragged(ImagePlus imp, MouseEvent e) {
		if (imp == null || ic == null) return;
		roi = imp.getRoi();
		if (roi == null) return;
		isImageRoi = roi instanceof ImageRoi;
		if (isImageRoi)
			((ImageRoi)roi).setZeroTransparent(true);
		
		if ( e.isAltDown() || e.isShiftDown() )
			moveRoi(e.getX(), e.getY());
		else
			rotateRoi(e.getX(), e.getY());
	}
		
	public void mouseReleased(ImagePlus imp, MouseEvent e) {
		if (IJ.recording()) {
			Roi roi = imp.getRoi();
			int n = roi.getPolygon().npoints;
			if (n<=20 && roi.getType()!=Roi.LINE)
				Recorder.recordRoi(roi);
			else if (n>20)
				Recorder.recordString("// Selection has "+n+" points, too many to record.\n");
		}
	}

	public void showOptionsDialog() {
		IJ.log("PlugInTool MouseRoiRotator Peter Haub dipsystems.de 10'2015");
	}

	public String getToolName() {
		return "Selection Rotator (press alt or shift to move)";
	}
	
	public String getToolIcon() {
		return "C037D06D15D16D24D25D26D27D28D29D2aD33D34D35D36D37D3bD3cD42D43D44D45D46D47D48D4cD4dDb1Db2Db6Db7Db8Db9DbaDbbDbcDc2Dc3Dc7Dc8Dc9DcaDcbDd4Dd5Dd6Dd7Dd8Dd9DdaDe8De9Df8CabcD05D14D17D18D19D1aD23D2bD2cD32D3dD41D51D52D53D54D55D56D57D58Da6Da7Da8Da9DaaDabDacDadDbdDc1DccDd2Dd3DdbDe4De5De6De7DeaDf9";
	}

	void rotateRoi(int sx, int sy){		
		xNew = ic.offScreenX(sx);
		yNew = ic.offScreenY(sy);
		
		dx1 = centerX - xNew;
		dy1 = centerY - yNew;
		dx2 = centerX - startX;	
		dy2 = centerY - startY;
		
		if (defaultRotationMode == UPDOWNROTATION){
			l1 = Math.sqrt(dx1*dx1 + dy1*dy1);
			l2 = Math.sqrt(dx2*dx2 + dy2*dy2);
			l = (l1 + l2)/2.0;
			dy = yNew - startY;
					
			if (l==0 || dy==0)  return;
			phi = Math.atan2(dy, l);
		}
		else{
			phi1 = Math.atan2(dy1, dx1);	
			phi2 = Math.atan2(dy2, dx2);
			phi = phi1 - phi2;
			if (phi == 0 || phi == Double.NaN) return;
		}
		
		startX = xNew;	
		startY = yNew;
		
		newRoi = RoiRotator.rotate(roi, phi*180/Math.PI);
		if (isImageRoi)
			imp2.draw();
		else
			imp2.setRoi(newRoi);
	}

	void moveRoi(int sx, int sy){
		xNew = ic.offScreenX(sx);
		yNew = ic.offScreenY(sy);
		
		dx1 = xNew - startX;
		dy1 = yNew - startY;
		
		if (dx1==0 && dy2==0)  return;
		startX = xNew;	
		startY = yNew;
		
		dx = roi.getXBase() + dx1;
		dy = roi.getYBase() + dy1;
		roi.setLocation(dx, dy);
		
		imp2.draw();
	}
	
}

