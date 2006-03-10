package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import ij.measure.Calibration;

public class ImageProperties implements PlugInFilter, TextListener {
	ImagePlus imp;
	static final int NANOMETER=0, MICROMETER=1, MILLIMETER=2, CENTIMETER=3,
		 METER=4, KILOMETER=5, INCH=6, FOOT=7, MILE=8, PIXEL=9, OTHER_UNIT=10;
	int oldUnitIndex;
	double oldUnitsPerCm;
	double oldScale;
	Vector nfields, sfields;
	boolean duplicatePixelWidth = true;
	String calUnit;
	double calPixelWidth, calPixelHeight;


	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		showDialog(imp);
	}
	
	void showDialog(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		Calibration calOrig = cal.copy();
		oldUnitIndex = getUnitIndex(cal.getUnit());
		oldUnitsPerCm = getUnitsPerCm(oldUnitIndex);
		int stackSize = imp.getImageStackSize();
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		boolean global1 = imp.getGlobalCalibration()!=null;
		boolean global2;
		GenericDialog gd = new GenericDialog(imp.getTitle());
		gd.addNumericField("Width:", imp.getWidth(), 0);
		gd.addNumericField("Height:", imp.getHeight(), 0);
		gd.addNumericField("Channels:", channels, 0);
		gd.addNumericField("Depth (z-slices):", slices, 0);
		gd.addNumericField("Frames (time-points):", frames, 0);
		gd.addMessage("");
		gd.addStringField("Unit of Length:", cal.getUnit());
		oldScale = cal.pixelWidth!=0?1.0/cal.pixelWidth:0;
		//gd.addNumericField("Pixels/Unit:", oldScale, (int)oldScale==oldScale?0:3);
		//gd.addMessage("");
		gd.addNumericField("Pixel_Width:", cal.pixelWidth, 5, 8, null);
		gd.addNumericField("Pixel_Height:", cal.pixelHeight, 5, 8, null);
		gd.addNumericField("Voxel_Depth:", cal.pixelDepth, 5, 8, null);
		gd.addMessage("");
		double interval = cal.frameInterval;
		gd.addNumericField("Frame Interval (sec.):", interval, (int)interval==interval?0:2, 8, null);
		String xo = cal.xOrigin==(int)cal.xOrigin?IJ.d2s(cal.xOrigin,0):IJ.d2s(cal.xOrigin,2);
		String yo = cal.yOrigin==(int)cal.yOrigin?IJ.d2s(cal.yOrigin,0):IJ.d2s(cal.yOrigin,2);
		String zo = "";
		if (cal.zOrigin!=0.0) {
			zo = cal.zOrigin==(int)cal.zOrigin?IJ.d2s(cal.zOrigin,0):IJ.d2s(cal.zOrigin,2);
			zo = "," + zo;
		}
		gd.addStringField("Origin (pixels):", xo+","+yo+zo);
		gd.addCheckbox("Global", global1);
		nfields = gd.getNumericFields();
        for (int i=0; i<nfields.size(); i++)
            ((TextField)nfields.elementAt(i)).addTextListener(this);
        sfields = gd.getStringFields();
        for (int i=0; i<sfields.size(); i++)
            ((TextField)sfields.elementAt(i)).addTextListener(this);
		calUnit = cal.getUnit();
		calPixelWidth = cal.pixelWidth;
		calPixelHeight = cal.pixelHeight;
		gd.showDialog();
		if (gd.wasCanceled())
			return;
 		double width = gd.getNextNumber();
 		double height = gd.getNextNumber();
 		channels = (int)gd.getNextNumber();
 		if (channels<1) channels = 1;
 		slices = (int)gd.getNextNumber();
 		if (slices<1) slices = 1;
 		frames = (int)gd.getNextNumber();
 		if (frames<1) frames = 1;
 		if (width!=imp.getWidth() || height!=imp.getHeight()) {
 			IJ.error("Properties", "Use Image>Adjust>Size to change the image size.");
 			return;
 		}
 		if (channels*slices*frames==stackSize)
 			imp.setDimensions(channels, slices, frames);
 		else
 			IJ.error("Properties", "The product of channels ("+channels+"), slices ("+slices
 				+")\n and frames ("+frames+") must equal the stack size ("+stackSize+").");

		String unit = gd.getNextString();
        if (unit.equals("um"))
            unit = IJ.micronSymbol + "m";
        else if (unit.equals("u"))
            unit = "" + IJ.micronSymbol;
        else if (unit.equals("A"))
        	unit = ""+IJ.angstromSymbol;
 		double pixelWidth = gd.getNextNumber();
 		double pixelHeight = gd.getNextNumber();
 		double pixelDepth = gd.getNextNumber();
		if (unit.equals("") || unit.equalsIgnoreCase("none") || pixelWidth==0.0) {
			cal.setUnit(null);
			cal.pixelWidth = 1.0;
			cal.pixelHeight = 1.0;
		} else {
			cal.setUnit(unit);
			cal.pixelWidth = pixelWidth;
			cal.pixelHeight = pixelHeight;
			cal.pixelDepth = pixelDepth;
		}

		cal.frameInterval = gd.getNextNumber();
        String[] origin = Tools.split(gd.getNextString(), " ,");
		double x = Tools.parseDouble(origin[0]);
		double y = origin.length>=2?Tools.parseDouble(origin[1]):Double.NaN;
		double z = origin.length>=3?Tools.parseDouble(origin[2]):Double.NaN;
		cal.xOrigin= Double.isNaN(x)?0.0:x;
		cal.yOrigin= Double.isNaN(y)?cal.xOrigin:y;
		cal.zOrigin= Double.isNaN(z)?0.0:z;
 		global2 = gd.getNextBoolean();
		if (!cal.equals(calOrig))
			imp.setCalibration(cal);
 		imp.setGlobalCalibration(global2?cal:null);
		if (global2 || global2!=global1)
			WindowManager.repaintImageWindows();
		else
			imp.repaintWindow();
	}
	
	double getNewScale(String newUnit) {
		//IJ.log("getNewScale: "+newUnit);
		if (oldUnitsPerCm==0.0) return 0.0;
		double newScale = 0.0;
		int newUnitIndex = getUnitIndex(newUnit);
		if (newUnitIndex!=oldUnitIndex) {
			double newUnitsPerCm = getUnitsPerCm(newUnitIndex);
			if (oldUnitsPerCm!=0.0 && newUnitsPerCm!=0.0) {
				newScale = oldScale * (oldUnitsPerCm/newUnitsPerCm);
			}
		}
		return newScale;
	}
	
	int getUnitIndex(String unit) {
		unit = unit.toLowerCase(Locale.US);
		if (unit.equals("cm")||unit.startsWith("cent"))
			return CENTIMETER;
		else if (unit.equals("mm")||unit.startsWith("milli"))
			return MILLIMETER;
		else if (unit.startsWith("inch"))
			return INCH;
		else if (unit.startsWith(""+IJ.micronSymbol)||unit.startsWith("u")||unit.startsWith("micro"))
			return MICROMETER;
		else if (unit.equals("nm")||unit.startsWith("nano"))
			return NANOMETER;
		else if (unit.startsWith("meter"))
			return METER;
		else if (unit.equals("km")||unit.startsWith("kilo"))
			return KILOMETER;
		else if (unit.equals("ft")||unit.equals("foot")||unit.equals("feet"))
			return FOOT;
		else if (unit.equals("mi")||unit.startsWith("mile"))
			return MILLIMETER;
		else
			return OTHER_UNIT;
	}
	
	double getUnitsPerCm(int unitIndex) {
		switch (unitIndex) {
			case NANOMETER: return  10000000.0;
			case MICROMETER: return    10000.0;
			case MILLIMETER: return       10.0;
			case CENTIMETER: return        1.0;
			case METER: return             0.01;
			case KILOMETER: return         0.00001;
			case INCH: return              0.3937;
			case FOOT: return              0.0328083;
			case MILE: return              0.000006213;
			default: return                0.0;
		}
	}

   	public void textValueChanged(TextEvent e) {
		//IJ.log("textValueChanged");
       TextField widthField  = (TextField)nfields.elementAt(0);
        int width = (int)Tools.parseDouble(widthField.getText(),-99);
        //if (width!=imp.getWidth()) widthField.setText(IJ.d2s(imp.getWidth(),0));
        
       TextField heightField  = (TextField)nfields.elementAt(1);
        int height = (int)Tools.parseDouble(heightField.getText(),-99);
        //if (height!=imp.getHeight()) heightField.setText(IJ.d2s(imp.getHeight(),0));
        
        int channels = (int)Tools.parseDouble(((TextField)nfields.elementAt(2)).getText(),-99);
        int depth = (int)Tools.parseDouble(((TextField)nfields.elementAt(3)).getText(),-99);
        int frames = (int)Tools.parseDouble(((TextField)nfields.elementAt(4)).getText(),-99);
        

        TextField pixelWidthField  = (TextField)nfields.elementAt(5);
		String newWidthText = pixelWidthField.getText()	;
		double newPixelWidth = Tools.parseDouble(newWidthText,-99);
        TextField pixelHeightField  = (TextField)nfields.elementAt(6);
		String newHeightText = pixelHeightField.getText()	;
		double newPixelHeight = Tools.parseDouble(newHeightText,-99);
		if (newPixelWidth!=-99 && newPixelHeight!=-99) {
			if (newPixelHeight!=calPixelHeight) duplicatePixelWidth = false;
			if (duplicatePixelWidth && newPixelWidth!=calPixelWidth) 
				if (newPixelWidth!=-99) {
					pixelHeightField.setText(newWidthText);
					calPixelHeight = Tools.parseDouble(newWidthText,-99);
				}
		}
		calPixelWidth = newPixelWidth;
		calPixelHeight = newPixelHeight;
  		TextField unitField = (TextField)sfields.elementAt(0);
 		String newUnit = unitField.getText();
 		if (!newUnit.equals(calUnit)) {
			double newScale = getNewScale(newUnit);
			if (newScale!=0.0) {
				//ppuField.setText(((int)newScale)==newScale?IJ.d2s(newScale,0):IJ.d2s(newScale,2));
				pixelWidthField.setText(IJ.d2s(1/newScale,6));
				pixelHeightField.setText(IJ.d2s(1/newScale,6));
				oldUnitIndex = getUnitIndex(newUnit);
				oldUnitsPerCm = getUnitsPerCm(oldUnitIndex);
				oldScale = newScale;
			}
			calUnit = newUnit;
		}
 	}

}
