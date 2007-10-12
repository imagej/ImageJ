package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.Calibration;
import ij.macro.Interpreter;
import ij.io.FileInfo;


/** Implements the "Stack to HyperVolume", "RGB to HyperVolume" 
	and "HyperVolume to Stack" commands. */
public class HyperVolumeConverter implements PlugIn {
	static final int C=0, Z=1, T=2;
	static final int CZT=0, CTZ=1, ZCT=2, ZTC=3, TCZ=4, TZC=5;
    static final String[] orders = {"xyczt (default)", "xyctz", "xyzct", "xyztc", "xytcz", "xytzc"};
    static int order = CZT;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
    	if (arg.equals("stacktohv"))
    		convertStackToHV(imp);
    	else if (arg.equals("hvtostack"))
    		convertHVToStack(imp);
	}
	
	/** Displays the current stack in a HyperVolume window. Based on the 
		Stack_to_Image5D class in Joachim Walter's Image5D plugin. */
	void convertStackToHV(ImagePlus imp) {
        int nChannels = imp.getNChannels();
        int nSlices = imp.getNSlices();
        int nFrames = imp.getNFrames();
		int stackSize = imp.getStackSize();
		if (stackSize==1) {
			IJ.error("Stack to HyperVolume", "Stack required");
			return;
		}
		GenericDialog gd = new GenericDialog("Convert to HyperVolume");
		gd.addChoice("Order:", orders, orders[order]);
		gd.addNumericField("Channels (c):", nChannels, 0);
		gd.addNumericField("Slices (z):", nSlices, 0);
		gd.addNumericField("Frame (t):", nFrames, 0);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		order = gd.getNextChoiceIndex();
		nChannels = (int) gd.getNextNumber();
		nSlices = (int) gd.getNextNumber();
		nFrames = (int) gd.getNextNumber();
		if (nChannels*nSlices*nFrames!=stackSize) {
			IJ.error("HyperVolume Converter", "channels x slices x frames <> stack size");
			return;
		}
		imp.setDimensions(nChannels, nSlices, nFrames);
		if (order!=CZT && imp.getStack().isVirtual())
			IJ.error("HyperVolume Converter", "Virtual stacks must by in XYCZT order.");
		else {
			shuffle(imp, order);
			imp.setOpenAsHypervolume(true);
			new StackWindow(imp);
		}
	}

	/** Changes the dimension order of a 3D or 4D stack from 
		the specified order (CTZ, ZCT, ZTC, TCZ or TZC) to 
		the XYCZT order used by ImageJ. */
	public void shuffle(ImagePlus imp, int order) {
        int nChannels = imp.getNChannels();
        int nSlices = imp.getNSlices();
        int nFrames = imp.getNFrames();
		int first=C, middle=Z, last=T;
		int nFirst=nChannels, nMiddle=nSlices, nLast=nFrames;
		switch (order) {
			case CTZ: first=C; middle=T; last=Z;
				nFirst=nChannels; nMiddle=nFrames; nLast=nSlices;
				break;
			case ZCT: first=Z; middle=C; last=T;
				nFirst=nSlices; nMiddle=nChannels; nLast=nFrames;
				break;
			case ZTC: first=Z; middle=T; last=C;
				nFirst=nSlices; nMiddle=nFrames; nLast=nChannels;
				break;
			case TCZ: first=T; middle=C; last=Z;
				nFirst=nFrames; nMiddle=nChannels; nLast=nSlices;
				break;
			case TZC: first=T; middle=Z; last=C;
				nFirst=nFrames; nMiddle=nSlices; nLast=nChannels;
				break;
		}
		if (order!=CZT) {
			Object[] images1 = imp.getStack().getImageArray();
			Object[] images2 = new Object[images1.length];
			System.arraycopy(images1, 0, images2, 0, images1.length);
			int[] index = new int[3];
			for (index[2]=0; index[2]<nFrames; ++index[2]) {
				for (index[1]=0; index[1]<nSlices; ++index[1]) {
					for (index[0]=0; index[0]<nChannels; ++index[0]) {
						int dstIndex = index[0] + index[1]*nChannels + index[2]*nChannels*nSlices;
						int srcIndex = index[first] + index[middle]*nFirst + index[last]*nFirst*nMiddle;
						images1[dstIndex] = images2[srcIndex];
					}
				}
			}
		}
	}

	void convertHVToStack(ImagePlus imp) {
		imp.setOpenAsHypervolume(false);
		new StackWindow(imp);
	}
	
}

