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
import java.awt.event.*;

public class RoiRotationTool extends PlugInTool {
	ImageCanvas ic = null;
	int startX=0, startY=0;
	
	public void mousePressed(ImagePlus imp, MouseEvent e) {
		ic = imp.getCanvas();
		if (imp == null || ic == null)
			return;
		Roi roi = imp.getRoi();
		if (roi==null) {
			IJ.beep();
			IJ.showStatus("No selection");
			return;
		}
	}

	public void mouseDragged(ImagePlus imp, MouseEvent e) {
		rotateRoi(imp, e.getX(), e.getY());
	}

	public void showOptionsDialog() {
		IJ.log("PlugInTool MouseRoiRotator Peter Haub dipsystems.de 10'2015");
	}

	public String getToolName() {
		return "Selection Rotator";
	}

	public String getToolIcon() {
		return "C037D06D15D16D24D25D26D27D28D29D2aD33D34D35D36D37D3bD3cD42D43D44D45D46D47D48D4cD4dDb1Db2Db6Db7Db8Db9DbaDbbDbcDc2Dc3Dc7Dc8Dc9DcaDcbDd4Dd5Dd6Dd7Dd8Dd9DdaDe8De9Df8CabcD05D14D17D18D19D1aD23D2bD2cD32D3dD41D51D52D53D54D55D56D57D58Da6Da7Da8Da9DaaDabDacDadDbdDc1DccDd2Dd3DdbDe4De5De6De7DeaDf9";
	}

	void rotateRoi(ImagePlus imp, int sx, int sy) {
		if (imp == null || ic == null)
			return;
		Roi roi = imp.getRoi();
		if (roi == null)
			return;
		boolean imageRoi = roi instanceof ImageRoi;
		if (imageRoi)
			((ImageRoi)roi).setZeroTransparent(true);
		
		// Mouse coordinate handling
		int centerX = imp.getWidth()/2;
		int centerY = imp.getHeight()/2;
		
		int xNew = ic.offScreenX(sx);
		int yNew = ic.offScreenY(sy);
		
		if (startX == 0 && startY == 0) {
			startX = xNew;
			startY = yNew;
			return;
		}
		
		int dx1 = centerX - xNew;
		int dy1 = centerY - yNew;
		int dx2 = centerX - startX;
		int dy2 = centerY - startY;
		
		double l1 = Math.sqrt(dx1*dx1 + dy1*dy1);
		double l2 = Math.sqrt(dx2*dx2 + dy2*dy2);
		double l = (l1 + l2)/2.0;
		double dy3 = yNew - startY;
		
		if (l==0 || dy3==0)
			return;
		startX = xNew;
		startY = yNew;
		
		double angle = Math.atan2(dy3, l);				
		Roi newRoi = RoiRotator.rotate(roi, angle*180/Math.PI);
		if (imageRoi)
			imp.draw();
		else
			imp.setRoi(newRoi);
	}

}

