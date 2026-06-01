package ij.util;
import ij.*;
import ij.process.*;
import ij.plugin.DICOM;
import java.util.HashSet;
import java.util.Locale;

/** DICOM utilities */
public class DicomTools {
	private static final int MAX_DIGITS = 5;
	private static final double SPACING_TOLERANCE = 1.0e-4;
	private static String[] sliceLabels;

	private static final String[][] SUMMARY_TAGS = {
		{"Modality", "0008,0060"},
		{"Manufacturer", "0008,0070"},
		{"Model", "0008,1090"},
		{"Protocol", "0018,1030"},
		{"Series Description", "0008,103E"},
		{"Body Part", "0018,0015"},
		{"Scan Options", "0018,0022"},
		{"Sequence Name", "0018,0024"},
		{"Magnetic Field Strength", "0018,0087"},
		{"Repetition Time", "0018,0080"},
		{"Echo Time", "0018,0081"},
		{"KVP", "0018,0060"},
		{"Exposure Time", "0018,1150"},
		{"X-Ray Tube Current", "0018,1151"},
		{"Photometric Interpretation", "0028,0004"},
		{"Transfer Syntax UID", "0002,0010"},
		{"Rescale Slope", "0028,1053"},
		{"Rescale Intercept", "0028,1052"},
		{"Rescale Type", "0028,1054"}
	};

	/** Sorts a DICOM stack by image number. */
	public static ImageStack sort(ImageStack stack) {
		if (IJ.debugMode) IJ.log("Sorting by DICOM image number");
		if (stack.size()==1) return stack;
		String[] strings = getSortStrings(stack, "0020,0013");
		if (strings==null) return stack;
		StringSorter.sort(strings);
		ImageStack stack2 = null;
		if (stack.isVirtual())
			stack2 = ((VirtualStack)stack).sortDicom(strings, sliceLabels, MAX_DIGITS);
		else
			stack2 = sortStack(stack, strings);
		return stack2!=null?stack2:stack;
	}
	
	private static ImageStack sortStack(ImageStack stack, String[] strings) {
		ImageProcessor ip = stack.getProcessor(1);
		ImageStack stack2 = new ImageStack(ip.getWidth(), ip.getHeight(), ip.getColorModel());
		for (int i=0; i<stack.size(); i++) {
			int slice = (int)Tools.parseDouble(strings[i].substring(strings[i].length()-MAX_DIGITS), 0.0);
			if (slice==0) return null;
			stack2.addSlice(sliceLabels[slice-1], stack.getPixels(slice));
		}
		stack2.update(stack.getProcessor(1));
		return stack2;
	}

	private static String[] getSortStrings(ImageStack stack, String tag) {
		double series = getSeriesNumber(getSliceLabel(stack,1));
		int n = stack.size();
		boolean checkRescaleSlope = (stack instanceof VirtualStack)?((VirtualStack)stack).getBitDepth()==16:false;
		if (Prefs.ignoreRescaleSlope)
			checkRescaleSlope = false;
		boolean showError = false;
		String[] values = new String[n];
		sliceLabels = new String[n];
		for (int i=1; i<=n; i++) {
			String tags = getSliceLabel(stack,i);
			if (tags==null) return null;
			sliceLabels[i-1] = tags;
			double value = getNumericTag(tags, tag);
			if (Double.isNaN(value)) {
				if (IJ.debugMode) IJ.log("  "+tag+"  tag missing in slice "+i);
				if (showError) rescaleSlopeError(stack);
				return null;
			}
			if (getSeriesNumber(tags)!=series) {
				if (IJ.debugMode) IJ.log("  all slices must be part of the same series");
				if (showError) rescaleSlopeError(stack);
				return null;
			}
			values[i-1] = toString(value, MAX_DIGITS) + toString(i, MAX_DIGITS);
			if (checkRescaleSlope) {
				double rescaleSlope = getNumericTag(tags, "0028,1053");
				if (rescaleSlope!=1.0)
					showError = true;
			}
		}
		if (showError) rescaleSlopeError(stack);
		return values;
	}
	
	private static void rescaleSlopeError(ImageStack stack) {
		((VirtualStack)stack).setBitDepth(32);
	}

	private static String toString(double value, int width) {
		String s = "       " + IJ.d2s(value,0);
		return s.substring(s.length()-MAX_DIGITS);
	}
	
	private static String getSliceLabel(ImageStack stack, int n) {
		String info = stack.getSliceLabel(n);
		if ((info==null || info.length()<100) && stack.isVirtual()) {
			String dir = ((VirtualStack)stack).getDirectory();
			String name = ((VirtualStack)stack).getFileName(n);
			DICOM reader = new DICOM();
			info = reader.getInfo(dir+name);
			if (info!=null)
				info = name + "\n" + info;
		}
		return info;
	}

	/** Calculates the voxel depth of the specified DICOM stack based 
		on the distance between the first and last slices. */
	public static double getVoxelDepth(ImageStack stack) {
		if (stack.isVirtual()) stack.getProcessor(1);
		String pos0 = getTag(stack.getSliceLabel(1), "0020,0032");
		String posn = null;
		double voxelDepth = -1.0;
		if (pos0!=null) {
			String[] xyz = pos0.split("\\\\");
			if (xyz.length!=3) return voxelDepth;
			double z0 = Double.parseDouble(xyz[2]);
			if (stack.isVirtual()) stack.getProcessor(stack.size());
			posn = getTag(stack.getSliceLabel(stack.size()), "0020,0032");
			if (posn==null) return voxelDepth;
			xyz = posn.split("\\\\");
			if (xyz.length!=3) return voxelDepth;
			double zn = Double.parseDouble(xyz[2]);
			voxelDepth = Math.abs((zn - z0) / (stack.size() - 1));
		}
		if (IJ.debugMode) IJ.log("DicomTools.getVoxelDepth: "+voxelDepth+"  "+pos0+"  "+posn);
		return voxelDepth;
	}

	/** Returns the value (as a string) of the specified DICOM tag id (in the form "0018,0050")
		of the specified image or stack slice. Returns null if the tag id is not found. */
	public static String getTag(ImagePlus imp, String id) {
		String metadata = null;
		if (imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			String label = stack.getSliceLabel(imp.getCurrentSlice());
			if (label!=null && label.indexOf('\n')>0) metadata = label;
		}
		if (metadata==null)
			metadata = (String)imp.getProperty("Info");
		return getTag(metadata, id);
	}
	
	/** Returns the name of the specified DICOM tag id. */
	public static String getTagName(String id) {
		return DICOM.getTagName(id);
	}

	/** Returns the DICOM metadata string for the current image or stack slice. */
	public static String getDicomMetadata(ImagePlus imp) {
		if (imp==null)
			return null;
		String metadata = null;
		if (imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			String label = stack.getSliceLabel(imp.getCurrentSlice());
			if (label!=null && label.indexOf('\n')>0)
				metadata = label;
		}
		if (metadata==null)
			metadata = (String)imp.getProperty("Info");
		return isDicomMetadata(metadata)?metadata:null;
	}

	/** Returns a compact DICOM metadata and stack QA report that avoids direct patient ID tags. */
	public static String getMetadataSummary(ImagePlus imp) {
		String metadata = getDicomMetadata(imp);
		if (metadata==null)
			return "";
		StringBuffer sb = new StringBuffer(2048);
		sb.append("DICOM Metadata Summary\n");
		sb.append("Image: ").append(imp.getTitle()).append("\n");
		sb.append("Dimensions: ").append(imp.getWidth()).append(" x ").append(imp.getHeight());
		if (imp.getStackSize()>1)
			sb.append(" x ").append(imp.getStackSize());
		sb.append("\n");
		sb.append("Bit depth: ").append(imp.getBitDepth()).append("-bit\n");
		appendSummaryTags(sb, metadata);
		appendCalibrationSummary(sb, imp, metadata);
		if (imp.getStackSize()>1)
			appendStackSummary(sb, imp.getStack());
		return sb.toString();
	}
		
	private static double getSeriesNumber(String tags) {
		double series = getNumericTag(tags, "0020,0011");
		if (Double.isNaN(series)) series = 0;
		return series;
	}

	private static double getNumericTag(String hdr, String tag) {
		String value = getTag(hdr, tag);
		if (value==null) return Double.NaN;
		int index3 = value.indexOf("\\");
		if (index3>0)
			value = value.substring(0, index3);
		return Tools.parseDouble(value);
	}

	/** Returns the value of a DICOM tag in an ImageJ DICOM metadata string. */
	public static String getTag(String hdr, String tag) {
		if (hdr==null) return null;
		tag = normalizeTag(tag);
		if (tag==null) return null;
		int index1 = hdr.indexOf(tag);
		if (index1==-1) return null;
		//IJ.log(hdr.charAt(index1+11)+"   "+hdr.substring(index1,index1+20));
		if (index1+11<hdr.length() && hdr.charAt(index1+11)=='>') {
			// ignore tags in sequences
			index1 = hdr.indexOf(tag, index1+10);
			if (index1==-1) return null;
		}
		index1 = hdr.indexOf(":", index1);
		if (index1==-1) return null;
		int index2 = hdr.indexOf("\n", index1);
		if (index2==-1) index2 = hdr.length();
		String value = hdr.substring(index1+1, index2);
		return value;
	}

	private static void appendSummaryTags(StringBuffer sb, String metadata) {
		sb.append("\nAcquisition\n");
		for (int i=0; i<SUMMARY_TAGS.length; i++)
			appendTag(sb, metadata, SUMMARY_TAGS[i][0], SUMMARY_TAGS[i][1]);
	}

	private static void appendCalibrationSummary(StringBuffer sb, ImagePlus imp, String metadata) {
		ij.measure.Calibration cal = imp.getCalibration();
		String spacing = cleanTag(metadata, "0028,0030");
		String imagerSpacing = cleanTag(metadata, "0018,1164");
		String sliceThickness = cleanTag(metadata, "0018,0050");
		String spacingBetweenSlices = cleanTag(metadata, "0018,0088");
		sb.append("\nGeometry and Calibration QA\n");
		if (spacing!=null)
			sb.append("DICOM Pixel Spacing: ").append(formatPixelSpacing(spacing)).append("\n");
		else if (imagerSpacing!=null)
			sb.append("DICOM Imager Pixel Spacing: ").append(formatPixelSpacing(imagerSpacing)).append("\n");
		else
			sb.append("DICOM Pixel Spacing: missing\n");
		appendTag(sb, metadata, "Slice Thickness", "0018,0050");
		appendTag(sb, metadata, "Spacing Between Slices", "0018,0088");
		appendTag(sb, metadata, "Image Orientation", "0020,0037");
		appendTag(sb, metadata, "Image Position", "0020,0032");
		sb.append("ImageJ Calibration: ").append(formatNumber(cal.pixelWidth)).append(" x ")
			.append(formatNumber(cal.pixelHeight)).append(" x ")
			.append(formatNumber(cal.pixelDepth)).append(" ")
			.append(cal.getUnit()).append("\n");
		appendCalibrationComparison(sb, cal, spacing, imagerSpacing, sliceThickness, spacingBetweenSlices);
	}

	private static void appendCalibrationComparison(StringBuffer sb, ij.measure.Calibration cal, String spacing, String imagerSpacing, String sliceThickness, String spacingBetweenSlices) {
		String pixelSpacing = spacing!=null?spacing:imagerSpacing;
		double[] xy = parseDoubles(pixelSpacing);
		if (xy!=null && xy.length>=2 && isMillimeterUnit(cal.getXUnit()) && isMillimeterUnit(cal.getYUnit())) {
			double dicomY = xy[0];
			double dicomX = xy[1];
			if (!nearlyEqual(cal.pixelWidth, dicomX) || !nearlyEqual(cal.pixelHeight, dicomY))
				sb.append("Warning: ImageJ XY calibration differs from DICOM pixel spacing.\n");
		}
		double z = getFirstNumber(spacingBetweenSlices);
		if (Double.isNaN(z))
			z = getFirstNumber(sliceThickness);
		if (!Double.isNaN(z) && isMillimeterUnit(cal.getZUnit()) && !nearlyEqual(cal.pixelDepth, z))
			sb.append("Warning: ImageJ Z calibration differs from DICOM slice spacing/thickness.\n");
	}

	private static void appendStackSummary(StringBuffer sb, ImageStack stack) {
		sb.append("\nStack QA\n");
		sb.append("Slices assessed: ").append(stack.size()).append("\n");
		appendConsistencyCheck(sb, stack, "Series Instance UID", "0020,000E");
		appendConsistencyCheck(sb, stack, "Frame of Reference UID", "0020,0052");
		appendConsistencyCheck(sb, stack, "Rows", "0028,0010");
		appendConsistencyCheck(sb, stack, "Columns", "0028,0011");
		appendConsistencyCheck(sb, stack, "Pixel Spacing", "0028,0030");
		appendConsistencyCheck(sb, stack, "Image Orientation", "0020,0037");
		appendConsistencyCheck(sb, stack, "Rescale Slope", "0028,1053");
		appendConsistencyCheck(sb, stack, "Rescale Intercept", "0028,1052");
		appendPositionSpacingCheck(sb, stack);
	}

	private static void appendConsistencyCheck(StringBuffer sb, ImageStack stack, String label, String tag) {
		HashSet values = new HashSet();
		int missing = 0;
		for (int i=1; i<=stack.size(); i++) {
			String value = cleanTag(getSliceLabel(stack, i), tag);
			if (value==null)
				missing++;
			else
				values.add(value);
		}
		sb.append(label).append(": ");
		if (missing==stack.size())
			sb.append("missing");
		else if (values.size()==1 && missing==0)
			sb.append("consistent");
		else {
			sb.append("varies");
			if (values.size()>0)
				sb.append(" (").append(values.size()).append(" values");
			if (missing>0)
				sb.append(values.size()>0?", ":" (").append(missing).append(" missing");
			sb.append(")");
		}
		sb.append("\n");
	}

	private static void appendPositionSpacingCheck(StringBuffer sb, ImageStack stack) {
		double[] previous = null;
		int previousSlice = 0;
		int missing = 0;
		int intervals = 0;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		double sum = 0.0;
		for (int i=1; i<=stack.size(); i++) {
			double[] position = parseTriplet(cleanTag(getSliceLabel(stack, i), "0020,0032"));
			if (position==null) {
				missing++;
				continue;
			}
			if (previous!=null && i==previousSlice+1) {
				double distance = distance(previous, position);
				if (distance<min) min = distance;
				if (distance>max) max = distance;
				sum += distance;
				intervals++;
			}
			previous = position;
			previousSlice = i;
		}
		sb.append("Image Position spacing: ");
		if (intervals==0)
			sb.append("not available");
		else {
			double mean = sum/intervals;
			sb.append(formatNumber(mean)).append(" mm mean");
			if (!nearlyEqual(min, max))
				sb.append(" (range ").append(formatNumber(min)).append("-").append(formatNumber(max)).append(")");
			if (spacingVariationIsHigh(min, max, mean))
				sb.append(" - warning: non-uniform");
		}
		if (missing>0)
			sb.append(" (").append(missing).append(" missing positions)");
		sb.append("\n");
	}

	private static void appendTag(StringBuffer sb, String metadata, String label, String tag) {
		String value = cleanTag(metadata, tag);
		if (value!=null)
			sb.append(label).append(": ").append(value).append("\n");
	}

	private static String cleanTag(String metadata, String tag) {
		return cleanValue(getTag(metadata, tag));
	}

	private static String cleanValue(String value) {
		if (value==null)
			return null;
		value = value.trim();
		return value.length()>0?value:null;
	}

	private static String formatPixelSpacing(String spacing) {
		double[] xy = parseDoubles(spacing);
		if (xy!=null && xy.length>=2)
			return formatNumber(xy[1])+" x "+formatNumber(xy[0])+" mm (x by y)";
		return spacing;
	}

	private static String formatNumber(double value) {
		String s = IJ.d2s(value, 6, 9);
		int exponent = s.indexOf('E');
		String suffix = "";
		if (exponent>=0) {
			suffix = s.substring(exponent);
			s = s.substring(0, exponent);
		}
		if (s.indexOf('.')>=0) {
			while (s.endsWith("0"))
				s = s.substring(0, s.length()-1);
			if (s.endsWith("."))
				s = s.substring(0, s.length()-1);
		}
		return s + suffix;
	}

	private static double[] parseTriplet(String value) {
		double[] values = parseDoubles(value);
		if (values==null || values.length<3)
			return null;
		return values;
	}

	private static double[] parseDoubles(String value) {
		if (value==null)
			return null;
		String[] parts = value.split("\\\\");
		double[] values = new double[parts.length];
		for (int i=0; i<parts.length; i++) {
			values[i] = Tools.parseDouble(parts[i].trim());
			if (Double.isNaN(values[i]))
				return null;
		}
		return values;
	}

	private static double getFirstNumber(String value) {
		double[] values = parseDoubles(value);
		return values!=null && values.length>0?values[0]:Double.NaN;
	}

	private static double distance(double[] p1, double[] p2) {
		double dx = p2[0] - p1[0];
		double dy = p2[1] - p1[1];
		double dz = p2[2] - p1[2];
		return Math.sqrt(dx*dx + dy*dy + dz*dz);
	}

	private static boolean nearlyEqual(double a, double b) {
		double tolerance = Math.max(Math.abs(a), Math.abs(b))*SPACING_TOLERANCE;
		if (tolerance<1.0e-6)
			tolerance = 1.0e-6;
		return Math.abs(a-b)<=tolerance;
	}

	private static boolean spacingVariationIsHigh(double min, double max, double mean) {
		if (mean==0.0)
			return false;
		return Math.abs(max-min)/Math.abs(mean)>SPACING_TOLERANCE;
	}

	private static boolean isMillimeterUnit(String unit) {
		if (unit==null)
			return false;
		unit = unit.toLowerCase(Locale.US);
		return unit.equals("mm") || unit.equals("millimeter") || unit.equals("millimeters");
	}

	private static boolean isDicomMetadata(String metadata) {
		if (metadata==null)
			return false;
		return metadata.indexOf("7FE0,0010")>=0 ||
			(metadata.indexOf("0028,0010")>=0 && metadata.indexOf("0028,0011")>=0) ||
			metadata.indexOf("0008,0060")>=0;
	}

	private static String normalizeTag(String tag) {
		if (tag==null)
			return null;
		tag = tag.trim().toUpperCase(Locale.US);
		if (tag.length()==8 && tag.indexOf(',')==-1)
			tag = tag.substring(0,4) + "," + tag.substring(4);
		return tag;
	}

}
