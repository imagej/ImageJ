package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.filter.*;
import ij.measure.Calibration;
import ij.plugin.frame.Recorder;
import ij.macro.Interpreter;

/** This plugin implements the Process/Image Calculator command.
<pre>
   // test script
   imp1 = IJ.openImage("http://imagej.nih.gov/ij/images/boats.gif")
   imp2 = IJ.openImage("http://imagej.nih.gov/ij/images/bridge.gif")
   imp3 = ImageCalculator.run(imp1, imp2, "add create 32-bit");
   imp3.show();
</pre>
*/
public class ImageCalculator implements PlugIn {

	private static String[] operators = {"Add","Subtract","Multiply","Divide", "AND", "OR", "XOR", "Min", "Max", "Average", "Difference", "Copy", "Transparent-zero"};
	private static String[] lcOperators = {"add","sub","mul","div", "and", "or", "xor", "min", "max", "ave", "diff", "copy", "zero"};
	private static int operator;
	private static String title1 = "";
	private static String title2 = "";
	private static boolean createWindow = true;
	private static boolean floatResult;
	private boolean processStack;
	private boolean macroCall;
	
	public void run(String arg) {
		int[] wList = WindowManager.getIDList();
		if (wList==null) {
			IJ.noImage();
			return;
		}
		IJ.register(ImageCalculator.class);
		String[] titles = new String[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			if (imp!=null)
				titles[i] = imp.getTitle();
			else
				titles[i] = "";
		}
		GenericDialog gd = new GenericDialog("Image Calculator");
		String defaultItem;
		if (title1.equals(""))
			defaultItem = titles[0];
		else
			defaultItem = title1;
		gd.addChoice("Image1:", titles, defaultItem);
		gd.addChoice("Operation:", operators, operators[operator]);
		if (title2.equals(""))
			defaultItem = titles[0];
		else
			defaultItem = title2;
		gd.addChoice("Image2:", titles, defaultItem);
		//gd.addStringField("Result:", "Result", 10);
		gd.addCheckbox("Create new window", createWindow);
		gd.addCheckbox("32-bit (float) result", floatResult);
		gd.addHelp(IJ.URL+"/docs/menus/process.html#calculator");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int index1 = gd.getNextChoiceIndex();
		title1 = titles[index1];
		operator = gd.getNextChoiceIndex();
		int index2 = gd.getNextChoiceIndex();
		//String resultTitle = gd.getNextString();
		createWindow = gd.getNextBoolean();
		floatResult = gd.getNextBoolean();		
		title2 = titles[index2];
		ImagePlus img1 = WindowManager.getImage(wList[index1]);
		ImagePlus img2 = WindowManager.getImage(wList[index2]);
		ImagePlus img3 = calculate(img1, img2, false);
		if (img3!=null) img3.show();
	}
	
	/** Performs arithmetic options on two images and returns the result,
		where  'operation' ("add","subtract", "multiply","divide", "and", 
		"or", "xor", "min", "max", "average", "difference" or "copy")
		specifies the operation. If 'operation' does not contain 'create'
		or '32-bit', the result is also saved in 'imp1" and null is returned
		if "imp1" is displayed. The 'operation' argument can include up
		to three modifiers: "create" (e.g., "add create") causes the result
		to be returned as a new image, "32-bit" causes the result to 
		be returned as 32-bit floating-point image and "stack" causes
		the entire stack to be processed. As an example,
		<pre>
		imp3 = ImageCalculator.run(imp1, imp2, "divide create 32-bit");
		</pre>
		divides 'imp1' by 'imp2' and returns the result as a new 32-bit image.
	*/
	public static ImagePlus run(ImagePlus img1, ImagePlus img2, String operation) {
		ImageCalculator ic = new ImageCalculator();
		return ic.run(operation, img1, img2);
	}

	public ImagePlus run(String operation, ImagePlus img1, ImagePlus img2) {
		if (img1==null || img2==null || operation==null) return null;
		operator = getOperator(operation);
		if (operator==-1)
			throw new IllegalArgumentException("No valid operator");
		createWindow = operation.indexOf("create")!=-1;
		floatResult= operation.indexOf("32")!=-1 || operation.indexOf("float")!=-1;
		processStack = operation.indexOf("stack")!=-1;
		return calculate(img1, img2, true);
	}
	
	/**
	* @deprecated
	* replaced by run(String,ImagePlus,ImagePlus)
	*/
	public void calculate(String operation, ImagePlus img1, ImagePlus img2) {
		if (img1==null || img2==null || operation==null) return;
		operator = getOperator(operation);
		if (operator==-1)
			{IJ.error("Image Calculator", "No valid operator"); return;}
		createWindow = operation.indexOf("create")!=-1;
		floatResult = operation.indexOf("32")!=-1 || operation.indexOf("float")!=-1;
		processStack = operation.indexOf("stack")!=-1;
		macroCall = true;
		ImagePlus img3 = calculate(img1, img2, true);
		if (img3!=null) img3.show();
	}
	
	int getOperator(String options) {
		options = options.toLowerCase();
		int op= -1;
		if  (options.indexOf("xor")!=-1)
			op = 6;
		if (op==-1) {
			for (int i=0; i<lcOperators.length; i++) {
				if (options.indexOf(lcOperators[i])!=-1) {
					op = i;
					break;
				}
			}
		}
		return op;
	}
		
	ImagePlus calculate(ImagePlus img1, ImagePlus img2, boolean apiCall) {
		ImagePlus img3 = null;
		if (img1.getCalibration().isSigned16Bit() || img2.getCalibration().isSigned16Bit())
			floatResult = true;
		if (floatResult && !(img1.getBitDepth()==32&&img2.getBitDepth()==32))
			createWindow = true;
		int size1 = img1.getStackSize();
		int size2 = img2.getStackSize();
		if (apiCall) {
			if (processStack && (size1>1||size2>1))
				img3 = doStackOperation(img1, img2);
			else
				img3 = doOperation(img1, img2);
			if (img3==null && !macroCall && (img1.getWindow()==null))
				img3 = img1;
			return img3;
		}
		boolean stackOp = false;
		if (size1>1) {
			int result = IJ.setupDialog(img1, 0);
			if (result==PlugInFilter.DONE)
				return null;
			if (result==PlugInFilter.DOES_STACKS) {
				img3 = doStackOperation(img1, img2);
				stackOp = true;
			} else
				img3 = doOperation(img1, img2);
		} else
			img3 = doOperation(img1, img2);
		if (Recorder.record) {
			String options = operators[operator];
			if (createWindow) options += " create";
			if (floatResult) options += " 32-bit";
			if (stackOp) options += " stack";
			if (Recorder.scriptMode()) {
				Recorder.recordCall("ImagePlus", "imp1 = WindowManager.getImage(\""+img1.getTitle()+"\");");
				Recorder.recordCall("ImagePlus", "imp2 = WindowManager.getImage(\""+img2.getTitle()+"\");");
				Recorder.recordCall("ImagePlus", "imp3 = ImageCalculator.run(imp1, imp2, \""+options+"\");");
				Recorder.recordCall("imp3.show();");
			} else
				Recorder.record("imageCalculator", options, img1.getTitle(), img2.getTitle());
			Recorder.setCommand(null); // don't record run(...)
		}
		return img3;
	}

	/** img1 = img2 op img2 (e.g. img1 = img2/img1) */
	ImagePlus doStackOperation(ImagePlus img1, ImagePlus img2) {
		ImagePlus img3 = null;
		int size1 = img1.getStackSize();
		int size2 = img2.getStackSize();
		if (size1>1 && size2>1 && size1!=size2) {
			IJ.error("Image Calculator", "'Image1' and 'image2' must be stacks with the same\nnumber of slices, or 'image2' must be a single image.");
			return null;
		}
		if (createWindow) {
			img1 = duplicateStack(img1);
			if (img1==null) {
				IJ.error("Calculator", "Out of memory");
				return null;
			}
			img3 = img1;
		}
		int mode = getBlitterMode();
		ImageWindow win = img1.getWindow();
		if (win!=null)
			WindowManager.setCurrentWindow(win);
		else if (Interpreter.isBatchMode() && !createWindow && WindowManager.getImage(img1.getID())!=null)
			IJ.selectWindow(img1.getID());
		Undo.reset();
		ImageStack stack1 = img1.getStack();
		StackProcessor sp = new StackProcessor(stack1, img1.getProcessor());
		try {
			if (size2==1)
				sp.copyBits(img2.getProcessor(), 0, 0, mode);
			else
				sp.copyBits(img2.getStack(), 0, 0, mode);
		}
		catch (IllegalArgumentException e) {
			IJ.error("\""+img1.getTitle()+"\": "+e.getMessage());
			return null;
		}
		img1.setStack(null, stack1);
		if (img1.getType()!=ImagePlus.GRAY8) {
			img1.getProcessor().resetMinAndMax();
		}
		if (img3==null)
			img1.updateAndDraw();
		return img3;
	}

	ImagePlus doOperation(ImagePlus img1, ImagePlus img2) {
		ImagePlus img3 = null;
		int mode = getBlitterMode();
		ImageProcessor ip1 = img1.getProcessor();
		ImageProcessor ip2 = img2.getProcessor();
		Calibration cal1 = img1.getCalibration();
		Calibration cal2 = img2.getCalibration();
		if (createWindow)
			ip1 = createNewImage(ip1, ip2);
		else {
			ImageWindow win = img1.getWindow();
			if (win!=null)
				WindowManager.setCurrentWindow(win);
			else if (Interpreter.isBatchMode() && !createWindow && WindowManager.getImage(img1.getID())!=null)
				IJ.selectWindow(img1.getID());
			ip1.snapshot();
			Undo.setup(Undo.FILTER, img1);
		}
		boolean rgb = ip2 instanceof ColorProcessor;
		if (floatResult && !rgb)
			ip2 = ip2.convertToFloat();
		try {
  			ip1.copyBits(ip2, 0, 0, mode);
		}
		catch (IllegalArgumentException e) {
			IJ.error("\""+img1.getTitle()+"\": "+e.getMessage());
			return null;
		}
		if (floatResult && rgb)
			ip1 = ip1.convertToFloat();
		if (!(ip1 instanceof ByteProcessor))
			ip1.resetMinAndMax();
		if (createWindow) {
			img3 = new ImagePlus("Result of "+img1.getTitle(), ip1);
			img3.setCalibration(cal1);
		} else
			img1.updateAndDraw();
		return img3;
	}

	ImageProcessor createNewImage(ImageProcessor ip1, ImageProcessor ip2) {
		int width = Math.min(ip1.getWidth(), ip2.getWidth());
		int height = Math.min(ip1.getHeight(), ip2.getHeight());
		ImageProcessor ip3 = ip1.createProcessor(width, height);
		if (floatResult && !(ip1 instanceof ColorProcessor)) {
			ip1 = ip1.convertToFloat();
			ip3 = ip3.convertToFloat();
		}
		ip3.insert(ip1, 0, 0);
		return ip3;
	}

	private int getBlitterMode() {
		int mode=0;
		switch (operator) {
			case 0: mode = Blitter.ADD; break;
			case 1: mode = Blitter.SUBTRACT; break;
			case 2: mode = Blitter.MULTIPLY; break;
			case 3: mode = Blitter.DIVIDE; break;
			case 4: mode = Blitter.AND; break;
			case 5: mode = Blitter.OR; break;
			case 6: mode = Blitter.XOR; break;
			case 7: mode = Blitter.MIN; break;
			case 8: mode = Blitter.MAX; break;
			case 9: mode = Blitter.AVERAGE; break;
			case 10: mode = Blitter.DIFFERENCE; break;
			case 11: mode = Blitter.COPY; break;
			case 12: mode = Blitter.COPY_ZERO_TRANSPARENT; break;
		}
		return mode;
	}
	
	ImagePlus duplicateStack(ImagePlus img1) {
		Calibration cal = img1.getCalibration();
		ImageStack stack1 = img1.getStack();
		int width = stack1.getWidth();
		int height = stack1.getHeight();
		int n = stack1.getSize();
		ImageStack stack2 = img1.createEmptyStack();
		try {
			for (int i=1; i<=n; i++) {
				ImageProcessor ip1 = stack1.getProcessor(i);
				ip1.resetRoi(); 
				ImageProcessor ip2 = ip1.crop();
				if (floatResult) {
					ip2.setCalibrationTable(cal.getCTable());
					ip2 = ip2.convertToFloat();
				} 
				stack2.addSlice(stack1.getSliceLabel(i), ip2);
			}
		}
		catch(OutOfMemoryError e) {
			stack2.trim();
			stack2 = null;
			return null;
		}
		ImagePlus img3 = new ImagePlus("Result of "+img1.getTitle(), stack2);
		img3.setCalibration(cal);
		if (img3.getStackSize()==n) {
			int[] dim = img1.getDimensions();
			img3.setDimensions(dim[2], dim[3], dim[4]);
			if (img1.isComposite()) {
				img3 = new CompositeImage(img3, 0);
				((CompositeImage)img3).copyLuts(img1);
			}
			if (img1.isHyperStack())
				img3.setOpenAsHyperStack(true);
		}
		return img3;
	}
	
}
