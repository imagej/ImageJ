package ij.io;
import java.io.*;
import java.util.*;
import java.net.*;

/** Decodes uncompressed, multi-image TIFF files. */
public class TiffDecoder {

	// tags
	public static final int NEW_SUBFILE_TYPE = 254;
	public static final int IMAGE_WIDTH = 256;
	public static final int IMAGE_LENGTH = 257;
	public static final int BITS_PER_SAMPLE = 258;
	public static final int COMPRESSION = 259;
	public static final int PHOTO_INTERP = 262;
	public static final int IMAGE_DESCRIPTION = 270;
	public static final int STRIP_OFFSETS = 273;
	public static final int SAMPLES_PER_PIXEL = 277;
	public static final int ROWS_PER_STRIP = 278;
	public static final int STRIP_BYTE_COUNT = 279;
	public static final int X_RESOLUTION = 282;
	public static final int Y_RESOLUTION = 283;
	public static final int PLANAR_CONFIGURATION = 284;
	public static final int RESOLUTION_UNIT = 296;
	public static final int SOFTWARE = 305;
	public static final int DATE_TIME = 306;
	public static final int COLOR_MAP = 320;
	public static final int SAMPLE_FORMAT = 339;
	public static final int METAMORPH1 = 33628;
	public static final int IPLAB = 34122;
	public static final int NIH_IMAGE_HDR = 43314;
	
	//constants
	static final int UNSIGNED = 1;
	static final int SIGNED = 2;
	static final int FLOATING_POINT = 3;

	//field types
	static final int SHORT = 3;
	static final int LONG = 4;

	private String directory;
	private String name;
	private String url;
	protected RandomAccessStream in;
	protected boolean debugMode;
	private boolean littleEndian;
	private String dInfo;
	private int ifdCount;
		
	public TiffDecoder(String directory, String name) {
		this.directory = directory;
		this.name = name;
	}

	public TiffDecoder(InputStream in, String name) {
		directory = "";
		this.name = name;
		url = "";
		this.in = new RandomAccessStream(in);
	}

	final int getInt() throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		int b3 = in.read();
		int b4 = in.read();
		if (littleEndian)
			return ((b4 << 24) + (b3 << 16) + (b2 << 8) + (b1 << 0));
		else
			return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
	}

	int getShort() throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		if (littleEndian)
			return ((b2 << 8) + b1);
		else
			return ((b1 << 8) + b2);
	}

	int OpenImageFileHeader() throws IOException {
	// Open 8-byte Image File Header at start of file.
	// Returns the offset in bytes to the first IFD or -1
	// if this is not a valid tiff file.
		int byteOrder = in.readShort();
		if (byteOrder==0x4949) // "II"
			littleEndian = true;
		else if (byteOrder==0x4d4d) // "MM"
			littleEndian = false;
		else {
			in.close();
			return -1;
		}
		int magicNumber = getShort(); // 42
		int offset = getInt();
		return offset;
	}
		
	int getValue(int fieldType, int count) throws IOException {
		int value = 0;
		int unused;
		if (fieldType==SHORT && count==1) {
				value = getShort();
				unused = getShort();
		}
		else
			value = getInt();
		return value;
	}	
	
	void getColorMap(int offset, FileInfo fi) throws IOException {
		byte[] colorTable16 = new byte[768*2];
		int saveLoc = in.getFilePointer();
		in.seek(offset);
		in.readFully(colorTable16);
		in.seek(saveLoc);
		fi.lutSize = 256;
		fi.reds = new byte[256];
		fi.greens = new byte[256];
		fi.blues = new byte[256];
		int j = 0;
		if (littleEndian)
			j++;
		for (int i=0; i<256; i++) {
			fi.reds[i] = colorTable16[j];
			fi.greens[i] = colorTable16[512+j];
			fi.blues[i] = colorTable16[1024+j];
			j += 2;
		}
		fi.fileType = FileInfo.COLOR8;
	}
	
	byte[] getString(int count, int offset) throws IOException {
		count--; // skip null byte at end of string
		if (count==0)
			return null;
		byte[] bytes = new byte[count];
		int saveLoc = in.getFilePointer();
		in.seek(offset);
		in.readFully(bytes);
		in.seek(saveLoc);
		return bytes;
	}

	/** Save the image description in the specified FileInfo. ImageJ
		saves spatial and density calibration data in this string. For
		stacks, it also saves the number of images to avoid having to
		decode an IFD for each image. */
	public void decodeImageDescription(byte[] description, FileInfo fi) {
		if (description.length<7)
			return;
		if (ij.IJ.debugMode)
			ij.IJ.log("Image Description: " + new String(description).replace('\n',' '));
		if (!new String(description,0,6).equals("ImageJ"))
			return;
		fi.description = new String(description);
	}

	void decodeNIHImageHeader(int offset, FileInfo fi) throws IOException {
		int saveLoc = in.getFilePointer();
		
		in.seek(offset+12);
		int version = in.readShort();
		
		in.seek(offset+160);
		double scale = in.readDouble();
		if (version>106 && scale!=0.0) {
			fi.pixelWidth = 1.0/scale;
			fi.pixelHeight = fi.pixelWidth;
		} 

		// spatial calibration
		in.seek(offset+172);
		int units = in.readShort();
		if (version<=153) units += 5;
		switch (units) {
			case 5: fi.unit = "nanometer"; break;
			case 6: fi.unit = "micrometer"; break;
			case 7: fi.unit = "mm"; break;
			case 8: fi.unit = "cm"; break;
			case 9: fi.unit = "meter"; break;
			case 10: fi.unit = "km"; break;
			case 11: fi.unit = "inch"; break;
			case 12: fi.unit = "ft"; break;
			case 13: fi.unit = "mi"; break;
		}

		// density calibration
		in.seek(offset+182);
		int fitType = in.read();
		int unused = in.read();
		int nCoefficients = in.readShort();
		if (fitType==11) {
			fi.calibrationFunction = 21; //Calibration.UNCALIBRATED_OD
			fi.valueUnit = "U. OD";
		} else if (fitType>=0 && fitType<=8 && nCoefficients>=1 && nCoefficients<=5) {
			switch (fitType) {
				case 0: fi.calibrationFunction = 0; break; //Calibration.STRAIGHT_LINE
				case 1: fi.calibrationFunction = 1; break; //Calibration.POLY2
				case 2: fi.calibrationFunction = 2; break; //Calibration.POLY3
				case 3: fi.calibrationFunction = 3; break; //Calibration.POLY4
				case 5: fi.calibrationFunction = 4; break; //Calibration.EXPONENTIAL
				case 6: fi.calibrationFunction = 5; break; //Calibration.POWER
				case 7: fi.calibrationFunction = 6; break; //Calibration.LOG
				case 8: fi.calibrationFunction = 7; break; //Calibration.RODBARD
			}
			fi.coefficients = new double[nCoefficients];
			for (int i=0; i<nCoefficients; i++) {
				fi.coefficients[i] = in.readDouble();
			}
			in.seek(offset+234);
			int size = in.read();
			StringBuffer sb = new StringBuffer();
			if (size>=1 && size<=16) {
				for (int i=0; i<size; i++)
					sb.append((char)(in.read()));
				fi.valueUnit = new String(sb);
			} else
				fi.valueUnit = " ";
		}
			
		in.seek(offset+260);
		int nImages = in.readShort();
		if(nImages>=2 && (fi.fileType==FileInfo.GRAY8||fi.fileType==FileInfo.COLOR8)) {
			fi.nImages = nImages;
			fi.pixelDepth = in.readFloat();	//SliceSpacing
			int skip = in.readShort();		//CurrentSlice
			fi.frameInterval = in.readFloat();
			//ij.IJ.write("fi.pixelDepth: "+fi.pixelDepth);
		}
			
		in.seek(offset+272);
		float aspectRatio = in.readFloat();
		if (version>140 && aspectRatio!=0.0)
			fi.pixelHeight = fi.pixelWidth/aspectRatio;
		
		in.seek(saveLoc);
	}
	
	void dumpTag(int tag, int count, int value, FileInfo fi) {
		String name;
		switch (tag) {
			case NEW_SUBFILE_TYPE: name="NewSubfileType"; break;
			case IMAGE_WIDTH: name="ImageWidth"; break;
			case IMAGE_LENGTH: name="ImageLength"; break;
			case STRIP_OFFSETS: name="StripOffsets"; break;
			case PHOTO_INTERP: name="PhotoInterp"; break;
			case IMAGE_DESCRIPTION: name="ImageDescription"; break;
			case BITS_PER_SAMPLE: name="BitsPerSample"; break;
			case SAMPLES_PER_PIXEL: name="SamplesPerPixel"; break;
			case ROWS_PER_STRIP: name="RowsPerStrip"; break;
			case STRIP_BYTE_COUNT: name="StripByteCount"; break;
			case X_RESOLUTION: name="XResolution"; break;
			case Y_RESOLUTION: name="YResolution"; break;
			case RESOLUTION_UNIT: name="ResolutionUnit"; break;
			case SOFTWARE: name="Software"; break;
			case DATE_TIME: name="DateTime"; break;
			case PLANAR_CONFIGURATION: name="PlanarConfiguration"; break;
			case COMPRESSION: name="Compression"; break; 
			case COLOR_MAP: name="ColorMap"; break; 
			case SAMPLE_FORMAT: name="SampleFormat"; break; 
			case NIH_IMAGE_HDR: name="NIHImageHeader"; break; 
			default: name="???"; break;
		}
		String cs = (count==1)?"":", count=" + count;
		dInfo += "    " + tag + ", \"" + name + "\", value=" + value + cs + "\n";
		//ij.IJ.write(tag + ", \"" + name + "\", value=" + value + cs);
	}

	double getRational(int loc) throws IOException {
		int saveLoc = in.getFilePointer();
		in.seek(loc);
		int numerator = getInt();
		int denominator = getInt();
		in.seek(saveLoc);
		//System.out.println("numerator: "+numerator);
		//System.out.println("denominator: "+denominator);
		if (denominator!=0)
			return (double)numerator/denominator;
		else
			return 0.0;
	}
	
	FileInfo OpenIFD() throws IOException {
	// Get Image File Directory data
	
		int tag, fieldType, count, value;
		int nEntries = getShort();
		if (nEntries<1)
			return null;
		ifdCount++;
		FileInfo fi = new FileInfo();
		for (int i=0; i<nEntries; i++) {
			tag = getShort();
			fieldType = getShort();
			count = getInt();
			value = getValue(fieldType, count);
			if (debugMode) dumpTag(tag, count, value, fi);
			//ij.IJ.write(i+"/"+nEntries+" "+tag + ", count=" + count + ", value=" + value);
			//if (tag==0) return null;
			switch (tag) {
				case IMAGE_WIDTH: 
					fi.width = value;
					break;
				case IMAGE_LENGTH: 
					fi.height = value;
					break;
				case STRIP_OFFSETS: 
					if (count==1)
						fi.offset = value;
					else {
						int saveLoc = in.getFilePointer();
						in.seek(value);
						fi.offset = getInt(); // Assumes contiguous strips
						in.seek(saveLoc);
					}
					break;
				case PHOTO_INTERP:
					fi.whiteIsZero = value==0;
					break;
				case BITS_PER_SAMPLE:
						if (count==1) {
							if (value==8)
								fi.fileType = FileInfo.GRAY8;
							else if (value==16) {
								fi.fileType = FileInfo.GRAY16_UNSIGNED;
								fi.intelByteOrder = littleEndian;
							}
							else if (value==32) {
								fi.fileType = FileInfo.GRAY32_INT;
								fi.intelByteOrder = littleEndian;
							} else if (value==1)
								fi.fileType = FileInfo.BITMAP;
							else
								throw new IOException("Unsupported BitsPerSample: " + value);
						} else if (count==3) {
							int saveLoc = in.getFilePointer();
							in.seek(value);
							if (getShort()!=8)
								throw new IOException("ImageJ can only open 8-bit/channel RGB images");
							in.seek(saveLoc);
						}
						break;
				case SAMPLES_PER_PIXEL:
					if (value==3)
						fi.fileType = FileInfo.RGB;
					else if (value!=1)
						throw new IOException("Unsupported SamplesPerPixel: " + value);
					break;
				case X_RESOLUTION:
					double xScale = getRational(value); 
					if (xScale!=0.0) fi.pixelWidth = 1.0/xScale; 
					break;
				case Y_RESOLUTION:
					double yScale = getRational(value); 
					if (yScale!=0.0) fi.pixelHeight = 1.0/yScale; 
					break;
				case RESOLUTION_UNIT:
					if (value==1&&fi.unit==null)
						fi.unit = " ";
					else if (value==2)
						fi.unit = "inch";
					else if (value==3)
						fi.unit = "cm";
					break;
				case PLANAR_CONFIGURATION:
					if (value==2 && fi.fileType==FileInfo.RGB)
						fi.fileType = FileInfo.RGB_PLANAR;
					break;
				case COMPRESSION:
					if (value!=1 && value!=7) // don't abort with Spot camera compressed (7) thumbnails
						throw new IOException("ImageJ cannot open compressed TIFF files ("+value+")");
					break;
				case COLOR_MAP: 
					if (count==768 && fi.fileType==fi.GRAY8)
						getColorMap(value, fi);
					break;
				case SAMPLE_FORMAT:
					if (fi.fileType==FileInfo.GRAY32_INT && value==FLOATING_POINT)
						fi.fileType = FileInfo.GRAY32_FLOAT;
					if (fi.fileType==FileInfo.GRAY16_UNSIGNED && value==SIGNED)
						fi.fileType = FileInfo.GRAY16_SIGNED;
					break;
				case IMAGE_DESCRIPTION: 
					if (ifdCount==1) {
						byte[] s = getString(count,value);
						if (s!=null) decodeImageDescription(s,fi);
					}
					break;
				case METAMORPH1: 
					if (name.indexOf(".STK")>0 || name.indexOf(".stk")>0)
						fi.nImages=9999;
					break;
				case IPLAB: 
					fi.nImages=value;
					break;
				case NIH_IMAGE_HDR: 
					if (count==256)
						decodeNIHImageHeader(value, fi);
					break;
				default:
			}
		}
		fi.fileFormat = fi.TIFF;
		fi.fileName = name;
		fi.directory = directory;
		if (url!=null)
			fi.url = url;
		if (debugMode) dInfo += "    offset=" + fi.offset + "\n";
		return fi;
	}


	public void enableDebugging() {
		debugMode = true;
	}
	
	
	public FileInfo[] getTiffInfo() throws IOException {
		int ifdOffset;
		Vector info;
				
		if (in==null)
			in = new RandomAccessStream(new RandomAccessFile(directory + name, "r"));
		info = new Vector();
		ifdOffset = OpenImageFileHeader();
		if (ifdOffset<0) {
			in.close();
			return null;
		}
		if (debugMode) dInfo = "\n  " + name + ": opening\n";
		while (ifdOffset>0) {
			in.seek(ifdOffset);
			FileInfo fi = OpenIFD();
			//ij.IJ.write(""+fi);
			if (fi!=null)
				info.addElement(fi);
			ifdOffset = getInt();
			if (debugMode) dInfo += "  nextIFD=" + ifdOffset + "\n";
			if (fi!=null && fi.nImages>1) // ignore extra IFDs in ImageJ and NIH Image stacks
				ifdOffset = 0;
		}
		if (info.size()==0) {
			in.close();
			return null;
		} else {
			FileInfo[] fi = new FileInfo[info.size()];
			info.copyInto((Object[])fi);
			if (debugMode) fi[0].info = dInfo;
			if (url!=null) {
				in.seek(0);
				fi[0].inputStream = in;
			} else
				in.close();
			return fi;
		}
	}

}