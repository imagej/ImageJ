package ij.gui;
import ij.ImagePlus;
	
	/** Plugins that implement this interface are notified when
		an ROI is created, modified or deleted. The 
		Plugins/Utilities/Monitor Events command uses this interface.
	*/
	public interface RoiListener {
		public static final int CREATED = 1;
		public static final int MOVED = 2;
		public static final int MODIFIED = 3;
		public static final int EXTENDED = 4;
		public static final int DELETED = 5;

	public void roiModified(ImagePlus imp, int id);

}
