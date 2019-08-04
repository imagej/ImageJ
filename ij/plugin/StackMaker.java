package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.*;
import ij.util.Tools;

/** The plugin implements the Image/Stacks/Tools/Montage to Stack command.
	It creates a w*h image stack from an wxh image montage.
	This is the opposite of what the "Make Montage" command does.
	2010.04.20,TF: Final stack can be cropped to remove border around frames.
*/
public class StackMaker implements PlugIn {
	private int rows;
	private int columns;
	private int border;

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) {
			IJ.noImage();
			return;
		}
		if (imp.getStackSize()>1) {
			IJ.error("This command requires a montage");
			return;
		}		
		String options = Macro.getOptions();
		if (options!=null) {
			options = options.replace("images_per_row=", "columns=");
			options = options.replace("images_per_column=", "rows=");
		}
		columns = info("xMontage", imp, 2);
		rows = info("yMontage", imp, 2);
		String montageHeight = (String)imp.getProperty("yMontage");
		if (montageHeight!=null)
			rows = Integer.parseInt(montageHeight);
		GenericDialog gd = new GenericDialog("Stack Maker");
		gd.addNumericField("Columns: ", columns, 0);
		gd.addNumericField("Rows: ", rows, 0);
		gd.addNumericField("Border width: ", border, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		columns = (int)gd.getNextNumber();
		rows = (int)gd.getNextNumber();
		border = (int)gd.getNextNumber();
		if (rows==0 || columns==0)
			return;
		ImageStack stack = makeStack(imp.getProcessor(), rows, columns, border);
		new ImagePlus("Stack", stack).show();
	}
	
	private int info(String key, ImagePlus imp, int value) {
		String svalue = imp.getStringProperty(key);
		if (svalue!=null)
			value = Integer.parseInt(svalue);
		return value;
	}
	
	public ImageStack makeStack(ImageProcessor ip, int rows, int columns, int border) {
		int stackSize = rows*columns;
		int width = ip.getWidth()/columns;
		int height = ip.getHeight()/rows;
		//IJ.log("makeStack: "+rows+" "+columns+" "+border+" "+width+" "+height);
		ImageStack stack = new ImageStack(width, height);
		for (int y=0; y<rows; y++)
			for (int x=0; x<columns; x++) {
				ip.setRoi(x*width, y*height, width, height);
				stack.addSlice(null, ip.crop());
			}
		if (border>0) { 
			int cropwidth = width-border-border/2;
			int cropheight = height-border-border/2;
			StackProcessor sp = new StackProcessor(stack,ip); 
			stack = sp.crop(border, border, cropwidth, cropheight);
		}
		return stack;
	}	 
}
