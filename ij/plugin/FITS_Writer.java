package ij.plugin;
import ij.*;
import ij.io.*;
import ij.process.*;
import java.io.*;

/** This plugin saves a 16 or 32 bit image in FITS format. It is a stripped-down version of the SaveAs_FITS 
	plugin from the collection of astronomical image processing plugins by Jennifer West at
	http://www.umanitoba.ca/faculties/science/astronomy/jwest/plugins.html
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
		File f = new File(path);
		String directory = f.getParent()+File.separator;
		String name = f.getName();
		if (f.exists()) f.delete();
		int numBytes = 0;
		if (bitDepth==8)
			ip = ip.convertToShort(false);
		else if (imp.getCalibration().isSigned16Bit())
			ip = ip.convertToFloat();
		if (ip instanceof ShortProcessor)
			numBytes = 2;
		else if (ip instanceof FloatProcessor)
			numBytes = 4;
		int fillerLength = 2880 - ( (numBytes * imp.getWidth() * imp.getHeight()) % 2880 );
		createHeader(path, ip, numBytes);
		writeData(path, ip);
		char[] endFiller = new char[fillerLength];
		appendFile(endFiller, path);
	}
	
	void createHeader(String path, ImageProcessor ip, int numBytes) {
		int numCards = 5;
		String bitperpix = "";
		if (numBytes==2) {bitperpix = "                  16";}
		else if (numBytes==4) {bitperpix = "                 -32";}
		else if (numBytes==1) {bitperpix = "                   8";}
 		appendFile(writeCard("SIMPLE", "                   T", ""), path);
 		appendFile(writeCard("BITPIX", bitperpix, ""), path);
 		appendFile(writeCard("NAXIS", "                   2", ""), path);
 		appendFile(writeCard("NAXIS1", "                 "+ip.getWidth(), "image width"), path);
 		appendFile(writeCard("NAXIS2", "                 "+ip.getHeight(), "image height"), path);
 		int fillerSize = 2880 - ((numCards*80+3) % 2880);
		char[] end = new char[3];
		end[0] = 'E'; end[1] = 'N'; end[2] = 'D';
		char[] filler = new char[fillerSize];
		for (int i = 0; i < fillerSize; i++)
			filler[i] = ' ';
 		appendFile(end, path);
 		appendFile(filler, path);
	}

	/** Writes one line of a FITS header */	
	char[] writeCard(String title, String value, String comment) {
		char[] card = new char[80];
		for (int i = 0; i < 80; i++)
			card[i] = ' ';
		s2ch(title, card, 0);
		card[8] = '=';
		s2ch(value, card, 10);
		card[31] = '/';
		s2ch(comment, card, 32);
		return card;
	}
			
	/** Converts a String to a char[] */
	void s2ch (String str, char[] ch, int offset) {
		int j = 0;
		for (int i = offset; i < str.length()+offset; i++)
			ch[i] = str.charAt(j++);
	}
	
	/** Appends 'line' to the end of the file specified by 'path'. */
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
			
	/** Appends the data of the current image to the end of the file specified by path. */
	void writeData(String path, ImageProcessor ip) {
		int w = ip.getWidth();
		int h = ip.getHeight();
		ip.flipVertical();
		if (ip instanceof ShortProcessor) {
			short[] pixels = (short[])ip.getPixels();
			try {	
				DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path,true)));
				for (int i = 0; i < (pixels.length); i++)
					dos.writeShort(pixels[i]);
				dos.close();
			}
			catch (IOException e) {
				IJ.write("Error writing file!");
				return;
			}
		} else if (ip instanceof FloatProcessor) {
			float[] pixels = (float[])ip.getPixels();
			try {	
				DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path,true)));
				for (int i = 0; i < (pixels.length); i++)		
					dos.writeFloat(pixels[i]);
				dos.close();
			}
			catch (IOException e) {
				IJ.write("Error writing file!");
				return;
			}						
		}
		ip.flipVertical();
	}
	
}
