package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;

public class ImageCalculator implements PlugIn {

	private static int operator;
	private static String title1 = "";
	private static String title2 = "";
	private static boolean createWindow = true;
	
	public void run(String arg) {
		int[] wList = WindowManager.getIDList();
		if (wList==null) {
			IJ.error("No windows are open.");
			return;
		}
		IJ.register(ImageCalculator.class);
		String[] titles = new String[wList.length];
		String[] operators = {"Add","Subtract","Multiply","Divide", "AND", "OR", "XOR", "Min", "Max", "Average", "Difference", "Copy"};
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			if (imp!=null)
				titles[i] = imp.getTitle();
			else
				titles[i] = "";
		}
		GenericDialog gd = new GenericDialog("Image Calculator", IJ.getInstance());
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
		gd.addCheckbox("Create New Window", createWindow);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int index1 = gd.getNextChoiceIndex();
		title1 = titles[index1];
		operator = gd.getNextChoiceIndex();
		int index2 = gd.getNextChoiceIndex();
		//String resultTitle = gd.getNextString();
		createWindow = gd.getNextBoolean();
		title2 = titles[index2];
		ImagePlus img1 = WindowManager.getImage(wList[index1]);
		ImagePlus img2 = WindowManager.getImage(wList[index2]);
		int size1 = img1.getStackSize();
		int size2 = img2.getStackSize();
		if (size1>1 && size2>1 && size1!=size2) {
			IJ.showMessage("Image Calculator", "Both stacks must have the same number of sizes.");
			return;
		}
		if (size1>1 && (size2==1||size1==size2)) {
			YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(),
				"Process Stack?", "Process all "+size1+" slices?");
			if (d.cancelPressed())
				return;
			if (d.yesPressed())
				doStackOperation(img1, img2);
			else
				doOperation(img1, img2);
		} else
			doOperation(img1, img2);
	}
	
	/** img1 = img2 op img2 (e.g. img1 = img2/img1) */
	void doStackOperation(ImagePlus img1, ImagePlus img2) {
		if (createWindow) {
			img1 = duplicateStack(img1);
			if (img1==null) {
				IJ.showMessage("Calculator", "Out of memory");
				return;
			}
			img1.show();
		}			
		int mode = getBlitterMode();
		ImageWindow win = img1.getWindow();
		if (win!=null)
			WindowManager.setCurrentWindow(win);
		Undo.reset();
		ImageStack stack1 = img1.getStack();
		StackProcessor sp = new StackProcessor(stack1, img1.getProcessor());
		try {
			if (img2.getStackSize()==1)
				sp.copyBits(img2.getProcessor(), 0, 0, mode);
			else
				sp.copyBits(img2.getStack(), 0, 0, mode);
		}
		catch (IllegalArgumentException e) {
			IJ.error("\""+img1.getTitle()+"\": "+e.getMessage());
			return;
		}
		img1.setStack(null, stack1);
		img1.updateAndDraw();
	}

	void doOperation(ImagePlus img1, ImagePlus img2) {
		int mode = getBlitterMode();
		ImageProcessor ip1 = img1.getProcessor();
		ImageProcessor ip2 = img2.getProcessor();
		if (createWindow)
			ip1 = createNewImage(ip1, ip2);
		else {
			ImageWindow win = img1.getWindow();
			if (win!=null)
				WindowManager.setCurrentWindow(win);
			ip1.snapshot();
			Undo.setup(Undo.FILTER, img1);
		}
		try {
			ip1.copyBits(ip2, 0, 0, mode);
		}
		catch (IllegalArgumentException e) {
			IJ.error("\""+img1.getTitle()+"\": "+e.getMessage());
			return;
		}
		if (createWindow)
			new ImagePlus("Result", ip1).show();
		else
			img1.updateAndDraw();
	}

	ImageProcessor createNewImage(ImageProcessor ip1, ImageProcessor ip2) {
		int width = Math.min(ip1.getWidth(), ip2.getWidth());
		int height = Math.min(ip1.getHeight(), ip2.getHeight());
		ImageProcessor ip3 = ip1.createProcessor(width, height);
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
		}
		return mode;
	}
	
	ImagePlus duplicateStack(ImagePlus img1) {
		ImageStack stack1 = img1.getStack();
		int width = stack1.getWidth();
		int height = stack1.getHeight();
		int n = stack1.getSize();
		ImageStack stack2 = img1.createEmptyStack();
		try {
			for (int i=1; i<=n; i++) {
				ImageProcessor ip1 = stack1.getProcessor(i);
				ip1.setRoi(null); 
				ImageProcessor ip2 = ip1.crop(); 
				stack2.addSlice(stack1.getSliceLabel(i), ip2);
			}
		}
		catch(OutOfMemoryError e) {
			stack2.trim();
			stack2 = null;
			return null;
		}
		return new ImagePlus("Result", stack2);
	}

}
