package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.FileInfo;
import java.awt.Color;

/**
 * This plugin implements the Image/Stacks/Tools/Make Substack command.
 * What it does is extracts selected images from a stack to make a new substack.
 * It takes three types of inputs: a range of images (e.g. 2-14), a range of images
 * with an increment (e.g. 2-14-3), or a list of images (e.g. 7,9,25,27,34,132).
 * It then copies those images from the active stack to a new stack in the order
 * of listing or range.
 *
 * @author Anthony Padua
 * @author Daniel Barboriak, MD
 * @author Neuroradiology
 * @author Duke University Medical Center
 *
 * @author Ved P. Sharma, Ph.D.
 * @author Anatomy and Structural Biology
 * @author Albert Einstein College of Medicine
 *
 */

public class SubstackMaker implements PlugIn {
	private static boolean delete = false;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp.isHyperStack() || imp.isComposite()) {
			(new SubHyperstackMaker()).run("");
			return;
		}
		String userInput = showDialog();
		if (userInput==null)
			return;
		ImagePlus imp2 = makeSubstack(imp, userInput);
		if (imp2!=null)
			imp2.show();
	}

	public ImagePlus makeSubstack(ImagePlus imp, String userInput) {
		String stackTitle = "Substack ("+userInput+")";
		if (stackTitle.length()>25) {
			int idxA = stackTitle.indexOf(",",18);
			int idxB = stackTitle.lastIndexOf(",");
			if(idxA>=1 && idxB>=1){
				String strA = stackTitle.substring(0,idxA);
				String strB = stackTitle.substring(idxB+1);
				stackTitle = strA + ", ... " + strB;
			}
		}
		ImagePlus imp2 = null;
		try {
			int idx1 = userInput.indexOf("-");
			if (idx1>=1) {									// input displayed in range
				String rngStart = userInput.substring(0, idx1);
				String rngEnd = userInput.substring(idx1+1);
				Integer obj = new Integer(rngStart);
				int first = obj.intValue();
				int inc = 1;
				int idx2 = rngEnd.indexOf("-");
				if (idx2>=1) {
					String rngEndAndInc = rngEnd;
					rngEnd = rngEndAndInc.substring(0, idx2);
					String rngInc = rngEndAndInc.substring(idx2+1);
					obj = new Integer(rngInc);
					inc = obj.intValue();
				}
				obj = new Integer(rngEnd);
				int last = obj.intValue();
				imp2 = stackRange(imp, first, last, inc, stackTitle);
			} else {
				int count = 1; // count # of slices to extract
				for (int j=0; j<userInput.length(); j++) {
					char ch = Character.toLowerCase(userInput.charAt(j));
					if (ch==',') {count += 1;}
				}
				int[] numList = new int[count];
				for (int i=0; i<count; i++) {
					int idx2 = userInput.indexOf(",");
					if (idx2>0) {
						String num = userInput.substring(0,idx2);
						Integer obj = new Integer(num);
						numList[i] = obj.intValue();
						userInput = userInput.substring(idx2+1);
					} else {
						String num = userInput;
						Integer obj = new Integer(num);
						numList[i] = obj.intValue();
					}
				}
				imp2 = stackList(imp, count, numList, stackTitle);
			}
		} catch (Exception e) {
			IJ.error("Substack Maker", "Invalid input string:        \n \n  \""+userInput+"\"");
		}
		return imp2;
	}
	
	String showDialog() {
		String options = Macro.getOptions();
		if (options!=null && !options.contains("slices=")) {
			Macro.setOptions(options.replace("channels=", "slices="));
			Macro.setOptions(options.replace("frames=", "slices="));
		}
		GenericDialog gd = new GenericDialog("Substack Maker");
		gd.setInsets(10,45,0);
		gd.addMessage("Enter a range (e.g. 2-14), a range with increment\n(e.g. 1-100-2) or a list (e.g. 7,9,25,27)", null, Color.darkGray);
		gd.addStringField("Slices:", "", 40);
		gd.addCheckbox("Delete slices from original stack", delete);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		else {
			delete = gd.getNextBoolean();
			return gd.getNextString();
		}
	}

	// extract specific slices
	ImagePlus stackList(ImagePlus imp, int count, int[] numList, String stackTitle) throws Exception {
		ImageStack stack = imp.getStack();
		ImageStack stack2 = null;
		boolean virtualStack = stack.isVirtual();
		double min = imp.getDisplayRangeMin();
		double max = imp.getDisplayRangeMax();
		Roi roi = imp.getRoi();
		for (int i=0, j=0; i<count; i++) {
			int currSlice = numList[i]-j;
			ImageProcessor ip2 = stack.getProcessor(currSlice);
			ip2.setRoi(roi);
			ip2 = ip2.crop();
			if (stack2==null)
				stack2 = new ImageStack(ip2.getWidth(), ip2.getHeight());
			stack2.addSlice(stack.getSliceLabel(currSlice), ip2);
			if (delete) {
				stack.deleteSlice(currSlice);
				j++;
			}
		}
		if (delete) {
			imp.setStack(stack);
			// next three lines for updating the scroll bar
			ImageWindow win = imp.getWindow();
			StackWindow swin = (StackWindow) win;
			if (swin!=null)
				swin.updateSliceSelector();
		}
		ImagePlus impSubstack = imp.createImagePlus();
		impSubstack.setStack(stackTitle, stack2);
		if (virtualStack)
			impSubstack.setDisplayRange(min, max);
		return impSubstack;
	}
	
	// extract range of slices
	ImagePlus stackRange(ImagePlus imp, int first, int last, int inc, String title) throws Exception {
		ImageStack stack = imp.getStack();
		ImageStack stack2 = null;
		boolean virtualStack = stack.isVirtual();
		double min = imp.getDisplayRangeMin();
		double max = imp.getDisplayRangeMax();
		Roi roi = imp.getRoi();
		boolean showProgress = stack.size()>400 || stack.isVirtual();
		for (int i= first, j=0; i<= last; i+=inc) {
			if (showProgress) IJ.showProgress(i,last);
			int currSlice = i-j;
			ImageProcessor ip2 = stack.getProcessor(currSlice);
			ip2.setRoi(roi);
			ip2 = ip2.crop();
			if (stack2==null)
				stack2 = new ImageStack(ip2.getWidth(), ip2.getHeight());
			stack2.addSlice(stack.getSliceLabel(currSlice), ip2);
			if (delete) {
				stack.deleteSlice(currSlice);
				j++;
			}
		}
		if (delete) {
			imp.setStack(stack);
			// next three lines for updating the scroll bar
			ImageWindow win = imp.getWindow();
			StackWindow swin = (StackWindow) win;
			if (swin!=null)
				swin.updateSliceSelector();
		}
		ImagePlus substack = imp.createImagePlus();
		substack.setStack(title, stack2);
		substack.setCalibration(imp.getCalibration());
		if (virtualStack)
			substack.setDisplayRange(min, max);
		return substack;
	}
}
