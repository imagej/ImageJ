package  ij.plugin;
import java.awt.*;
import java.io.*;
import java.awt.image.*;
import ij.*;
import ij.io.*;
import ij.process.*;

/** Implements the File/Save As/BMP command. Based on BMPFile class from 
	http://www.javaworld.com/javaworld/javatips/jw-javatip60-p2.html,
	with bug fixes by David Sykes. */
public class BMP_Writer implements PlugIn {
    //--- Private constants
    private final static int BITMAPFILEHEADER_SIZE = 14;
    private final static int BITMAPINFOHEADER_SIZE = 40;
    //--- Private variable declaration
    //--- Bitmap file header
    private byte bitmapFileHeader [] = new byte [14];
    private byte bfType [] =  {(byte)'B', (byte)'M'};
    private int bfSize = 0;
    private int bfReserved1 = 0;
    private int bfReserved2 = 0;
    private int bfOffBits = BITMAPFILEHEADER_SIZE + BITMAPINFOHEADER_SIZE;
    //--- Bitmap info header
    private byte bitmapInfoHeader [] = new byte [40];
    private int biSize = BITMAPINFOHEADER_SIZE;
    private int biWidth = 0;
    private int biHeight = 0;
    private int biPlanes = 1;
    private int biBitCount = 24;
    private int biCompression = 0;
    private int biSizeImage = 0x030000;
    private int biXPelsPerMeter = 0x0;
    private int biYPelsPerMeter = 0x0;
    private int biClrUsed = 0;
    private int biClrImportant = 0;
    //--- Bitmap raw data
    private int intBitmap [];
    private byte byteBitmap [];
    //--- File section
    private FileOutputStream fo;
    private BufferedOutputStream bfo;
    
    private ImagePlus imp;

    public void run(String path) {
    	imp = IJ.getImage();
        IJ.showProgress(0);
        try {
            writeImage(imp, path);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg==null || msg.equals(""))
                msg = ""+e;
            IJ.showMessage("BMP Writer", "An error occured writing the file.\n \n" + msg);
        }
        IJ.showProgress(1);
        IJ.showStatus("");
    }

    void writeImage(ImagePlus imp, String path) throws Exception {
		String prompt = "Save as ";
		if (imp.getType() == ImagePlus.GRAY8) {
			prompt += "8";
			biBitCount = 8;
			biClrUsed=256;
		} else {
			prompt += "24";
			biBitCount = 24;
		}
		prompt += " bit BMP";
		if (path==null || path.equals("")) {
			SaveDialog sd = new SaveDialog(prompt, imp.getTitle(), ".bmp");
			if (sd.getFileName()==null)
				return;
			else
				path = sd.getDirectory()+sd.getFileName();
		}
		imp.startTiming();
		saveBitmap(path, imp.getWidth(), imp.getHeight() );
    }


    public void saveBitmap (String path, int parWidth, int parHeight) throws Exception {
        fo = new FileOutputStream (path);
        bfo = new BufferedOutputStream(fo);
        save (parWidth, parHeight);
        bfo.close();
        fo.close ();
     }

    /*
    *	The saveMethod is the main method of the process. This method
    *	will call the convertImage method to convert the memory image to
    *	a byte array; method writeBitmapFileHeader creates and writes
    *	the bitmap file header; writeBitmapInfoHeader creates the
    *	information header; and writeBitmap writes the image.
    *
    */
    private void save (int parWidth, int parHeight) throws Exception {
        convertImage (parWidth, parHeight);
        writeBitmapFileHeader ();
        writeBitmapInfoHeader ();
        if(biBitCount == 8)
            writeBitmapPalette ();
        writeBitmap ();
    }

    private void writeBitmapPalette() throws Exception {
        LookUpTable lut = imp.createLut();
        if (IJ.debugMode) IJ.log("getMapSize="+lut.getMapSize());
        //int  intPalette[] = new int [lut.getSize];
        //byte bytePalette[] = new byte [lut.getSize*4];
        byte[] g = lut.getGreens();
        byte[] r = lut.getReds();
        byte[] b = lut.getBlues();
        for(int i = 0;i<lut.getMapSize();i++) {
            bfo.write(b[i]);
            bfo.write(g[i]);
            bfo.write(r[i]);
            bfo.write(0x00);
        }
     }

    /*
    * convertImage converts the memory image to the bitmap format (BRG).
    * It also computes some information for the bitmap info header.
    *
    */
    private boolean convertImage (int parWidth, int parHeight) {
        int pad;
         if(biBitCount == 24) {
            intBitmap = new int [parWidth * parHeight];
            intBitmap = (int[]) (imp.getProcessor().convertToRGB()).getPixels();
        } else {
            byteBitmap = new byte [parWidth * parHeight];
            byteBitmap = (byte[]) (imp.getProcessor()).getPixels();
        }

        // ---------------------- biSizeImage Calculation is incorrect in original source file  ------------
        if(biBitCount == 24) {
        //    pad = (4 - ((parWidth * 3) % 4)) * parHeight;         // original calculation
              pad = 4-((parWidth*3) % 4);                           // my corrections follow ..
              if (pad == 4) pad = 0;
              pad = pad * parHeight;
            biSizeImage = ((parWidth * parHeight) * 3) + pad;
        } else {
            pad = (4 - ((parWidth) % 4)) * parHeight;
           if (IJ.debugMode) IJ.log("total pad="+pad);
            biSizeImage = ((parWidth * parHeight)) + pad;
        }

        bfSize = biSizeImage + BITMAPFILEHEADER_SIZE + BITMAPINFOHEADER_SIZE;

        biWidth = parWidth;
        biHeight = parHeight;
        return (true);
    }

    /*
    * writeBitmap converts the image returned from the pixel grabber to
    * the format required. Remember: scan lines are inverted in
    * a bitmap file!
    *
    * Each scan line must be padded to an even 4-byte boundary.
    */
    private void writeBitmap () throws Exception {
         int value;
        int i;
        int pad;
        byte rgb [] = new byte [3];
        if(biBitCount==24)
            pad = 4 - ((biWidth * 3) % 4);
        else 
            pad = 4 - ((biWidth) % 4);
        if (pad == 4)		// <==== Bug correction
            pad = 0;			// <==== Bug correction

       if (IJ.debugMode) IJ.log("pad="+pad);

       for(int row = biHeight; row>0; row--) {
           IJ.showProgress((double)(biHeight-row)/biHeight);
           for( int col = 0; col<biWidth; col++) {
                if(biBitCount==24) {
                    value = intBitmap [(row-1)*biWidth + col ];
                    rgb [0] = (byte) (value & 0xFF);
                    rgb [1] = (byte) ((value >> 8) & 0xFF);
                    rgb [2] = (byte) ((value >>	16) & 0xFF);
                    bfo.write(rgb);
                } else {
                    //IJ.write("pixel index: "+ ( (row-1)*biWidth + col )+" : "+byteBitmap [(row-1)*biWidth + col ]);
                    bfo.write(byteBitmap [(row-1)*biWidth + col ]);
                }
            }
            for (i = 1; i <= pad; i++)
                bfo.write (0x00);
        }
     }


    /*
    * writeBitmapFileHeader writes the bitmap file header to the file.
    *
    */
    private void writeBitmapFileHeader() throws Exception {
        fo.write (bfType);
        fo.write (intToDWord (bfSize));
        fo.write (intToWord (bfReserved1));
        fo.write (intToWord (bfReserved2));
        fo.write (intToDWord (bfOffBits));

        if (IJ.debugMode) {
           IJ.log("bfType="+bfType);
           IJ.log("bfSize="+bfSize);
           IJ.log("bfReserved1="+bfReserved1);
           IJ.log("bfReserved2="+bfReserved2);
           IJ.log("bfOffBits="+bfOffBits);
        }
    }

    /*
    *
    * writeBitmapInfoHeader writes the bitmap information header
    * to the file.
    *
    */
    private void writeBitmapInfoHeader () throws Exception {
        fo.write (intToDWord (biSize));
        fo.write (intToDWord (biWidth));
        fo.write (intToDWord (biHeight));
        fo.write (intToWord (biPlanes));
        fo.write (intToWord (biBitCount));
        fo.write (intToDWord (biCompression));
        fo.write (intToDWord (biSizeImage));
        fo.write (intToDWord (biXPelsPerMeter));
        fo.write (intToDWord (biYPelsPerMeter));
        fo.write (intToDWord (biClrUsed));
        fo.write (intToDWord (biClrImportant));

        if (IJ.debugMode) {
           IJ.log("biSize="+biSize);
           IJ.log("biWidth="+biWidth);
           IJ.log("biHeight="+biHeight);
           IJ.log("biPlanes"+biPlanes);
           IJ.log("biBitCount="+biBitCount);
           IJ.log("biCompression="+biCompression);
           IJ.log("biSizeImage="+biSizeImage);
           IJ.log("biXPelsPerMeter="+biXPelsPerMeter);
           IJ.log("biYPelsPerMeter="+biYPelsPerMeter);
           IJ.log("biClrUsed="+biClrUsed);
           IJ.log("biClrImportant="+biClrImportant);
        }
    }

    /*
    *
    * intToWord converts an int to a word, where the return
    * value is stored in a 2-byte array.
    *
    */
    private byte [] intToWord (int parValue) {
        byte retValue [] = new byte [2];
        retValue [0] = (byte) (parValue & 0x00FF);
        retValue [1] = (byte) ((parValue >>	8) & 0x00FF);
        return (retValue);
    }

    /*
    *
    * intToDWord converts an int to a double word, where the return
    * value is stored in a 4-byte array.
    *
    */
    private byte [] intToDWord (int parValue) {
        byte retValue [] = new byte [4];
        retValue [0] = (byte) (parValue & 0x00FF);
        retValue [1] = (byte) ((parValue >>	8) & 0x000000FF);
        retValue [2] = (byte) ((parValue >>	16) & 0x000000FF);
        retValue [3] = (byte) ((parValue >>	24) & 0x000000FF);
        return (retValue);
    }
}

