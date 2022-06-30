package ij.plugin;
import ij.*;
import ij.gui.GenericDialog;
import ij.process.*;
import ij.measure.Calibration;

/** Implements the Image/Stacks/Tools/Grouped Z Project command. */

public class GroupedZProjector implements PlugIn {
	private static int method = ZProjector.AVG_METHOD;
	private int groupSize;
	
	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		int size = imp.getStackSize();
		if (size==1) {
			IJ.error("Z Project", "This command requires a stack");
			return;
		}
		if (imp.isHyperStack()) {
			new ZProjector().run2(imp, "");
			return;
		}
		if (!showDialog(imp))
			return;
		ImagePlus imp2 = groupZProject(imp, method, groupSize);
		imp2.setCalibration(imp.getCalibration());
		Calibration cal = imp2.getCalibration();
		cal.pixelDepth *= groupSize;
		if (imp!=null)
			imp2.show();
	}
	
	public ImagePlus groupZProject(ImagePlus imp, int method, int groupSize) {
		if (method<0 || method>=ZProjector.METHODS.length)
			return null;
		int[] dim = imp.getDimensions();
		int projectedStackSize = imp.getStackSize()/groupSize;
		imp.setDimensions(1, groupSize, projectedStackSize);
		ZProjector zp = new ZProjector(imp);
		zp.setMethod(method);
		zp.setStartSlice(1);
		zp.setStopSlice(groupSize);
		zp.doHyperStackProjection(true);
		imp.setDimensions(dim[2], dim[3], dim[4]);

		ImagePlus zProjectorOutput = zp.getProjection();
		int[] zProjectDim = zProjectorOutput.getDimensions();
		for (int i=2; i<dim.length; i++) {
			if (dim[i] != 1)
				zProjectDim[i] = projectedStackSize;
			else
				zProjectDim[i] = 1;
		}
		// Fix dimensions for output ImagePlus
		zProjectorOutput.setDimensions(zProjectDim[2], zProjectDim[3], zProjectDim[4]);
		return zProjectorOutput;
	}
	
	boolean showDialog(ImagePlus imp) {
		int size = imp.getStackSize();
		GenericDialog gd = new GenericDialog("Z Project");
		gd.addChoice("Projection method:", ZProjector.METHODS, ZProjector.METHODS[method]);
		gd.addNumericField("Group size:", size, 0);
		String factors = "Valid factors: ";
		int i = 1, count = 0;
		while (i <= size && count<10) {
			if (size % i == 0) {
				count++; factors +=	 " "+ i +",";
			}
			i++;
		}
		gd.setInsets(10,0,0);
		gd.addMessage(factors+"...");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		method = gd.getNextChoiceIndex();
		groupSize = (int)gd.getNextNumber();
		if (groupSize<1 || groupSize>size || (size%groupSize)!=0) {
			IJ.error("ZProject", "Group size must divide evenly into the stack size.");
			return false;
		}
		return true;
	}
	
}