package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;
import ij.measure.Calibration;

public class ImageProperties implements PlugInFilter {
	ImagePlus imp;
	static final int NANOMETER=0, MICROMETER=1, MILLIMETER=2, CENTIMETER=3,
		 METER=4, KILOMETER=5, INCH=6, FOOT=7, MILE=8, PIXEL=9, OTHER_UNIT=10;
	int oldUnitIndex;
	double oldUnitsPerCm;
	double oldScale;


	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		showDialog(imp);
	}
	
	void showDialog(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		oldUnitIndex = getUnitIndex(cal.getUnit());
		oldUnitsPerCm = getUnitsPerCm(oldUnitIndex);
		GenericDialog gd = new ImagePropertiesDialog(imp.getTitle(), this);
		gd.addStringField("Unit of Length:", cal.getUnit());
		oldScale = cal.pixelWidth!=0?1.0/cal.pixelWidth:0;
		gd.addNumericField("Pixels/Unit:", oldScale, (int)oldScale==oldScale?0:3);
		int stackSize = imp.getStackSize();
		if (stackSize>1) {
			gd.addMessage("");
			gd.addNumericField("Slice Spacing:", cal.pixelDepth, (int)cal.pixelDepth==cal.pixelDepth?0:3);
			double fps = cal.frameInterval>0.0?1/cal.frameInterval:0.0;
			gd.addNumericField("Frames per Second:", fps, (int)fps==fps?0:2);		}
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		String unit = gd.getNextString();
        if (unit.equals("um"))
            unit = IJ.micronSymbol + "m";
        else if (unit.equals("u"))
            unit = "" + IJ.micronSymbol;
        else if (unit.equals("A"))
        	unit = ""+IJ.angstromSymbol;
 		double resolution = gd.getNextNumber();
		if (unit.equals("")||unit.equalsIgnoreCase("pixel")
		||unit.equalsIgnoreCase("none")||resolution==0.0) {
			cal.setUnit(null);
			cal.pixelWidth = 1.0;
			cal.pixelHeight = 1.0;
		} else {
			cal.setUnit(unit);
			cal.pixelWidth = 1.0/resolution;
			cal.pixelHeight = 1.0/resolution;
		}
		if (stackSize>1) {
			double spacing = gd.getNextNumber();
			double fps = gd.getNextNumber();
			cal.pixelDepth = spacing;
			if (fps!=0.0)
				cal.frameInterval = 1.0/fps;
			else
				cal.frameInterval = 0.0;
		}
		imp.repaintWindow();
	}
	
	double getNewScale(String newUnit) {
		if (oldUnitsPerCm==0.0)
			return 0.0;
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

}

class ImagePropertiesDialog extends GenericDialog {
	ImageProperties iprops;
	
	public ImagePropertiesDialog(String title, ImageProperties iprops) {
		super(title);
		this.iprops = iprops;
	}

   	public void textValueChanged(TextEvent e) {
 		TextField unitField = (TextField)stringField.elementAt(0);
 		if (e.getSource()!=unitField)
   			return;
 		String newUnit = unitField.getText();
  		TextField ppuField = (TextField)numberField.elementAt(0);
   		double newScale = iprops.getNewScale(newUnit);
  		if (newScale!=0.0) {
  			ppuField.setText(((int)newScale)==newScale?IJ.d2s(newScale,0):IJ.d2s(newScale,2));
  			iprops.oldUnitIndex = iprops.getUnitIndex(newUnit);
			iprops.oldUnitsPerCm = iprops.getUnitsPerCm(iprops.oldUnitIndex);
			iprops.oldScale = newScale;;
		}
 	}

}
