package ij.plugin;
import ij.*;
import ij.text.TextWindow;
import ij.util.DicomTools;

/** Displays a compact DICOM acquisition, calibration and stack QA report. */
public class DicomMetadata implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) {
			IJ.noImage();
			return;
		}
		String summary = DicomTools.getMetadataSummary(imp);
		if (summary.length()==0) {
			IJ.error("DICOM Metadata", "The current image does not contain DICOM metadata.");
			return;
		}
		new TextWindow("DICOM Metadata for "+imp.getTitle(), summary, 620, 520);
	}

}
