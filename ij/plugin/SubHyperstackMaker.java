package ij.plugin;
import ij.*;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import ij.process.LUT;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;

/**
 * This plugin is used by the Image/Stacks/Tools/Make Substack
 * command to create substacks of hyperstacks.
 *  
 * @author Curtis Rueden
 */
public class SubHyperstackMaker implements PlugIn {

	public void run(String arg) {
		// verify input image is appropriate
		ImagePlus input = WindowManager.getCurrentImage();
		if (input == null) {
			IJ.showMessage("No image open.");
			return;
		}
		if (input.getStackSize() == 1) {
			IJ.showMessage("Image is not a stack.");
			return;
		}
		int cCount = input.getNChannels();
		int zCount = input.getNSlices();
		int tCount = input.getNFrames();
		boolean hasC = cCount > 1;
		boolean hasZ = zCount > 1;
		boolean hasT = tCount > 1;

		// prompt for C, Z and T ranges
		GenericDialog gd = new GenericDialog("Subhyperstack Maker");
		gd.addMessage("Enter a range (e.g. 2-14), a range with increment\n"
			+ "(e.g. 1-100-2) or a list (e.g. 7,9,25,27)", null, Color.darkGray);
		if (hasC) gd.addStringField("Channels:", "1-" + cCount, 40);
		if (hasZ) gd.addStringField("Slices:", "1-" + zCount, 40);
		if (hasT) gd.addStringField("Frames:", "1-" + tCount, 40);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		String cString = hasC ? gd.getNextString() : "1";
		String zString = hasZ ? gd.getNextString() : "1";
		String tString = hasT ? gd.getNextString() : "1";

		// compute subhyperstack
		ImagePlus output = makeSubhyperstack(input, cString, zString, tString);

		// display result
		output.show();
	}

	public static ImagePlus makeSubhyperstack(ImagePlus input, String cString, String zString, String tString) {
		ArrayList<Integer> cList = parseList(cString, input.getNChannels());
		ArrayList<Integer> zList = parseList(zString, input.getNSlices());
		ArrayList<Integer> tList = parseList(tString, input.getNFrames());
		return makeSubhyperstack(input, cList, zList, tList);
	}

	public static ImagePlus makeSubhyperstack(ImagePlus input, List<Integer> cList, List<Integer> zList, List<Integer> tList) {
		// validate inputs
		if (cList.size() == 0)
			throw new IllegalArgumentException("Must specify at least one channel");
		if (zList.size() == 0)
			throw new IllegalArgumentException("Must specify at least one slice");
		if (tList.size() == 0)
			throw new IllegalArgumentException("Must specify at least one frame");

		ImageStack inputStack = input.getImageStack();

		int cCount = input.getNChannels();
		int zCount = input.getNSlices();
		int tCount = input.getNFrames();

		for (int c : cList)
			check("C", c, cCount);
		for (int z : zList)
			check("Z", z, zCount);
		for (int t : tList)
			check("T", t, tCount);

		// create output image
		String title = WindowManager.getUniqueName(input.getTitle());
		ImagePlus output = IJ.createHyperStack(title, input.getWidth(), input.getHeight(), cList.size(), zList.size(), tList.size(), input.getBitDepth());
		//ImagePlus output = input.createHyperStack(title, cList.size(), zList.size(), tList.size(), input.getBitDepth());	
		ImageStack outputStack = output.getImageStack();

		// add specified planes to subhyperstack
		int oc = 0, oz, ot;
		for (int c : cList) {
			oc++;
			oz = 0;
			for (int z : zList) {
				oz++;
				ot = 0;
				for (int t : tList) {
					ot++;
					int i = input.getStackIndex(c, z, t);
					int oi = output.getStackIndex(oc, oz, ot);
					String label = inputStack.getSliceLabel(i);
					ImageProcessor ip = inputStack.getProcessor(i);
					outputStack.setSliceLabel(label, oi);
					outputStack.setPixels(ip.getPixels(), oi);
					//IJ.log("  "+c + "  "+z+"  "+t+"  "+i +" "+oi+"  "+outputStack.getProcessor(1).getPixelValue(0,0));	
				}
			}
		}
		output.setStack(outputStack);

		// propagate composite image settings, if appropriate
		if (input instanceof CompositeImage) {
			CompositeImage compositeInput = (CompositeImage) input;
			CompositeImage compositeOutput =
				new CompositeImage(output, compositeInput.getMode());
			oc = 0;
			for (int c : cList) {
				oc++;
				LUT table = compositeInput.getChannelLut(c);
				compositeOutput.setChannelLut(table, oc);
				compositeOutput.setPositionWithoutUpdate(oc, 1, 1);
				compositeInput.setPositionWithoutUpdate(c, 1, 1);
				double min = compositeInput.getDisplayRangeMin();
				double max = compositeInput.getDisplayRangeMax();
				compositeOutput.setDisplayRange(min, max);
			}
			output = compositeOutput;
		}
		return output;
	}

	private static void check(String name, int index, int count) {
		if (index < 1 || index > count) {
			throw new IllegalArgumentException("Invalid " + name + " index: " +
				index);
		}
	}

	private static ArrayList<Integer> parseList(String planeString, int count) {
		ArrayList<Integer> list = new ArrayList<Integer>();
		for (String token : planeString.split("\\s*,\\s*")) {
			int dash1 = token.indexOf("-");
			int dash2 = token.lastIndexOf("-");
			if (dash1 < 0) {
				// single number
				int index;
				try {
					index = Integer.parseInt(token);
				} catch (NumberFormatException exc) {
					throw new IllegalArgumentException("Invalid number: " + token);
				}
				if (index < 1 || index > count)
					throw new IllegalArgumentException("Invalid number: " + token);
				list.add(Integer.parseInt(token));
			} else {
				// range, with or without increment
				int min, max, step;
				try {
					min = Integer.parseInt(token.substring(0, dash1));
					if (dash1 == dash2) {
						// range (e.g. 2-14)
						max = Integer.parseInt(token.substring(dash1 + 1));
						step = 1;
					} else {
						// range with increment (e.g. 1-100-2)
						max = Integer.parseInt(token.substring(dash1 + 1, dash2));
						step = Integer.parseInt(token.substring(dash2 + 1));
					}
				} catch (NumberFormatException exc) {
					throw new IllegalArgumentException("Invalid range: " + token);
				}
				if (min < 1 || min > max || max > count || step < 1)
					throw new IllegalArgumentException("Invalid range: " + token);
				for (int index = min; index <= max; index += step)
					list.add(index);
			}
		}
		return list;
	}

}
