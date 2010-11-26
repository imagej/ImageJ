package ij.plugin;
import java.io.*;
import java.util.Properties; 
import ij.*;
import ij.io.*;
import ij.process.*;

/**
 * This plugin saves a 16 or 32 bit image in FITS format. It is a stripped-down version of the SaveAs_FITS 
 *	plugin from the collection of astronomical image processing plugins by Jennifer West at
 *	http://www.umanitoba.ca/faculties/science/astronomy/jwest/plugins.html.
 *
 * <br>Version 2010-11-23 : corrects 16-bit writing, adds BZERO & BSCALE updates (K.A. Collins, Univ. Louisville).
 * <br>Version 2008-09-07 : preserves non-minimal FITS header if already present (F.V. Hessman, Univ. Goettingen).
 * <br>Version 2008-12-15 : fixed END card recognition bug (F.V. Hessman, Univ. Goettingen).
 */
public class FITS_Writer implements PlugIn {

	public void run(String path) {
		ImagePlus imp = IJ.getImage();
		ImageProcessor ip = imp.getProcessor();
		int numImages = imp.getImageStackSize();
		int bitDepth = imp.getBitDepth();
		if (bitDepth==24) {
			IJ.error("RGB images are not supported");
			return;
		}

		// GET PATH
		if (path == null || path.trim().length() == 0) {
			String title = "image.fits";
			SaveDialog sd = new SaveDialog("Write FITS image",title,".fits");
			path = sd.getDirectory()+sd.getFileName();
		}

		// GET FILE
		File f = new File(path);
		String directory = f.getParent()+File.separator;
		String name = f.getName();
		if (f.exists()) f.delete();
		int numBytes = 0;

		// GET IMAGE
		if (bitDepth==8)
			ip = ip.convertToShort(false);
		else if (imp.getCalibration().isSigned16Bit())
			ip = ip.convertToFloat();
		if (ip instanceof ShortProcessor)
			numBytes = 2;
		else if (ip instanceof FloatProcessor)
			numBytes = 4;
		int fillerLength = 2880 - ( (numBytes * imp.getWidth() * imp.getHeight()) % 2880 );

		// WRITE FITS HEADER
		String[] hdr = getHeader(imp);
		if (hdr == null)
			createHeader(path, ip, numBytes);
		else
			copyHeader(hdr, path, ip, numBytes);

		// WRITE DATA
		writeData(path, ip);
		char[] endFiller = new char[fillerLength];
		appendFile(endFiller, path);
	}

	/**
	 * Creates a FITS header for an image which doesn't have one already.
	 */	
	void createHeader(String path, ImageProcessor ip, int numBytes) {
		int numCards = 7;
		String bitperpix = "";
		if      (numBytes==2) {bitperpix = "                  16";}
		else if (numBytes==4) {bitperpix = "                 -32";}
		else if (numBytes==1) {bitperpix = "                   8";}
 		appendFile(writeCard("SIMPLE", "                   T", "Created by ImageJ FITS_Writer"), path);
 		appendFile(writeCard("BITPIX", bitperpix, "number of bits per data pixel"), path);
 		appendFile(writeCard("NAXIS", "                   2", "number of data axes"), path);
 		appendFile(writeCard("NAXIS1", "                "+ip.getWidth(), "length of data axis 1"), path);
 		appendFile(writeCard("NAXIS2", "                "+ip.getHeight(), "length of data axis 2"), path);
        if (numBytes==2)
            appendFile(writeCard("BZERO", "               32768", "data range offset"), path);
        else
            appendFile(writeCard("BZERO", "                   0", "data range offset"), path);
        appendFile(writeCard("BSCALE", "                   1", "default scaling factor"), path);

 		int fillerSize = 2880 - ((numCards*80+3) % 2880);
		char[] end = new char[3];
		end[0] = 'E'; end[1] = 'N'; end[2] = 'D';
		char[] filler = new char[fillerSize];
		for (int i = 0; i < fillerSize; i++)
			filler[i] = ' ';
 		appendFile(end, path);
 		appendFile(filler, path);
	}

	/**
	 * Writes one line of a FITS header
	 */ 
	char[] writeCard(String title, String value, String comment) {
		char[] card = new char[80];
		for (int i = 0; i < 80; i++)
			card[i] = ' ';
		s2ch(title, card, 0);
		card[8] = '=';
		s2ch(value, card, 10);
		card[31] = '/';
		card[32] = ' ';
		s2ch(comment, card, 33);
		return card;
	}
			
	/**
	 * Converts a String to a char[]
	 */
	void s2ch (String str, char[] ch, int offset) {
		int j = 0;
		for (int i = offset; i < 80 && i < str.length()+offset; i++)
			ch[i] = str.charAt(j++);
	}
	
	/**
	 * Appends 'line' to the end of the file specified by 'path'.
	 */
	void appendFile(char[] line, String path) {
		try {
			FileWriter output = new FileWriter(path, true);
			output.write(line);
			output.close();
		}
		catch (IOException e) {
			IJ.showStatus("Error writing file!");
			return;
		}
	}
			
	/**
	 * Appends the data of the current image to the end of the file specified by path.
	 */
	void writeData(String path, ImageProcessor ip) {
		int w = ip.getWidth();
		int h = ip.getHeight();
		if (ip instanceof ShortProcessor) {
			short[] pixels = (short[])ip.getPixels();
			try {   
				DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path,true)));
				for (int i = h - 1; i >= 0; i-- )
                    for (int j = i*w; j < w*(i+1); j++)
                        dos.writeShort(pixels[j]^0x8000);
				dos.close();
			}
			catch (IOException e) {
				IJ.showStatus("Error writing file!");
				return;
			}
		} else if (ip instanceof FloatProcessor) {
			float[] pixels = (float[])ip.getPixels();
			try {   
				DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path,true)));
				for (int i = h - 1; i >= 0; i-- )
                    for (int j = i*w; j < w*(i+1); j++)
					dos.writeFloat(pixels[j]);
				dos.close();
			}
			catch (IOException e) {
				IJ.showStatus("Error writing file!");
				return;
			}					   
		}
	}

	/**
	 * Extracts the original FITS header from the Properties object of the
	 * ImagePlus image (or from the current slice label in the case of an ImageStack)
	 * and returns it as an array of String objects representing each card.
	 *
	 * Taken from the ImageJ astroj package (www.astro.physik.uni-goettingen.de/~hessman/ImageJ/Astronomy)
	 *
	 * @param img		The ImagePlus image which has the FITS header in it's "Info" property.
	 */
	public static String[] getHeader (ImagePlus img) {
		String content = null;

		int depth = img.getStackSize();
		if (depth == 1) {
			Properties props = img.getProperties();
			if (props == null)
				return null;
			content = (String)props.getProperty ("Info");
		}
		else if (depth > 1) {
			int slice = img.getCurrentSlice();
			ImageStack stack = img.getStack();
			content = stack.getSliceLabel(slice);
		}
		if (content == null)
			return null;

		// PARSE INTO LINES

		String[] lines = content.split("\n");

		// FIND "SIMPLE" AND "END" KEYWORDS

		int istart = 0;
		for (; istart < lines.length; istart++) {
			if (lines[istart].startsWith("SIMPLE") ) break;
		}
		if (istart == lines.length) return null;

		int iend = istart+1;
		for (; iend < lines.length; iend++) {
			String s = lines[iend].trim();
			if ( s.equals ("END") || s.startsWith ("END ") ) break;
		}
		if (iend >= lines.length) return null;

		int l = iend-istart+1;
		String header = "";
		for (int i=0; i < l; i++)
			header += lines[istart+i]+"\n";
		return header.split("\n");
	}

	/**
	 * Converts a string into an 80-char array.
	 */
	char[] eighty(String s) {
		char[] c = new char[80];
		int l=s.length();
		for (int i=0; i < l && i < 80; i++)
			c[i]=s.charAt(i);
		if (l < 80) {
			for (; l < 80; l++) c[l]=' ';
		}
		return c;
	}

	/**
	 * Copies the image header contained in the image's Info property.
	 */
	void copyHeader(String[] hdr, String path, ImageProcessor ip, int numBytes) {
		int numCards = 7;
		String bitperpix = "";

		// THIS STUFF NEEDS TO BE MADE CONFORMAL WITH THE PRESENT IMAGE
		if      (numBytes==2) {bitperpix = "                  16";}
		else if (numBytes==4) {bitperpix = "                 -32";}
		else if (numBytes==1) {bitperpix = "                   8";}
 		appendFile(writeCard("SIMPLE", "                   T", "Created by ImageJ FITS_Writer"), path);
 		appendFile(writeCard("BITPIX", bitperpix, "number of bits per data pixel"), path);
 		appendFile(writeCard("NAXIS", "                   2", "number of data axes"), path);
		appendFile(writeCard("NAXIS1", "                "+ip.getWidth(), "length of data axis 1"), path);
 		appendFile(writeCard("NAXIS2", "                "+ip.getHeight(), "length of data axis 2"), path);
        if (numBytes==2)
            appendFile(writeCard("BZERO", "               32768", "data range offset"), path);
        else
            appendFile(writeCard("BZERO", "                   0", "data range offset"), path);
        appendFile(writeCard("BSCALE", "                   1", "default scaling factor"), path);


		// APPEND THE REST OF THE HEADER
		char[] card;
		for (int i=0; i < hdr.length; i++) {
			String s = hdr[i];
			card = eighty(s);
			if (!s.startsWith("SIMPLE") &&
			    !s.startsWith("BITPIX") &&
			    !s.startsWith("NAXIS")  &&
                !s.startsWith("BZERO") &&
                !s.startsWith("BSCALE") &&
			    !s.startsWith("END ")   &&
			     s.trim().length() > 1) {
				appendFile(card, path);
				numCards++;
			}
		}
 
		// FINISH OFF THE HEADER
		int fillerSize = 2880 - ((numCards*80+3) % 2880);
		char[] end = new char[3];
		end[0] = 'E'; end[1] = 'N'; end[2] = 'D';
		char[] filler = new char[fillerSize];
		for (int i = 0; i < fillerSize; i++)
			filler[i] = ' ';
		appendFile(end, path);
		appendFile(filler, path);
	}

}

