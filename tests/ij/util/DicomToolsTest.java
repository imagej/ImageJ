package ij.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ShortProcessor;

import org.junit.Test;

/**
 * Unit tests for {@link DicomTools}.
 */
public class DicomToolsTest {

	@Test
	public void testGetTagFromMetadata() {
		String metadata = line("0008,0060", "Modality", "CT") +
			line("0028,0030", "Pixel Spacing", "0.5\\0.5");
		assertEquals(" CT", DicomTools.getTag(metadata, "0008,0060"));
		assertEquals(" CT", DicomTools.getTag(metadata, "00080060"));
		assertEquals(" 0.5\\0.5", DicomTools.getTag(metadata, "0028,0030"));
		assertNull(DicomTools.getTag(metadata, "0018,0050"));
	}

	@Test
	public void testGetTagSkipsSequenceValues() {
		String metadata = "0010,0010  >Patient's Name: Hidden\n" +
			line("0010,0010", "Patient's Name", "Visible");
		assertEquals(" Visible", DicomTools.getTag(metadata, "0010,0010"));
	}

	@Test
	public void testMetadataSummaryReportsCalibrationAndStackQA() {
		ImageStack stack = new ImageStack(2, 2);
		stack.addSlice("slice 1\n" + metadata("0"), new short[4]);
		stack.addSlice("slice 2\n" + metadata("1.25"), new short[4]);
		ImagePlus imp = new ImagePlus("QA Stack", stack);
		Calibration cal = imp.getCalibration();
		cal.pixelWidth = 0.5;
		cal.pixelHeight = 0.5;
		cal.pixelDepth = 1.25;
		cal.setUnit("mm");
		String summary = DicomTools.getMetadataSummary(imp);
		assertTrue(summary.indexOf("DICOM Metadata Summary")>=0);
		assertTrue(summary.indexOf("Modality: CT")>=0);
		assertTrue(summary.indexOf("DICOM Pixel Spacing: 0.5 x 0.5 mm (x by y)")>=0);
		assertTrue(summary.indexOf("ImageJ Calibration: 0.5 x 0.5 x 1.25 mm")>=0);
		assertTrue(summary.indexOf("Pixel Spacing: consistent")>=0);
		assertTrue(summary.indexOf("Image Position spacing: 1.25 mm mean")>=0);
		assertFalse(summary.indexOf("Warning:")>=0);
	}

	@Test
	public void testMetadataSummaryWarnsForCalibrationMismatch() {
		ImagePlus imp = new ImagePlus("Mismatch", new ShortProcessor(2, 2));
		imp.setProperty("Info", metadata("0"));
		Calibration cal = imp.getCalibration();
		cal.pixelWidth = 1.0;
		cal.pixelHeight = 1.0;
		cal.pixelDepth = 2.0;
		cal.setUnit("mm");
		String summary = DicomTools.getMetadataSummary(imp);
		assertTrue(summary.indexOf("Warning: ImageJ XY calibration differs from DICOM pixel spacing.")>=0);
		assertTrue(summary.indexOf("Warning: ImageJ Z calibration differs from DICOM slice spacing/thickness.")>=0);
	}

	private static String metadata(String z) {
		return line("0008,0060", "Modality", "CT") +
			line("0020,000E", "Series Instance UID", "1.2.3") +
			line("0020,0052", "Frame of Reference UID", "1.2.3.4") +
			line("0028,0010", "Rows", "2") +
			line("0028,0011", "Columns", "2") +
			line("0028,0030", "Pixel Spacing", "0.5\\0.5") +
			line("0018,0050", "Slice Thickness", "1.25") +
			line("0018,0088", "Spacing Between Slices", "1.25") +
			line("0020,0032", "Image Position (Patient)", "0\\0\\" + z) +
			line("0020,0037", "Image Orientation (Patient)", "1\\0\\0\\0\\1\\0") +
			line("0028,1053", "Rescale Slope", "1") +
			line("0028,1052", "Rescale Intercept", "-1024") +
			line("7FE0,0010", "Pixel Data", "0");
	}

	private static String line(String tag, String name, String value) {
		return tag + "  " + name + ": " + value + "\n";
	}

}
