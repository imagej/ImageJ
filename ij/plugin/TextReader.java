package ij.plugin;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import ij.*;
import ij.io.*;
import ij.process.*;


/** This plugin opens a tab-delimeted text file as an image.
	If 'arg' is empty, it displays a file open dialog and opens
	and displays the file. If 'arg' is a path, it opens the 
	specified file and the calling routine can display it using
	"((ImagePlus)IJ.runPlugIn("ij.plugin.TextReader", path)).show()".
	*/
public class TextReader extends ImagePlus implements PlugIn {

	private static String defaultDirectory;
	int words = 0, chars = 0, lines = 0;
	
	public void run(String arg) {
		OpenDialog od = new OpenDialog("Acquire Text Image...", arg);
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
		catch (Exception e) {
			IJ.showMessage("TextReader", e.getMessage());
			return;
		}

    	setProcessor(name, ip);
    	if (arg.equals(""))
    		show();
	}
	

	public ImageProcessor openFile(String path) throws IOException {
		words = chars = lines = 0;
		Reader r = new BufferedReader(new FileReader(path));
		countLines(r);
		r.close();
		r = new BufferedReader(new FileReader(path));
		int width = words/lines;
		//IJ.write("" + lines + " " + words + " " + " "+width);
		float[] pixels = new float[width*lines];
		ImageProcessor ip = new FloatProcessor(width, lines, pixels, null);
		read(r, width*lines, pixels);
		ip.resetMinAndMax();
		return ip;
	}

	public void countLines(Reader r) throws IOException {
		StreamTokenizer tok = new StreamTokenizer(r);
		int width=1;

		tok.resetSyntax();
		tok.wordChars(33, 255);
		tok.whitespaceChars(0, ' ');
		tok.eolIsSignificant(true);

		while (tok.nextToken() != StreamTokenizer.TT_EOF) {
			switch (tok.ttype) {
				case StreamTokenizer.TT_EOL:
					lines++;
					if (lines==1)
						width = words;
					if (lines%20==0 && width>1 && lines<=width)
						IJ.showProgress(((double)lines/width)/2.0);
					break;
				case StreamTokenizer.TT_WORD:
					words++;
					break;
			}
		}
	}

	public void read(Reader r, int size, float[] pixels) throws IOException {
		StreamTokenizer tok = new StreamTokenizer(r);
		tok.resetSyntax();
		tok.wordChars(33, 255);
		tok.whitespaceChars(0, ' ');
		tok.parseNumbers();

		int i = 0;
		int inc = size/20;
		while (tok.nextToken() != StreamTokenizer.TT_EOF) {
			if (tok.ttype==StreamTokenizer.TT_NUMBER) {
				pixels[i++] = (float)tok.nval;
				if (i%inc==0)
					IJ.showProgress(0.5+((double)i/size)/2.0);
			}
		}
		IJ.showProgress(1.0);
	}

}
