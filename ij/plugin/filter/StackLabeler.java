package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import java.awt.*;

/** This plugin implements the Image/Stacks/Label command. */
public class StackLabeler implements ExtendedPlugInFilter, DialogListener {
	private static final int flags = DOES_ALL+DOES_STACKS;
	private ImagePlus imp;
	private double time;
	private static int x = 5;
	private static int y = 20;
	private static int fontSize = 18;
	private int maxWidth;
	private Font font;
	private static double start = 0;
	private static double interval = 1;
	private static String text = "";
	private static int decimalPlaces = 0;
	private static boolean textBeforeNumber;
	private int fieldWidth;

	public int setup(String arg, ImagePlus imp) {
		if (imp!=null && imp.isHyperStack()) {
			IJ.error("StackLabeler", "This command does not currently work with hyperstacks.");
			return DONE;
		}
		this.imp = imp;
		return flags;
	}

    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		ImageProcessor ip = imp.getProcessor();
		Rectangle roi = ip.getRoi();
		if (roi.width<ip.getWidth() || roi.height<ip.getHeight()) {
			x = roi.x;
			y = roi.y+roi.height;
			fontSize = (int) ((roi.height - 1.10526)/0.934211);	
			if (fontSize<7) fontSize = 7;
			if (fontSize>80) fontSize = 80;
		}
		GenericDialog gd = new GenericDialog("StackLabeler");
		gd.setInsets(2, 5, 0);
		gd.addStringField("Starting value:", IJ.d2s(start,decimalPlaces));
		gd.addStringField("Interval:", ""+IJ.d2s(interval,decimalPlaces));
		gd.addNumericField("X location:", x, 0);
		gd.addNumericField("Y location:", y, 0);
		gd.addNumericField("Font size:", fontSize, 0);
		gd.addStringField("Text:", text, 10);
		gd.setInsets(10,20,0);
        gd.addCheckbox("Text before number:", textBeforeNumber);
        gd.addPreviewCheckbox(pfr);
        gd.addHelp(IJ.URL+"/docs/menus/image.html#label");
        gd.addDialogListener(this);
		gd.showDialog();
        if (gd.wasCanceled())
        	return DONE;
        else
        	return flags;
    }

    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		start = Tools.parseDouble(gd.getNextString());
 		String str = gd.getNextString();
 		interval = Tools.parseDouble(str);
		x = (int)gd.getNextNumber();
		y = (int)gd.getNextNumber();
		fontSize = (int)gd.getNextNumber();
		text = gd.getNextString();
		textBeforeNumber = gd.getNextBoolean();
		int index = str.indexOf(".");
		if (index!=-1)
			decimalPlaces = str.length()-index-1;
		else
			decimalPlaces = 0;
		if (gd.invalidNumber()) return false;
		font = new Font("SansSerif", Font.PLAIN, fontSize);
		time = start;
		if (y<fontSize) y = fontSize;
		ImageProcessor ip = imp.getProcessor();
		ip.setFont(font);
		int stackSize = imp.getStackSize();
		maxWidth = ip.getStringWidth(getString(start+interval*stackSize));
		fieldWidth = 1;
		if (stackSize>=10) fieldWidth = 2;
		if (stackSize>=100) fieldWidth = 3;
		if (stackSize>=1000) fieldWidth = 4;
		if (stackSize>=10000) fieldWidth = 5;
        return true;
    }

	public void run(ImageProcessor ip) {
		ip.setFont(font);
		ip.setColor(Toolbar.getForegroundColor());
		ip.setAntialiasedText(fontSize>=18);
		String s = getString(time);
		ip.moveTo(x+maxWidth-ip.getStringWidth(s), y);
		ip.drawString(s);
		time += interval;
	}
	
	String getString(double time) {
		if (interval==0.0)
			return text;
		else if (textBeforeNumber && decimalPlaces==0)
			return text+zeroFill((int)time);
		else if (textBeforeNumber)
			return text+IJ.d2s(time, decimalPlaces);
		else
			return IJ.d2s(time, decimalPlaces)+" "+text;
	}
	
	String  zeroFill(int n) {
		String str = ""+n;
		while (str.length()<fieldWidth)
			str = "0" + str;
		return str;
	}
		
	public void setNPasses (int nPasses) {}

}
