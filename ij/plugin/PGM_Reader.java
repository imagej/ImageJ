package ij.plugin;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import ij.*;
import ij.io.*;
import ij.process.*;

/**
     This plugin opens PGM (portable graymap) format images.

     The portable graymap format is a lowest  common  denominator
     grayscale file format.  The definition is as follows:

     - A "magic number" for identifying the  file  type.   A  pgm
       file's magic number is the two characters "P2".
     - Whitespace (blanks, TABs, CRs, LFs).
     - A width, formatted as ASCII characters in decimal.
     - Whitespace.
     - A height, again in ASCII decimal.
     - Whitespace.
     - The maximum gray value, again in ASCII decimal.
     - Whitespace.
     - Width * height gray values, each in ASCII decimal, between
       0  and  the  specified  maximum  value,  separated by whi-
       tespace, starting at the top-left corner of  the  graymap,
       proceeding  in normal English reading order.  A value of 0
       means black, and the maximum value means white.
     - Characters from a "#" to the next end-of-line are  ignored
       (comments).
     - No line should be longer than 70 characters.

     Here is an example of a small graymap in this format:
     P2
     # feep.pgm
     24 7
     15
     0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0
     0  3  3  3  3  0  0  7  7  7  7  0  0 11 11 11 11  0  0 15 15 15 15  0
     0  3  0  0  0  0  0  7  0  0  0  0  0 11  0  0  0  0  0 15  0  0 15  0
     0  3  3  3  0  0  0  7  7  7  0  0  0 11 11 11  0  0  0 15 15 15 15  0
     0  3  0  0  0  0  0  7  0  0  0  0  0 11  0  0  0  0  0 15  0  0  0  0
     0  3  0  0  0  0  0  7  7  7  7  0  0 11 11 11 11  0  0 15  0  0  0  0
     0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0  0

     There is a  PGM variant that stores the pixel data as raw bytes:

     -The "magic number" is "P5" instead of "P2".
     -The gray values are stored as plain bytes, instead of ASCII decimal.
     -No whitespace is allowed in the grays section, and only a single
     character of whitespace (typically a newline) is allowed after the maxval.
     -The files are smaller and many times faster to read and write.

*/

public class PGM_Reader extends ImagePlus implements PlugIn {

	private int width, height;
	private boolean rawBits;
	private boolean sixteenBits;
	
	public void run(String arg) {
		OpenDialog od = new OpenDialog("PGM Reader...", arg);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		String path = directory + name;
		
		IJ.showStatus("Opening: " + path);
		ImageProcessor ip;
		try {
			ip = openFile(path);
		}
		catch (IOException e) {
			String msg = e.getMessage();
			IJ.showMessage("PGM Reader", msg.equals("")?""+e:msg);
			return;
		}

		setProcessor(name, ip);
		if (arg.equals(""))
			show();
	}

	public ImageProcessor openFile(String path) throws IOException {
		InputStream is = new BufferedInputStream(new FileInputStream(path));
		//This was a failed attempt to avoid the "deprecated" message
		//Reader r = new InputStreamReader(is);
		//StreamTokenizer tok = new StreamTokenizer(r);
		StreamTokenizer tok = new StreamTokenizer(is);
		tok.resetSyntax();
		tok.wordChars(33, 255);
		tok.whitespaceChars(0, ' ');
		tok.parseNumbers();
		tok.eolIsSignificant(true);
		tok.commentChar('#');
		openHeader(tok);
		if (sixteenBits)
			return open16bitRawImage(is, width, height);
		else {
			byte[] pixels = new byte[width*height];
			ImageProcessor ip = new ByteProcessor(width, height, pixels, null);
			if (rawBits)
				openRawImage(is, width*height, pixels);
			else
				openAsciiImage(tok, width*height, pixels);
			return ip;
		}
	}

	public void openHeader(StreamTokenizer tok) throws IOException {
		String magicNumber = getWord(tok);
		if (magicNumber.equals("P5"))
			rawBits = true;
		else if (!magicNumber.equals("P2"))
			throw new IOException("PGM files must start with \"P2\" or \"P5\"");
		width = getInt(tok);
		height = getInt(tok);
		int maxValue = getInt(tok);
		if (width==-1 || height==-1 || maxValue==-1)
			throw new IOException("Error opening PGM header..");
		sixteenBits = rawBits && maxValue>255;
		String msg = "The maximum gray value is larger than ";
		if (sixteenBits && maxValue>65535)
			throw new IOException(msg + "65535.");
		if (!sixteenBits && maxValue>255)
			throw new IOException(msg + "255.");
	}

	public void openAsciiImage(StreamTokenizer tok, int size, byte[] pixels) throws IOException {
		int i = 0;
		int inc = size/20;
		while (tok.nextToken() != tok.TT_EOF) {
			if (tok.ttype==tok.TT_NUMBER) {
				pixels[i++] = (byte)(((int)tok.nval)&255);
				if (i%inc==0)
					IJ.showProgress(0.5+((double)i/size)/2.0);
			}
		}
		IJ.showProgress(1.0);
	}

	public void openRawImage(InputStream is, int size, byte[] pixels) throws IOException {
		int count = 0;
		while (count<size && count>=0)
			count = is.read(pixels, count, size-count);
	}

	public ImageProcessor open16bitRawImage(InputStream is, int width, int height) throws IOException {
		int size = width*height*2;
		byte[] bytes = new byte[size];
		int count = 0;
		while (count<size && count>=0)
			count = is.read(bytes, count, size-count);
		short[] pixels = new short[size/2];
		for (int i=0,j=0; i<size/2; i++,j+=2)
			pixels[i] = (short)(((bytes[j]&0xff)<<8) | (bytes[j+1]&0xff)); //big endian
		return new ShortProcessor(width, height, pixels, null);
   	}
	
	String getWord(StreamTokenizer tok) throws IOException {
		while (tok.nextToken() != tok.TT_EOF) {
			if (tok.ttype==tok.TT_WORD)
				return tok.sval;
		}
		return null;
	}

	int getInt(StreamTokenizer tok) throws IOException {
		while (tok.nextToken() != tok.TT_EOF) {
			if (tok.ttype==tok.TT_NUMBER)
				return (int)tok.nval;
		}
		return -1;
	}

}


