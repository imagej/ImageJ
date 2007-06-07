package ij.io;
import java.io.*;

/**Saves an image described by a FileInfo object as an uncompressed, big-endian TIFF file.*/
public class TiffEncoder {
	static final int IMAGE_START = 768;
	static final int HDR_SIZE = 8;
	static final int MAP_SIZE = 768; // in 16-bit words
	static final int BPS_DATA_SIZE = 6;
	static final int SCALE_DATA_SIZE = 16;
	
	
	private FileInfo fi;
	private int bitsPerSample;
	private int photoInterp;
	private int samplesPerPixel;
	private int nEntries;
	private int ifdSize;
	private int imageOffset = IMAGE_START;
	private int imageSize;
	private int stackSize;
	private byte[] description;
	
	public TiffEncoder (FileInfo fi) {
		this.fi = fi;
		fi.intelByteOrder = false;
		bitsPerSample = 8;
		samplesPerPixel = 1;
		nEntries = 9;
		int bytesPerPixel = 1;
		switch (fi.fileType) {
			case FileInfo.GRAY8:
				photoInterp = fi.whiteIsZero?0:1;
				break;
			case FileInfo.GRAY16_UNSIGNED:
			case FileInfo.GRAY16_SIGNED:
				bitsPerSample = 16;
				photoInterp = fi.whiteIsZero?0:1;
				bytesPerPixel = 2;
				break;
			case FileInfo.GRAY32_FLOAT:
				bitsPerSample = 32;
				photoInterp = fi.whiteIsZero?0:1;
				bytesPerPixel = 4;
				break;
			case FileInfo.RGB:
				photoInterp = 2;
				samplesPerPixel = 3;
				bytesPerPixel = 3;
				break;
			case FileInfo.COLOR8:
				photoInterp = 3;
				nEntries = 10;
				break;
			default:
				photoInterp = 0;
		}
		if (fi.unit!=null && fi.pixelWidth!=0 && fi.pixelHeight!=0)
			nEntries += 3; // XResolution, YResolution and ResolutionUnit
		if (fi.fileType==fi.GRAY32_FLOAT)
			nEntries++; // SampleFormat tag
		makeDescriptionString();
		if (description!=null)
			nEntries++;  // ImageDescription tag
		ifdSize = 2 + nEntries*12 + 4;
		imageSize = fi.width*fi.height*bytesPerPixel;
		stackSize = imageSize*fi.nImages;
	}
	
	/** Saves the image as a TIFF file. The DataOutputStream is not closed.
		The fi.pixels field must contain the image data. If fi.nImages>1
		then fi.pixels must be a 2D array. The fi.offset field is ignored. */
	public void write(DataOutputStream out) throws IOException {
		writeHeader(out);
		int nextIFD = 0;
		if (fi.nImages>1) {
			nextIFD = IMAGE_START+stackSize;
			if (fi.fileType==FileInfo.COLOR8) nextIFD += MAP_SIZE*2;
		}
		writeIFD(out, imageOffset, nextIFD);
		int bpsSize=0, scaleSize=0, descriptionSize=0;
		if (fi.fileType==FileInfo.RGB)
			bpsSize = writeBitsPerPixel(out);
		if (description!=null)
			descriptionSize = writeDescription(out);
		if (fi.unit!=null && fi.pixelWidth!=0 && fi.pixelHeight!=0)
			scaleSize = writeScale(out);
		byte[] filler = new byte[IMAGE_START - (HDR_SIZE+ifdSize+bpsSize+descriptionSize+scaleSize)];
		out.write(filler); // force image to start at offset 768
		//ij.IJ.write("filler: "+filler.length);
		new ImageWriter(fi).write(out);
		if (fi.fileType==FileInfo.COLOR8)
			writeColorMap(out);
		for (int i=2; i<=fi.nImages; i++) {
			if (i==fi.nImages)
				nextIFD = 0;
			else
				nextIFD += ifdSize;
			imageOffset += imageSize;
			writeIFD(out, imageOffset, nextIFD);
		}
	}
	
	/** Writes the 8-byte image file header. */
	void writeHeader(DataOutputStream out) throws IOException {
		byte[] hdr = new byte[8];
		hdr[0] = 77; // "MM" (Motorola byte order)
		hdr[1] = 77;
		hdr[2] = 0;  // 42 (magic number)
		hdr[3] = 42;
		hdr[4] = 0;  // 8 (offset to first IFD)
		hdr[5] = 0;
		hdr[6] = 0;
		hdr[7] = 8;
		out.write(hdr);
	}
	
	/** Writes one 12-byte IFD entry. */
	void writeEntry(DataOutputStream out, int tag, int fieldType, int count, int value) throws IOException {
		out.writeShort(tag);
		out.writeShort(fieldType);
		out.writeInt(count);
		if (count==1 && fieldType==TiffDecoder.SHORT)
			value <<= 16; //left justify 16-bit values
		out.writeInt(value); // may be an offset
	}
	
	/** Writes one IFD (Image File Directory). */
	void writeIFD(DataOutputStream out, int imageOffset, int nextIFD) throws IOException {	
		int tagDataOffset = HDR_SIZE + ifdSize;
		out.writeShort(nEntries);
		writeEntry(out, TiffDecoder.NEW_SUBFILE_TYPE, 4, 1, 0);
		writeEntry(out, TiffDecoder.IMAGE_WIDTH,      3, 1, fi.width);
		writeEntry(out, TiffDecoder.IMAGE_LENGTH,     3, 1, fi.height);
		if (fi.fileType==FileInfo.RGB) {
			writeEntry(out, TiffDecoder.BITS_PER_SAMPLE,  3, 3, tagDataOffset);
			tagDataOffset += BPS_DATA_SIZE;
		} else
			writeEntry(out, TiffDecoder.BITS_PER_SAMPLE,  3, 1, bitsPerSample);
		writeEntry(out, TiffDecoder.PHOTO_INTERP,     3, 1, photoInterp);
		if (description!=null) {
			writeEntry(out, TiffDecoder.IMAGE_DESCRIPTION, 2, description.length, tagDataOffset);
			tagDataOffset += description.length;
		}
		writeEntry(out, TiffDecoder.STRIP_OFFSETS,    4, 1, imageOffset);
		writeEntry(out, TiffDecoder.SAMPLES_PER_PIXEL,3, 1, samplesPerPixel);
		writeEntry(out, TiffDecoder.ROWS_PER_STRIP,   3, 1, fi.height);
		writeEntry(out, TiffDecoder.STRIP_BYTE_COUNT, 4, 1, imageSize);
		if (fi.unit!=null && fi.pixelWidth!=0 && fi.pixelHeight!=0) {
			writeEntry(out, TiffDecoder.X_RESOLUTION, 5, 1, tagDataOffset);
			writeEntry(out, TiffDecoder.Y_RESOLUTION, 5, 1, tagDataOffset+8);
			tagDataOffset += SCALE_DATA_SIZE;
			int unit = 1;
			if (fi.unit.equals("inch"))
				unit = 2;
			else if (fi.unit.equals("cm"))
				unit = 3;
			writeEntry(out, TiffDecoder.RESOLUTION_UNIT, 3, 1, unit);
		}
		if (fi.fileType==fi.GRAY32_FLOAT) {
			int format = TiffDecoder.FLOATING_POINT;
			writeEntry(out, TiffDecoder.SAMPLE_FORMAT, 3, 1, format);
		}
		if (fi.fileType==FileInfo.COLOR8)
			writeEntry(out, TiffDecoder.COLOR_MAP, 3, MAP_SIZE, IMAGE_START+stackSize);
		out.writeInt(nextIFD);
	}
	
	/** Writes the 6 bytes of data required by RGB BitsPerSample tag. */
	int writeBitsPerPixel(DataOutputStream out) throws IOException {
		out.writeShort(8);
		out.writeShort(8);
		out.writeShort(8);
		return BPS_DATA_SIZE;
	}

	/** Writes the 16 bytes of data required by the XResolution and YResolution tags. */
	int writeScale(DataOutputStream out) throws IOException {
		double xscale = 1.0/fi.pixelWidth;
		double yscale = 1.0/fi.pixelHeight;
		double scale = 1000000.0;
		if (xscale>1000.0) scale = 1000.0;
		out.writeInt((int)(xscale*scale));
		out.writeInt((int)scale);
		out.writeInt((int)(yscale*scale));
		out.writeInt((int)scale);
		return SCALE_DATA_SIZE;
	}

	/** Writes the variable length ImageDescription string. */
	int writeDescription(DataOutputStream out) throws IOException {
		out.write(description,0,description.length);
		return description.length;
	}

	/** Writes color palette following the image. */
	void writeColorMap(DataOutputStream out) throws IOException {
		byte[] colorTable16 = new byte[MAP_SIZE*2];
		int j=0;
		for (int i=0; i<fi.lutSize; i++) {
			colorTable16[j] = fi.reds[i];
			colorTable16[512+j] = fi.greens[i];
			colorTable16[1024+j] = fi.blues[i];
			j += 2;
		}
		out.write(colorTable16);
	}
	
	/** Creates an optional image description string for saving calibration data.
		For stacks, also saves the stack size so ImageJ can open the stack without
		decoding an IFD for each slice.*/
	void makeDescriptionString() {
		StringBuffer sb = new StringBuffer(100);
		sb.append("ImageJ="+ij.ImageJ.VERSION+"\n");
		if (fi.nImages>1)
			sb.append("images="+fi.nImages+"\n");
		if (fi.unit!=null)
			sb.append("unit="+fi.unit+"\n");
		if (fi.valueUnit!=null) {
			sb.append("cf="+fi.calibrationFunction+"\n");
			if (fi.coefficients!=null) {
				for (int i=0; i<fi.coefficients.length; i++)
					sb.append("c"+i+"="+fi.coefficients[i]+"\n");
			}
			sb.append("vunit="+fi.valueUnit+"\n");
		}
		sb.append("");
		description = new String(sb).getBytes();
		description[description.length-1] = 0; 
	}

}
