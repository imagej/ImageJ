package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.Animator;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

/** <pre>
 * ImageJ Plugin for reading an AVI file into an image stack
 *	(one slice per video frame)
 *
 *
 * Restrictions and Notes:
 *		- Only few formats supported:
 *			- uncompressed 8 bit with palette (=LUT)
 *			- uncompressed 8 & 16 bit grayscale
 *			- uncompressed 24 & 32 bit RGB (alpha channel ignored)
 *			- uncompressed 32 bit AYUV (alpha channel ignored)
 *			- various YUV 4:2:2 and 4:2:0 compressed formats (i.e., formats with
 *						  full luminance resolution, but reduced chroma resolution
 *			- png or jpeg-encoded individual frames.
 *			  Note that most MJPG (motion-JPEG) formats are not read correctly.
 *		- Does not read avi formats with more than one frame per chunk
 *		- Palette changes during the video not supported
 *		- Out-of-sequence frames (sequence given by index) not supported
 *		- Different frame sizes in one file (rcFrame) not supported
 *		- Conversion of (A)YUV formats to grayscale is non-standard:
 *		  All 255 levels are kept as in the input (i.e. the full dynamic
 *		  range of data from a frame grabber is preserved).
 *		  For standard behavior, use "Brightness&Contrast", Press "Set",
 *		  enter "Min." 16, "Max." 235, and press "Apply".
 *		- Restrictions for AVIs with blank frames:
 *		  Currently only supported with AVI-2 type index.
 *		  Blank frames are ignored.
 *		  Selection of start and end frames is inconsistent between normal and
 *		  virtual stacks.
 *		  Timing in slice info is incorrect unless read as virtual stack.
 *		- Note: As a last frame, one can enter '0' (= last frame),
 *		  '-1' (last frame -1), etc.
 *
 * Version History:
 *	 2008-04-29
 *		  based on a plugin by Daniel Marsh and Wayne Rasband;
 *		  modifications by Michael Schmid
 *		- Support for several other formats added, especially some YUV
 *		  (also named YCbCr) formats
 *		- Uneven chunk sizes fixed
 *		- Negative biHeight fixed
 *		- Audio or second video stream don't cause a problem
 *		- Can read part of a file (specify start & end frame numbers)
 *		- Can convert YUV and RGB to grayscale (does not convert 8-bit with palette)
 *		- Can flip vertically
 *		- Can create a virtual stack
 *		- Added slice label: time of the frame in the movie
 *		- Added a public method 'getStack' that does not create an image window
 *		- More compact code, especially for reading the header (rewritten)
 *		- In the code, bitmapinfo items have their canonical names.
 *	 2008-06-08
 *		- Support for png and jpeg/mjpg encoded files added
 *		- Retrieves animation speed from image frame rate
 *		- Exception handling without multiple error messages
 *	 2008-07-03
 *		- Support for 16bit AVIs coded by MIL (Matrox Imaging Library)
 *	 2009-03-06
 *		- Jesper Soendergaard Pedersen added support for extended (large) AVI files,
 *		  also known as 'AVI 2.0' or 'OpenDML 1.02 AVI file format extension'
 *		  For Virtual stacks, it reads the 'AVI 2.0' index (indx and ix00 tags).
 *		  This results in a dramatic speed increase in loading of virtual stacks.
 *		  If indx and ix00 are not found or bIndexType is unsupported, as well as for
 *		  non-virtual stacks it finds the frames 'the old way', by scanning the whole file.
 *		- Fixes a bug where it read too many frames.
 *		  This version was published as external plugin.
 *	 2011-12-03
 *		- Minor updates & cleanup for integration into ImageJ again.
 *		- Multithread-compliant.
 *	 2011-12-10
 *		- Based on a plugin by Jesper Soendergaard Pedersen, also reads the 'idx1' index of
 *		  AVI 1 files, speeding up initial reading of virtual stacks also for smaller files.
 *		- When the first frame to read is > 1, uses the index to quickly skip the initial frames.
 *		- Creates a unique window name.
 *		- Opens MJPG files also if they do not contain Huffman tables
 *	 2012-02-01
 *		- added support for YV12, I420, NV12, NV21 (planar formats with 2x2 U and V subsampling)
 *	 2012-12-04
 *		- can read AVI-2 files with blank frames into a virtual stack
 *	 2013-10-29
 *		- can read MJPG files where the frames don't have the same pixel number as the overall video
 *	 2015-09-28
 *		- reads most ImageJ AVI1 files with size>4 GB (incorrectly written by ImageJ versions before 1.50b)
 *	 2017-04-21
 *		- bugfix: file was not closed in case of dialog cancelled or some IO errors.
 *      - Tries to recover data from truncated files.
 *
 *
 * The AVI format looks like this:
 * RIFF AVI					RIFF HEADER, AVI CHUNK
 *	 | LIST hdrl			MAIN AVI HEADER
 *	 | | avih				AVI HEADER
 *	 | | LIST strl			STREAM LIST(s) (One per stream)
 *	 | | | strh				STREAM HEADER (Required after above; fourcc type is 'vids' for video stream)
 *	 | | | strf				STREAM FORMAT (for video: BitMapInfo; may also contain palette)
 *	 | | | strd				OPTIONAL -- STREAM DATA (ignored in this plugin)
 *	 | | | strn				OPTIONAL -- STREAM NAME (ignored in this plugin)
 *	 | | | indx				OPTIONAL -- MAIN 'AVI 2.0' INDEX
 *	 | LIST movi			MOVIE DATA
 *	 | | ix00				partial video index of 'AVI 2.0', usually missing in AVI 1 (ix01 would be for audio)
 *	 | | [rec]				RECORD DATA (one record per frame for interleaved video; optional, unsupported in this plugin)
 *	 | | |-dataSubchunks	RAW DATA: '??wb' for audio, '??db' and '??dc' for uncompressed and
 *	 | |					compressed video, respectively. "??" denotes stream number, usually "00" or "01"
 *	 | idx1					AVI 1 INDEX ('old-style'); may be missing in very old formats
 * RIFF AVIX				'AVI 2.0' only: further chunks
 *	 | LIST movi			more movie data, as above, usually with ix00 index
 *							Any number of further chunks (RIFF tags) may follow
 *
 * Items ('chunks') with one fourcc (four-character code such as 'strh') start with two 4-byte words:
 * the fourcc and the size of the data area.
 * Items with two fourcc (e.g. 'LIST hdrl') have three 4-byte words: the first fourcc, the size and the
 * second fourcc. Note that the size includes the 4 bytes needed for the second fourcc.
 *
 * Chunks with fourcc 'JUNK' can appear anywhere and should be ignored.
 *
 * </pre>
 */

public class AVI_Reader extends VirtualStack implements PlugIn {

	//four-character codes for avi chunk types
	//NOTE: byte sequence is reversed - ints in Intel (little endian) byte order!
	private final static int   FOURCC_RIFF = 0x46464952;   //'RIFF'
	private final static int   FOURCC_AVI =	 0x20495641;   //'AVI '
	private final static int   FOURCC_AVIX = 0x58495641;   //'AVIX'	 // extended AVI
	private final static int   FOURCC_00ix = 0x78693030;   //'00ix'	 // index within
	private final static int   FOURCC_indx = 0x78646e69;   //'indx'	 // main index
	private final static int   FOURCC_idx1 = 0x31786469;   //'idx1'	 // index of single 'movi' block
	private final static int   FOURCC_LIST = 0x5453494c;   //'LIST'
	private final static int   FOURCC_hdrl = 0x6c726468;   //'hdrl'
	private final static int   FOURCC_avih = 0x68697661;   //'avih'
	private final static int   FOURCC_strl = 0x6c727473;   //'strl'
	private final static int   FOURCC_strh = 0x68727473;   //'strh'
	private final static int   FOURCC_strf = 0x66727473;   //'strf'
	private final static int   FOURCC_movi = 0x69766f6d;   //'movi'
	private final static int   FOURCC_rec =	 0x20636572;   //'rec '
	private final static int   FOURCC_JUNK = 0x4b4e554a;   //'JUNK'
	private final static int   FOURCC_vids = 0x73646976;   //'vids'
	private final static int   FOURCC_00db = 0x62643030;   //'00db'
	private final static int   FOURCC_00dc = 0x63643030;   //'00dc'

	//four-character codes for supported compression formats; see fourcc.org
	private final static int   NO_COMPRESSION	 = 0;		   //uncompressed, also 'RGB ', 'RAW '
	private final static int   NO_COMPRESSION_RGB= 0x20424752; //'RGB ' -a name for uncompressed
	private final static int   NO_COMPRESSION_RAW= 0x20574152; //'RAW ' -a name for uncompressed
	private final static int   NO_COMPRESSION_Y800=0x30303859; //'Y800' -a name for 8-bit grayscale
	private final static int   NO_COMPRESSION_Y8 = 0x20203859; //'Y8  ' -another name for Y800
	private final static int   NO_COMPRESSION_GREY=0x59455247; //'GREY' -another name for Y800
	private final static int   NO_COMPRESSION_Y16= 0x20363159; //'Y16 ' -a name for 16-bit uncompressed grayscale
	private final static int   NO_COMPRESSION_MIL= 0x204c494d; //'MIL ' - Matrox Imaging Library
	private final static int   AYUV_COMPRESSION	 = 0x56555941; //'AYUV' -uncompressed, but alpha, Y, U, V bytes
	private final static int   UYVY_COMPRESSION	 = 0x59565955; //'UYVY' - 4:2:2 with byte order u y0 v y1
	private final static int   Y422_COMPRESSION	 = 0x564E5955; //'Y422' -another name for of UYVY
	private final static int   UYNV_COMPRESSION	 = 0x32323459; //'UYNV' -another name for of UYVY
	private final static int   CYUV_COMPRESSION	 = 0x76757963; //'cyuv' -as UYVY but not top-down
	private final static int   V422_COMPRESSION	 = 0x32323456; //'V422' -as UYVY but not top-down
	private final static int   YUY2_COMPRESSION	 = 0x32595559; //'YUY2' - 4:2:2 with byte order y0 u y1 v
	private final static int   YUNV_COMPRESSION	 = 0x564E5559; //'YUNV' -another name for YUY2
	private final static int   YUYV_COMPRESSION	 = 0x56595559; //'YUYV' -another name for YUY2
	private final static int   YVYU_COMPRESSION	 = 0x55595659; //'YVYU' - 4:2:2 with byte order y0 u y1 v

	private final static int   I420_COMPRESSION	 = 0x30323449; //'I420' - y plane followed by 2x2 subsampled U and V
	private final static int   IYUV_COMPRESSION	 = 0x56555949; //'IYUV' - another name for I420
	private final static int   YV12_COMPRESSION	 = 0x32315659; //'YV12' - y plane followed by 2x2 subsampled V and U
	private final static int   NV12_COMPRESSION	 = 0x3231564E; //'NV12' - y plane followed by 2x2 subsampled interleaved U, V
	private final static int   NV21_COMPRESSION	 = 0x3132564E; //'NV21' - y plane followed by 2x2 subsampled interleaved V, U

	private final static int   JPEG_COMPRESSION	 = 0x6765706a; //'jpeg' JPEG compression of individual frames
	private final static int   JPEG_COMPRESSION2 = 0x4745504a; //'JPEG' JPEG compression of individual frames
	private final static int   JPEG_COMPRESSION3 = 0x04;	   //BI_JPEG: JPEG compression of individual frames
	private final static int   MJPG_COMPRESSION	 = 0x47504a4d; //'MJPG' Motion JPEG, also reads compression of individual frames
	private final static int   PNG_COMPRESSION	 = 0x20676e70; //'png ' PNG compression of individual frames
	private final static int   PNG_COMPRESSION2	 = 0x20474e50; //'PNG ' PNG compression of individual frames
	private final static int   PNG_COMPRESSION3	 = 0x05;	   //BI_PNG PNG compression of individual frames

	private final static int   BITMASK24 = 0x10000;			   //for 24-bit (in contrast to 8, 16,... not a bitmask)
	private final static long  SIZE_MASK = 0xffffffffL;		   //for conversion of sizes from unsigned int to long
	private final static long  FOUR_GB	 = 0x100000000L;	   //2^32; above this size of data AVI 1 has a problem for sure

	// flags from AVI chunk header
	private final static int   AVIF_HASINDEX	 = 0x00000010;	// Index at end of file?
	private final static int   AVIF_MUSTUSEINDEX = 0x00000020;	// ignored by this plugin
	private final static int   AVIF_ISINTERLEAVED= 0x00000100;	// ignored by this plugin

	// constants used to read 'AVI 2' index chunks (others than those defined here are not supported)
	private final static byte  AVI_INDEX_OF_CHUNKS=0x01;	   //index of frames
	private final static byte  AVI_INDEX_OF_INDEXES=0x00;	   //main indx pointing to ix00 etc subindices

	//static versions of dialog parameters that will be remembered
	private static boolean	   staticConvertToGray;
	private static boolean	   staticFlipVertical;
	private static boolean	   staticIsVirtual = true;
	//dialog parameters
	private int				   firstFrame = 1;		//the first frame to read
	private int				   lastFrame = 0;		//the last frame to read; 0 means 'read all'
	private boolean			   convertToGray;		//whether to convert color video to grayscale
	private boolean			   flipVertical;		//whether to flip image vertical
	private boolean			   isVirtual;			//whether to open as virtual stack
   //the input file
	private	 RandomAccessFile  raFile;
	private	 String			   raFilePath;
	private	 boolean		   headerOK = false;	//whether header has been read
	//more avi file properties etc
	private	 int			   streamNumber;		//number of the (first) video stream
	private	 int			   type0xdb, type0xdc;	//video stream chunks must have one of these two types (e.g. '00db' for straem 0)
	private	 long			   fileSize;			//file size
	private	 long			   aviSize;				//size of 'AVI' chunk
	private	 long			   headerPositionEnd;	//'movi' will start somewhere here
	private	 long			   indexPosition;		//position of the main index (indx)
	private	 long			   indexPositionEnd;	//indx seek end
	private	 long			   moviPosition;		//position of 'movi' list
	private	 int			   paddingGranularity = 2;	//tags start at even address
	private	 int			   frameNumber = 1;		//frame currently read; global because distributed over 1st AVi and further RIFF AVIX chunks
	private	 int			   lastFrameToRead = Integer.MAX_VALUE;
	private	 int			   totalFramesFromIndex;//number of frames from 'AVI 2.0' indices
	private	 boolean		   indexForCountingOnly;//don't read the index, only count int totalFramesFromIndex how many entries
	private	 boolean		   isOversizedAvi1;		//AVI-1 file > 4GB
	//derived from BitMapInfo
	private	 int			   dataCompression;		//data compression type used
	private	 boolean		   isPlanarFormat;		//I420 & YV12 formats: y frame, then u,v frames
	private	 int			   scanLineSize;
	private	 boolean		   dataTopDown;			//whether data start at top of image
	private	 ColorModel		   cm;
	private	 boolean		   variableLength;		//compressed (PNG, JPEG) frames have variable length
	//for conversion to ImageJ stack
	private	 Vector<long[]>	   frameInfos;			//for virtual stack: long[] with frame pos&size in file, time(usec)
	private	 ImageStack		   stack;
	private	 ImagePlus		   imp;
	//for debug messages and error handling
	private	 boolean		   verbose = IJ.debugMode;
	private	 long			   startTime;
	private	 boolean		   aborting;
	private	 boolean		   displayDialog = true;
    private  String 		   errorText;			//error occurred during makeStack, or null

	//From AVI Header Chunk
	private	 int			   dwMicroSecPerFrame;
	private	 int			   dwMaxBytesPerSec;
	private	 int			   dwReserved1;
	private	 int			   dwFlags;
	private	 int			   dwTotalFrames;		//AVI 2.0: will be replaced by number of frames from index
	private	 int			   dwInitialFrames;
	private	 int			   dwStreams;
	private	 int			   dwSuggestedBufferSize;
	private	 int			   dwWidth;
	private	 int			   dwHeight;

	//From Stream Header Chunk
	private	 int			   fccStreamHandler;
	private	 int			   dwStreamFlags;
	private	 int			   dwPriorityLanguage;	//actually 2 16-bit words: wPriority and wLanguage
	private	 int			   dwStreamInitialFrames;
	private	 int			   dwStreamScale;
	private	 int			   dwStreamRate;
	private	 int			   dwStreamStart;
	private	 int			   dwStreamLength;
	private	 int			   dwStreamSuggestedBufferSize;
	private	 int			   dwStreamQuality;
	private	 int			   dwStreamSampleSize;

	//From Stream Format Chunk: BITMAPINFO contents (40 bytes)
	private	 int			   biSize;				// size of this header in bytes (40)
	private	 int			   biWidth;
	private	 int			   biHeight;
	private	 short			   biPlanes;			// no. of color planes: for the formats decoded; here always 1
	private	 short			   biBitCount;			// Bits per Pixel
	private	 int			   biCompression;
	private	 int			   biSizeImage;			// size of image in bytes (may be 0: if so, calculate)
	private	 int			   biXPelsPerMeter;		// horizontal resolution, pixels/meter (may be 0)
	private	 int			   biYPelsPerMeter;		// vertical resolution, pixels/meter (may be 0)
	private	 int			   biClrUsed;			// no. of colors in palette (if 0, calculate)
	private	 int			   biClrImportant;		// no. of important colors (appear first in palette) (0 means all are important)



	/** The plugin is invoked by ImageJ using this method.
	 *	@param arg	 String 'arg' may be used to select the path. If it is an empty string,
	 *	a file open dialog is shown, and the resulting ImagePlus is displayed thereafter.
	 *	The ImagePlus is not displayed only if 'arg' is a non-empty String; it can be
	 *	retrieved with getImagePlus().
	 */
	public void run (String arg) {
		String options = IJ.isMacro()?Macro.getOptions():null;
		if (options!=null && options.contains("select=") && !options.contains("open="))
			Macro.setOptions(options.replaceAll("select=", "open="));
		OpenDialog	od = new OpenDialog("Open AVI File", arg);
		String fileName = od.getFileName();
		if (fileName == null) return;
		String fileDir = od.getDirectory();
		String path = fileDir + fileName;
		try {
			openAndReadHeader(path);								//open and read header
		} catch (Exception e) {
			error(exceptionMessage(e));
			return;
		} finally {
			closeFile(raFile);
		}
		if (displayDialog && !showDialog(fileName))					//ask for parameters
			return;
		errorText = null;
		ImageStack stack = makeStack(path, firstFrame, lastFrame, isVirtual, convertToGray, flipVertical);	//read data
		if (aborting)
			return;													//error message has been shown already
		if (stack==null || stack.getSize() == 0 || stack.getProcessor(1)==null) {	//read nothing?
            if (errorText != null)
				error(errorText);
            else {
				 String rangeText = "";
				if (firstFrame > 1 || (lastFrame != 0 && lastFrame != dwTotalFrames))
					rangeText = "\nin Range "+firstFrame+
                            (lastFrame>0 ? " - "+lastFrame : " - end");
                error("Error: No Frames Found"+rangeText);
            }
			return;
		} else if (errorText != null)
            IJ.showMessage("AVI Reader", errorText);				//show the error, e.g. we may have an incomplete stack
		imp = new ImagePlus(WindowManager.makeUniqueName(fileName), stack);
		if (imp.getBitDepth()==16)
			imp.getProcessor().resetMinAndMax();
		setFramesPerSecond(imp);
		FileInfo fi = new FileInfo();
		fi.fileName = fileName;
		fi.directory = fileDir;
		imp.setFileInfo(fi);
		if (arg.equals(""))
			imp.show();
		IJ.showTime(imp, startTime, "Read AVI in ", stack.getSize());
	}

	/** Returns the ImagePlus opened by run(). */
	public ImagePlus getImagePlus() {
		return imp;
	}

	/** Opens an AVI file as a virtual stack. The ImagePlus is not displayed. */
	public static ImagePlus openVirtual(String path) {
		return open(path, true);
	}

	/** Opens an AVI file as a stack in memory or a virtual stack. The ImagePlus is not displayed. */
	public static ImagePlus open(String path, boolean virtual) {
		AVI_Reader reader = new AVI_Reader();
		ImageStack stack = reader.makeStack (path, 1, 0, virtual, false, false);
		if (stack!=null)
			return new ImagePlus((new File(path)).getName(), stack);
		else
			return null;
	}

	/** Create an ImageStack from an avi file with given path.
	 * @param path				Directoy+filename of the avi file
	 * @param firstFrame  Number of first frame to read (first frame of the file is 1)
	 * @param lastFrame	  Number of last frame to read or 0 for reading all, -1 for all but last...
	 * @param isVirtual			Whether to return a virtual stack
	 * @param convertToGray		Whether to convert color images to grayscale
	 * @return	Returns the stack (may be incomplete on error); null on failure.
	 *	The stack returned may be non-null, but have a length of zero if no suitable frames were found.
	 *  Use <code>getErrorText</code> to determine whether there has been an error reading the file.
	 *  For virtual stacks, not that I/O errors may also occur later, when reading the frames.
	 */
	public ImageStack makeStack (String path, int firstFrame, int lastFrame,
			boolean isVirtual, boolean convertToGray, boolean flipVertical) {
		this.firstFrame = firstFrame;
		this.lastFrame = lastFrame;
		this.isVirtual = isVirtual;
		this.convertToGray = convertToGray;
		this.flipVertical = flipVertical;
		IJ.showProgress(.001);
		try {
			readAVI(path);
		} catch (OutOfMemoryError e) {
			stack.trim();
			errorText = "Out of memory.  " + stack.getSize() + " of " + dwTotalFrames + " frames will be opened.";
		} catch (Exception e) {
			errorText = exceptionMessage(e);
            if (isVirtual || stack==null || stack.getSize()==0)		//return null only if we have really nothing
				return null;
		} finally {
			closeFile(raFile);
			if (verbose)
                IJ.log("File closed.");
			IJ.showProgress(1.0);
		}
		if (isVirtual && frameInfos != null)
			stack = this;
		if (stack!=null && cm!=null)
			stack.setColorModel(cm);
		return stack;
	}

    /** Returns a description of the error reading the file with <code>makeStack</code> or null if no error */
	public String getErrorText() {
		return errorText;
	}

	/** Returns an ImageProcessor for the specified slice of this virtual stack (if it is one)
     *  where 1<=n<=nslices. Returns null if no virtual stack or no slices or error reading the frame.
	 */
	public synchronized ImageProcessor getProcessor(int n) {
		if (frameInfos==null || frameInfos.size()==0 || raFilePath==null)
			return null;
		if (n<1 || n>frameInfos.size())
			throw new IllegalArgumentException("Argument out of range: "+n);
		Object pixels = null;
		RandomAccessFile rFile = null;
		try {
			rFile = new RandomAccessFile(new File(raFilePath), "r");
			long[] frameInfo = (long[])(frameInfos.get(n-1));
			pixels = readFrame(rFile, frameInfo[0], (int)frameInfo[1]);
		} catch (Exception e) {
			error(exceptionMessage(e));
			return null;
		} finally {
			closeFile(rFile);
		}
		if (pixels == null) return null; //failed
		if (pixels instanceof byte[])
			return new ByteProcessor(dwWidth, biHeight, (byte[])pixels, cm);
		else if (pixels instanceof short[])
			return new ShortProcessor(dwWidth, biHeight, (short[])pixels, cm);
		else
			return new ColorProcessor(dwWidth, biHeight, (int[])pixels);
	}

	/** Returns the image width of the virtual stack */
	public int getWidth() {
		return dwWidth;
	}

	/** Returns the image height of the virtual stack */
	public int getHeight() {
		return biHeight;
	}

	/** Returns the number of images in this virtual stack (if it is one) */
	public int getSize() {
		if (frameInfos == null) return 0;
		else return frameInfos.size();
	}

	/** Returns the label of the specified slice in this virtual stack (if it is one). */
	public String getSliceLabel(int n) {
		if (frameInfos==null || n<1 || n>frameInfos.size())
			throw new IllegalArgumentException("No Virtual Stack or argument out of range: "+n);
		return frameLabel(((long[])(frameInfos.get(n-1)))[2]);
	}

	/** Deletes the specified image from this virtual stack (if it is one),
	 *	where 1<=n<=nslices. */
	public void deleteSlice(int n) {
		if (frameInfos==null || frameInfos.size()==0) return;
		if (n<1 || n>frameInfos.size())
			throw new IllegalArgumentException("Argument out of range: "+n);
		frameInfos.removeElementAt(n-1);
	}

	/** Parameters dialog, returns false on cancel */
	private boolean showDialog (String fileName) {
		if (lastFrame!=-1)
			lastFrame = dwTotalFrames;
		if (!IJ.isMacro()) {
			convertToGray = staticConvertToGray;
			flipVertical = staticFlipVertical;
			isVirtual = staticIsVirtual;
		}
		GenericDialog gd = new GenericDialog("AVI Reader");
		gd.addNumericField("First Frame: ", firstFrame, 0);
		gd.addNumericField("Last Frame: ", lastFrame, 0, 6, "");
		gd.addCheckbox("Use Virtual Stack", isVirtual);
		gd.addCheckbox("Convert to Grayscale", convertToGray);
		gd.addCheckbox("Flip Vertical", flipVertical);
		gd.setSmartRecording(true);
		gd.showDialog();
		if (gd.wasCanceled()) return false;
		firstFrame = (int)gd.getNextNumber();
		lastFrame = (int)gd.getNextNumber();
		isVirtual = gd.getNextBoolean();
		convertToGray = gd.getNextBoolean();
		flipVertical = gd.getNextBoolean();
		if (!IJ.isMacro()) {
			staticConvertToGray = convertToGray;
			staticFlipVertical = flipVertical;
			staticIsVirtual = isVirtual;
		}
		IJ.register(this.getClass());
		return true;
	}

	/** Read into a (virtual) stack */
	private void readAVI(String path) throws Exception, IOException {
		if (!headerOK)							// we have not read the header yet?
			openAndReadHeader(path);
        else {
			File file = new File(path);			// open if currently not open
			raFile = new RandomAccessFile(file, "r");
        }
		startTime += System.currentTimeMillis();// taking previously elapsed time into account
		/** MOVED UP HERE BY JSP*/
		if (lastFrame > 0)						// last frame number to read: from Dialog
			lastFrameToRead = lastFrame;
		if (lastFrame < 0 && dwTotalFrames > 0) // negative means "end frame minus ..."
			lastFrameToRead = dwTotalFrames+lastFrame;
		if (lastFrameToRead < firstFrame)		// no frames to read
			return;
		boolean hasIndex = (dwFlags & AVIF_HASINDEX) != 0;
		if (isVirtual || firstFrame>1) {		// avoid scanning frame-by-frame where we only need the positions
			frameInfos = new Vector<long[]>(100); // holds frame positions, sizes and time since start
			long nextPosition = -1;
			if (indexPosition > 0) {			// attempt to get AVI2.0 index instead of scanning for all frames
				raFile.seek(indexPosition);
				nextPosition = findFourccAndRead(FOURCC_indx, false, indexPositionEnd, false);
			}
			if (hasIndex && (frameInfos==null ||frameInfos.size()==0)) { // got nothing from indx, attempt to read AVI 1 index 'idx1'
				raFile.seek(headerPositionEnd);
				moviPosition = findFourccAndSkip(FOURCC_movi, true, fileSize);	// go behind the 'movi' list
				if (moviPosition<0)
					throw new Exception("AVI File has no movie data");
				long positionBehindMovie = raFile.getFilePointer();
				while (positionBehindMovie < fileSize-8) {
					if (verbose)
						IJ.log("searching for 'idx1' at 0x"+Long.toHexString(positionBehindMovie));
					raFile.seek(positionBehindMovie);
					if (positionBehindMovie > FOUR_GB)
						isOversizedAvi1 = true;
					nextPosition = findFourccAndRead(FOURCC_idx1, false, fileSize, false);
					if (nextPosition >= 0)		//AVI-1 index 'idx1' found
						break;
					positionBehindMovie += FOUR_GB;	 //maybe position was wrong because it was a 32-bit number, but > 4GB?
				}
			}
			if (verbose)
				IJ.log("'frameInfos' has "+frameInfos.size()+" entries");
		}
		if (isVirtual && frameInfos.size()>0)	// Virtual Stack only needs reading the index
			return;
		// Read AVI (movie data) frame by frame - if no index tag is present the pointers
		// for the virtual AVI stack will be read here
		raFile.seek(headerPositionEnd);
		if (firstFrame>1 && frameInfos.size()>0) {
			long[] frameInfo = (long[])frameInfos.get(0);
			raFile.seek(frameInfo[0]-8);		// chunk starts 8 bytes before frame data
			frameNumber = firstFrame;
			if (verbose)
				IJ.log("directly go to frame "+firstFrame+" @ 0x"+Long.toHexString(frameInfo[0]-8));
			readMovieData(fileSize);
		} else {
			frameNumber = 1;
			findFourccAndRead(FOURCC_movi, true, fileSize, true);
		}

		long pos = raFile.getFilePointer();
		//IJ.log("at 0x"+Long.toHexString(pos)+" filesize=0x"+Long.toHexString(fileSize));
		// extended AVI: try to find further 'RIFF' chunks, where we expect AVIX tags
		while (pos>0 && pos<fileSize && (frameNumber<lastFrameToRead+1))
				pos = findFourccAndRead(FOURCC_RIFF, false, fileSize, false);
		return;
	 }

	/** Open the file with given path and read its header */
	private void openAndReadHeader (String path) throws Exception, IOException {
		startTime = System.currentTimeMillis();
		if (verbose)
			IJ.log("OPEN AND READ AVI FILE HEADER "+timeString());
		File file = new File(path);							// o p e n
		raFile = new RandomAccessFile(file, "r");
		raFilePath = path;
		fileSize = raFile.length();
		int fileType = readInt();							// f i l e	 h e a d e r
		if (verbose)
			IJ.log("File header: File type='"+fourccString(fileType)+"' (should be 'RIFF')"+timeString());
		if (fileType != FOURCC_RIFF)
			throw new Exception("Not an AVI file.");
		aviSize = readInt() & SIZE_MASK;					//size of AVI chunk
		int riffType = readInt();
		if (verbose)
			IJ.log("File header: RIFF type='"+fourccString(riffType)+"' (should be 'AVI ')");
		if (riffType != FOURCC_AVI)
			throw new Exception("Not an AVI file.");

		findFourccAndRead(FOURCC_hdrl, true, fileSize, true);
		startTime -= System.currentTimeMillis(); //becomes minus elapsed Time
		headerOK = true;
	}

	/** Read AVIX chunks following the first RIFF AVI for large files (sequential reading frame-by-frame beyond the first chunk) **/
	private void readAVIX(long endPosition) throws Exception, IOException {
		if (verbose)
			IJ.log("Trying to read AVIX"+timeString());
		int riffType = readInt();
		if (verbose)
			IJ.log("File header: RIFF type='"+fourccString(riffType)+"' (should be 'AVIX')");
		if (riffType != FOURCC_AVIX)
			throw new Exception("Not an AVI file.");
		findFourccAndRead(FOURCC_movi, true, fileSize, true); //read movie data
	}

	/** Find the next position of fourcc or LIST fourcc and read contents.
	 *	Returns next position
	 *	If not found, throws exception or returns -1 */
	private long findFourccAndRead(int fourcc, boolean isList, long endPosition,
	boolean throwNotFoundException) throws Exception, IOException {
		long nextPos;
		boolean contentOk = false;
		do {
			int type = readType(endPosition);
			if (type == 0) {			//reached endPosition without finding
				if (throwNotFoundException)
					throw new Exception("Required item '"+fourccString(fourcc)+"' not found");
				else
					return -1;
			}
			long size = readInt() & SIZE_MASK;
			nextPos = raFile.getFilePointer() + size;

			if (nextPos>endPosition || nextPos>fileSize) {
				errorText = "AVI File Error: '"+fourccString(type)+"' @ 0x"+Long.toHexString(raFile.getFilePointer()-8)+" has invalid length. File damaged/truncated?";
				IJ.log(errorText);		// this text is also remembered as error message for showing in message box
				if (fourcc == FOURCC_movi)
                    nextPos = fileSize;	// if movie data truncated, try reading what we can get
				else
                    return -1;			// otherwise, nothing to be done
			}
			if (isList && type == FOURCC_LIST)
				type = readInt();
			if (verbose)
				IJ.log("Search for '"+fourccString(fourcc)+"', found "+fourccString(type)+"' data "+posSizeString(nextPos-size, size));
			if (type==fourcc) {
				contentOk = readContents(fourcc, nextPos);
			} else if (verbose)
				IJ.log("'"+fourccString(type)+"', ignored");
			raFile.seek(nextPos);
			if (contentOk)
				return nextPos;			//found and read, breaks the loop
		} while (!contentOk);
		return nextPos;
	}

	/** Find the next position of fourcc or LIST fourcc, but does not read it, only
	 *	returns the first position inside the fourcc chunk and puts the file pointer
	 *	behind the fourcc chunk (if successful).
	 *	If not found, returns -1 */
	private long findFourccAndSkip(int fourcc, boolean isList, long endPosition) throws IOException {
		while (true) {
			int type = readType(endPosition);
			if (type == 0)				//reached endPosition without finding
				return -1;
			long size = readInt() & SIZE_MASK;
			long chunkPos = raFile.getFilePointer();
			long nextPos = chunkPos + size;	 //note that 'size' of a list includes the 'type' that follows now
			if (isList && type == FOURCC_LIST)
				type = readInt();
			if (verbose)
				IJ.log("Searching for (to skip) '"+fourccString(fourcc)+"', found "+fourccString(type)+
						"' data "+posSizeString(chunkPos, size));
			raFile.seek(nextPos);
			if (type == fourcc)
				return chunkPos;		//found and skipped, breaks the loop
		}
	}

	/** read contents defined by four-cc code; returns true if contens ok */
	private boolean readContents (int fourcc, long endPosition) throws Exception, IOException {
		switch (fourcc) {
			case FOURCC_hdrl:
				headerPositionEnd = endPosition;
				findFourccAndRead(FOURCC_avih, false, endPosition, true);
				findFourccAndRead(FOURCC_strl, true, endPosition, true);
				return true;
			case FOURCC_avih:
				readAviHeader();
				return true;
			case FOURCC_strl:
				long nextPosition = findFourccAndRead(FOURCC_strh, false, endPosition, false);
				if (nextPosition<0) return false;
				indexPosition = findFourccAndRead(FOURCC_strf, false, endPosition, true);
				indexPositionEnd= endPosition;
				indexForCountingOnly = true;			//try reading indx for counting number of entries
				totalFramesFromIndex = 0;
				nextPosition = findFourccAndRead(FOURCC_indx, false, endPosition, false);
				if (nextPosition > 0 && totalFramesFromIndex > dwTotalFrames)
					dwTotalFrames = totalFramesFromIndex;
				indexForCountingOnly = false;
				return true;
			case FOURCC_strh:
				int streamType = readInt();
				if (streamType != FOURCC_vids) {
					if (verbose)
						IJ.log("Non-video Stream '"+fourccString(streamType)+" skipped");
					streamNumber++;
					return false;
				}
				readStreamHeader();
				return true;
			case FOURCC_strf:
				readBitMapInfo(endPosition);
				return true;
			case FOURCC_indx:
			case FOURCC_00ix:
				readAvi2Index(endPosition);
				return true;
			case FOURCC_idx1:
				readOldFrameIndex(endPosition);
				return true;
			case FOURCC_RIFF:
				readAVIX(endPosition);
				return true;
			case FOURCC_movi:
				readMovieData(endPosition);
				return true;
		}
		throw new Exception("Program error, type "+fourccString(fourcc));
	}

	void readAviHeader() throws Exception, IOException {		//'avih'
		dwMicroSecPerFrame = readInt();
		dwMaxBytesPerSec = readInt();
		dwReserved1 = readInt(); //in newer avi formats, this is dwPaddingGranularity?
		dwFlags = readInt();
		dwTotalFrames = readInt();
		dwInitialFrames = readInt();
		dwStreams = readInt();
		dwSuggestedBufferSize = readInt();
		dwWidth = readInt();
		dwHeight = readInt();
		// dwReserved[4] follows, ignored

		if (verbose) {
			IJ.log("AVI HEADER (avih):"+timeString());
			IJ.log("   dwMicroSecPerFrame=" + dwMicroSecPerFrame);
			IJ.log("   dwMaxBytesPerSec=" + dwMaxBytesPerSec);
			IJ.log("   dwReserved1=" + dwReserved1);
			IJ.log("   dwFlags=" + dwFlags);
			IJ.log("   dwTotalFrames=" + dwTotalFrames);
			IJ.log("   dwInitialFrames=" + dwInitialFrames);
			IJ.log("   dwStreams=" + dwStreams);
			IJ.log("   dwSuggestedBufferSize=" + dwSuggestedBufferSize);
			IJ.log("   dwWidth=" + dwWidth);
			IJ.log("   dwHeight=" + dwHeight);
		}
	}

	void readStreamHeader() throws Exception, IOException {		//'strh'
		fccStreamHandler = readInt();
		dwStreamFlags = readInt();
		dwPriorityLanguage = readInt();
		dwStreamInitialFrames = readInt();
		dwStreamScale = readInt();
		dwStreamRate = readInt();
		dwStreamStart = readInt();
		dwStreamLength = readInt();
		dwStreamSuggestedBufferSize = readInt();
		dwStreamQuality = readInt();
		dwStreamSampleSize = readInt();
		//rcFrame rectangle follows, ignored
		if (verbose) {
			IJ.log("VIDEO STREAM HEADER (strh):");
			IJ.log("   fccStreamHandler='" + fourccString(fccStreamHandler)+"'");
			IJ.log("   dwStreamFlags=" + dwStreamFlags);
			IJ.log("   wPriority,wLanguage=" + dwPriorityLanguage);
			IJ.log("   dwStreamInitialFrames=" + dwStreamInitialFrames);
			IJ.log("   dwStreamScale=" + dwStreamScale);
			IJ.log("   dwStreamRate=" + dwStreamRate);
			IJ.log("   dwStreamStart=" + dwStreamStart);
			IJ.log("   dwStreamLength=" + dwStreamLength);
			IJ.log("   dwStreamSuggestedBufferSize=" + dwStreamSuggestedBufferSize);
			IJ.log("   dwStreamQuality=" + dwStreamQuality);
			IJ.log("   dwStreamSampleSize=" + dwStreamSampleSize);
		}
		if (dwStreamSampleSize > 1)
			throw new Exception("Video stream with "+dwStreamSampleSize+" (more than 1) frames/chunk not supported");
		// what the chunks in that stream will be named (we have two possibilites: uncompressed & compressed)
		type0xdb = FOURCC_00db + (streamNumber<<8); //'01db' for stream 1, etc. (inverse byte order!)
		type0xdc = FOURCC_00dc + (streamNumber<<8); //'01dc' for stream 1, etc.
	}

	/** Read 'AVI 2'-type main index 'indx' or an 'ix00' index to frames
	 *	(only the types AVI_INDEX_OF_INDEXES and AVI_INDEX_OF_CHUNKS are supported) */
	private void readAvi2Index(long endPosition) throws Exception, IOException {
		short wLongsPerEntry = readShort();
		byte bIndexSubType = raFile.readByte();
		byte bIndexType = raFile.readByte();
		int nEntriesInUse = readInt();
		int dwChunkId = readInt();
		long qwBaseOffset = readLong();
		readInt();	// 3rd dwReserved (first two dwreserved are qwBaseOffset!)
		if (verbose) {
			String bIndexString = bIndexType == AVI_INDEX_OF_CHUNKS ? ": AVI_INDEX_OF_CHUNKS" :
					bIndexType == AVI_INDEX_OF_INDEXES ? ": AVI_INDEX_OF_INDEXES" : ": UNSUPPORTED";
			IJ.log("AVI 2 INDEX:");
			IJ.log("   wLongsPerEntry=" + wLongsPerEntry);
			IJ.log("   bIndexSubType=" + bIndexSubType);
			IJ.log("   bIndexType=" + bIndexType + bIndexString);
			IJ.log("   nEntriesInUse=" + nEntriesInUse);
			IJ.log("   dwChunkId='" + fourccString(dwChunkId)+"'");
			if (bIndexType == AVI_INDEX_OF_CHUNKS)
				IJ.log("   qwBaseOffset=" + "0x"+Long.toHexString(qwBaseOffset));
		}
		if (bIndexType == AVI_INDEX_OF_INDEXES) {		// 'indx' points to other indices
			if (wLongsPerEntry != 4) return;			//badly formed index, ignore it
			for (int i=0;i<nEntriesInUse;i++) {			//read all entries (each pointing to an ix00 index)
				long qwOffset = readLong();
				int dwSize = readInt();
				int dwDuration = readInt();				//number of frames in ix00; ignored: not always trustworthy
				if (verbose)
					IJ.log("   indx entry: '" +fourccString(dwChunkId)+"' incl header "+posSizeString(qwOffset,dwSize)+timeString());
				long nextIndxEntryPointer = raFile.getFilePointer();
				raFile.seek(qwOffset);					//qwOffset & dwSize here include chunk header of ix00
				findFourccAndRead(FOURCC_00ix, false, qwOffset+dwSize, true);
				raFile.seek(nextIndxEntryPointer);
				if (frameNumber>lastFrameToRead) break;
			}
		} else if (bIndexType == AVI_INDEX_OF_CHUNKS) {
			if (verbose) {
				IJ.log("readAvi2Index frameNumber="+frameNumber+" firstFrame="+firstFrame);
				if (indexForCountingOnly) IJ.log("<just counting frames, not interpreting index now>");
			}
			if (wLongsPerEntry != 2) return;				//badly formed index, ignore it
			if (dwChunkId != type0xdb && dwChunkId != type0xdc) { //not the stream we search for? (should not happen)
				if (verbose)
					IJ.log("INDEX ERROR: SKIPPED ix00, wrong stream number or type, should be "+
							fourccString(type0xdb)+" or "+fourccString(type0xdc));
				return;
			}
			if (indexForCountingOnly) {					//only count number of entries, don't put into table
				totalFramesFromIndex += nEntriesInUse;
				return;
			}
			for (int i=0;i<nEntriesInUse;i++) {
				long dwOffset = readInt() & 0xffffffffL;
				long pos=qwBaseOffset+dwOffset;
				int dwSize = readInt();
				if (isVirtual) IJ.showProgress((double)frameNumber/lastFrameToRead);
				if (frameNumber >= firstFrame && dwSize>0) { //only valid frames (no blank frames)
					frameInfos.add(new long[] {pos, dwSize, (long) frameNumber*dwMicroSecPerFrame});
					if (verbose)
						IJ.log("movie data "+frameNumber+" '"+fourccString(dwChunkId)+"' "+posSizeString(pos,dwSize)+timeString());
				}
				frameNumber++;
				if (frameNumber>lastFrameToRead) break;
			}
			if (verbose)
				IJ.log("Index read up to frame "+(frameNumber-1));
		}
	}

	/** Read AVI 1 index 'idx1' */
	private void readOldFrameIndex(long endPosition) throws Exception, IOException {
		//IJ.log("READ AVI 1 INDEX, isOversizedAvi1="+isOversizedAvi1);
		int offset = -1;		//difference between absolute frame address and address given in idx1
		int[] offsetsToTry = new int[] {0, (int)moviPosition}; // dwOffset may be w.r.t. file start or w.r.t. 'movi' list.
		long lastFramePos = 0;
		while (true) {
			if ((raFile.getFilePointer()+16) >endPosition) break;

			int dwChunkId = readInt();
			int dwFlags = readInt();
			int dwOffset = readInt();
			int dwSize = readInt();
			//IJ.log("idx1: dwOffset=0x"+Long.toHexString(dwOffset));
			//IJ.log("moviPosition=0x"+Long.toHexString(moviPosition));
			if ((dwChunkId==type0xdb || dwChunkId==type0xdc) && dwSize>0) {
				if (offset < 0) {		// find out what the offset refers to
					long temp = raFile.getFilePointer();
					for (int i=0; i<offsetsToTry.length; i++) {
						long pos = (dwOffset + offsetsToTry[i]) & SIZE_MASK;
						if (pos < moviPosition) continue;	// frame must be in 'movi' list
						raFile.seek(pos);
						int chunkIdAtPos = readInt();		// see whether this offset points to the desired chunk
						//IJ.log("read@=0x"+Long.toHexString(pos)+":  '"+fourccString(chunkIdAtPos)+"'");
						if (chunkIdAtPos == dwChunkId) {
							offset = offsetsToTry[i];
							break;
						}
					}
					if (verbose)
						IJ.log("idx1: dwOffsets are w.r.t. 0x"+(offset<0 ? " UNKONWN??" : Long.toHexString(offset)));
					raFile.seek(temp);
					if (offset < 0) return;					// neither offset works
				}
				long framePos = (dwOffset & SIZE_MASK) + offset;
				if (isOversizedAvi1)
					while (framePos < lastFramePos) framePos += FOUR_GB; //index entries are modulo 2^32, assume frames are ascending
				lastFramePos = framePos;
				if (frameNumber >= firstFrame) {
					frameInfos.add(new long[]{framePos+8, dwSize, (long)frameNumber*dwMicroSecPerFrame});
					if (verbose)
						IJ.log("idx1 movie data '"+fourccString(dwChunkId)+"' "+posSizeString(framePos,dwSize)+timeString());
				}
				frameNumber++;
				if (frameNumber>lastFrameToRead) break;
			} //if(dwChunkId...)
		} //while(true)
		if (verbose)
			IJ.log("Index read up to frame "+(frameNumber-1));

	}

	/**Read stream format chunk: starts with BitMapInfo, may contain palette
	*/
	void readBitMapInfo(long endPosition) throws Exception, IOException {
		biSize = readInt();
		biWidth = readInt();
		biHeight = readInt();
		biPlanes = readShort();
		biBitCount = readShort();
		biCompression = readInt();
		biSizeImage = readInt();
		biXPelsPerMeter = readInt();
		biYPelsPerMeter = readInt();
		biClrUsed = readInt();
		biClrImportant = readInt();
		if (verbose) {
			IJ.log("   biSize=" + biSize);
			IJ.log("   biWidth=" + biWidth);
			IJ.log("   biHeight=" + biHeight);
			IJ.log("   biPlanes=" + biPlanes);
			IJ.log("   biBitCount=" + biBitCount);
			IJ.log("   biCompression=0x" + Integer.toHexString(biCompression)+" '"+fourccString(biCompression)+"'");
			IJ.log("   biSizeImage=" + biSizeImage);
			IJ.log("   biXPelsPerMeter=" + biXPelsPerMeter);
			IJ.log("   biYPelsPerMeter=" + biYPelsPerMeter);
			IJ.log("   biClrUsed=" + biClrUsed);
			IJ.log("   biClrImportant=" + biClrImportant);
		}

		int allowedBitCount = 0;
		boolean readPalette = false;
		switch (biCompression) {
			case NO_COMPRESSION:
			case NO_COMPRESSION_RGB:
			case NO_COMPRESSION_RAW:
				dataCompression = NO_COMPRESSION;
				dataTopDown = biHeight<0;	//RGB mode is usually bottom-up, negative height signals top-down
				allowedBitCount = 8 | BITMASK24 | 32; //we don't support 1, 2 and 4 byte data
				readPalette = biBitCount <= 8;
				break;
			case NO_COMPRESSION_Y8:
			case NO_COMPRESSION_GREY:
			case NO_COMPRESSION_Y800:
				dataTopDown = true;
				dataCompression = NO_COMPRESSION;
				allowedBitCount = 8;
				break;
			case NO_COMPRESSION_Y16:
			case NO_COMPRESSION_MIL:
				dataCompression = NO_COMPRESSION;
				allowedBitCount = 16;
				break;
			case AYUV_COMPRESSION:
				dataCompression = AYUV_COMPRESSION;
				allowedBitCount = 32;
				break;
			case UYVY_COMPRESSION:
			case UYNV_COMPRESSION:
				dataTopDown = true;
			case CYUV_COMPRESSION:	//same, not top-down
			case V422_COMPRESSION:
				dataCompression = UYVY_COMPRESSION;
				allowedBitCount = 16;
				break;
			case YUY2_COMPRESSION:
			case YUNV_COMPRESSION:
			case YUYV_COMPRESSION:
				dataTopDown = true;
				dataCompression = YUY2_COMPRESSION;
				allowedBitCount = 16;
				break;
			case YVYU_COMPRESSION:
				dataTopDown = true;
				dataCompression = YVYU_COMPRESSION;
				allowedBitCount = 16;
				break;
			case IYUV_COMPRESSION:
			case I420_COMPRESSION:
			case YV12_COMPRESSION:
			case NV12_COMPRESSION:
			case NV21_COMPRESSION:
				dataCompression = (dataCompression==IYUV_COMPRESSION) ?
						I420_COMPRESSION : biCompression;
				dataTopDown = biHeight>0;
				isPlanarFormat = true;
				allowedBitCount = 12;
				break;
			case JPEG_COMPRESSION:
			case JPEG_COMPRESSION2:
			case JPEG_COMPRESSION3:
			case MJPG_COMPRESSION:
				dataCompression = JPEG_COMPRESSION;
				variableLength = true;
				break;
			case PNG_COMPRESSION:
			case PNG_COMPRESSION2:
			case PNG_COMPRESSION3:
				variableLength = true;
				dataCompression = PNG_COMPRESSION;
				break;
			default:
				throw new Exception("Unsupported compression: "+Integer.toHexString(biCompression)+
						(biCompression>=0x20202020 ? " '" + fourccString(biCompression)+"'" : ""));
		}

		int bitCountTest = (biBitCount==24) ? BITMASK24 : biBitCount;  //convert "24" to a flag
		if (allowedBitCount!=0 && (bitCountTest & allowedBitCount)==0)
			throw new Exception("Unsupported: "+biBitCount+" bits/pixel for compression '"+
					fourccString(biCompression)+"'");

		if (biHeight < 0)		//negative height was for top-down data in RGB mode
			biHeight = -biHeight;

		if (isPlanarFormat && ((biWidth&1)!=0 || (biHeight&1)!=0))
			throw new Exception("Odd size ("+biWidth+"x"+biHeight+") unsupported with "+fourccString(biCompression)+" compression");
		// raw & interleaved YUV: scan line is padded with zeroes to be a multiple of four bytes
		scanLineSize = isPlanarFormat ?
				(biWidth * biBitCount) / 8 : ((biWidth * biBitCount + 31) / 32) * 4;

		// a value of biClrUsed=0 means we determine this based on the bits per pixel, if there is a palette
		long spaceForPalette  = endPosition-raFile.getFilePointer();
		if (readPalette && biClrUsed==0 && spaceForPalette!=0)
			biClrUsed = 1 << biBitCount;

		if (verbose) {
			IJ.log("   > data compression=0x" + Integer.toHexString(dataCompression)+" '"+fourccString(dataCompression)+"'");
			IJ.log("   > palette colors=" + biClrUsed);
			IJ.log("   > scan line size=" + scanLineSize);
			IJ.log("   > data top down=" + dataTopDown);
		}

		//read color palette
		if (readPalette && biClrUsed > 0) {
			if (verbose)
				IJ.log("   Reading "+biClrUsed+" Palette colors: " + posSizeString(spaceForPalette));
			if (spaceForPalette < biClrUsed*4)
				throw new Exception("Not enough data ("+spaceForPalette+") for palette of size "+(biClrUsed*4));
			byte[]	pr	  = new byte[biClrUsed];
			byte[]	pg	  = new byte[biClrUsed];
			byte[]	pb	  = new byte[biClrUsed];
			for (int i = 0; i < biClrUsed; i++) {
				pb[i] = raFile.readByte();
				pg[i] = raFile.readByte();
				pr[i] = raFile.readByte();
				raFile.readByte();
			}
			cm = new IndexColorModel(biBitCount, biClrUsed, pr, pg, pb);
		}
	}

	/**Read from the 'movi' chunk. Skips audio ('..wb', etc.), 'LIST' 'rec' etc, only reads '..db' or '..dc'*/
	void readMovieData(long endPosition) throws Exception, IOException {
		if (verbose)
			IJ.log("MOVIE DATA "+posSizeString(endPosition-raFile.getFilePointer())+timeString()+
					"\nSearching for stream "+streamNumber+": '"+
					fourccString(type0xdb)+"' or '"+fourccString(type0xdc)+"' chunks");
		if (isVirtual) {
			if (frameInfos==null)						// we might have it already from reading the first chunk
				frameInfos = new Vector<long[]>(lastFrameToRead);	// holds frame positions in file (for non-constant frame sizes, should hold long[] with pos and size)
		} else if (stack==null)
				stack = new ImageStack(dwWidth, biHeight);
		while (true) {									//loop over all chunks
			int type = readType(endPosition);
			if (type==0) break;							//endPosition of 'movi' reached?
			long size = readInt() & SIZE_MASK;
			long pos = raFile.getFilePointer();
			long nextPos = pos + size;
			if (nextPos > endPosition && nextPos < fileSize-8 && fileSize > FOUR_GB) {
				endPosition =  fileSize;				//looks like old ImageJ AVI 1.0 >4GB: wrong endPosition
			}
			if ((type==type0xdb || type==type0xdc) && size>0) {
				IJ.showProgress((double)frameNumber /lastFrameToRead);
				if (verbose)
					IJ.log(frameNumber+" movie data '"+fourccString(type)+"' "+posSizeString(size)+timeString());
				if (frameNumber >= firstFrame) {
					if (isVirtual)
						frameInfos.add(new long[]{pos, size, frameNumber*dwMicroSecPerFrame});
					else {						 //read the frame
						Object pixels = readFrame(raFile, pos, (int)size);
						String label = frameLabel(frameNumber*dwMicroSecPerFrame);
						stack.addSlice(label, pixels);
					}
				}
				frameNumber++;
				if (frameNumber>lastFrameToRead) break;
			} else if (verbose)
				IJ.log("skipped '"+fourccString(type)+"' "+posSizeString(size));
			if (nextPos > endPosition) break;
			raFile.seek(nextPos);
		}
	}

	/** Reads a frame at a given position in the file, returns pixels array */
	private Object readFrame (RandomAccessFile rFile, long filePos, int size)
			throws Exception, IOException {
		rFile.seek(filePos);
		//if (verbose)
		//IJ.log("virtual AVI: readFrame @"+posSizeString(filePos, size)+" varlength="+variableLength);
		if (variableLength)					//JPEG or PNG-compressed frames
			return readCompressedFrame(rFile, size);
		else
			return readFixedLengthFrame(rFile, size);
	}

	/** Reads a JPEG or PNG-compressed frame from a RandomAccessFile and
	 *	returns the pixels array of the resulting image and sets the
	 *	ColorModel cm (if appropriate) */
	private Object readCompressedFrame (RandomAccessFile rFile, int size)
			throws Exception, IOException {
		InputStream inputStream = new raInputStream(rFile, size, biCompression==MJPG_COMPRESSION);
		BufferedImage bi = ImageIO.read(inputStream);
		if (bi==null) throw new Exception("can't read frame, ImageIO returns null");
		int type = bi.getType();
		ImageProcessor ip = null;
		if (type==BufferedImage.TYPE_BYTE_GRAY) {
			ip = new ByteProcessor(bi);
		} else if (type==bi.TYPE_BYTE_INDEXED) {
			cm = bi.getColorModel();
			ip = new ByteProcessor((Image)bi);
		} else
			ip =  new ColorProcessor(bi);
		if (convertToGray)
			ip = ip.convertToByte(false);
		if (flipVertical)
			ip.flipVertical();
		if (ip.getWidth()!=dwWidth || ip.getHeight()!=biHeight)
			ip = ip.resize(dwWidth, biHeight);
		return ip.getPixels();
	}

	/** Read a fixed-length frame (RandomAccessFile rFile, long filePos, int size)
	 *	return the pixels array of the resulting image
	 */
	private Object readFixedLengthFrame (RandomAccessFile rFile, int size)	throws Exception, IOException {
		if (size < scanLineSize*biHeight && biBitCount==24  && !isPlanarFormat)
			size = scanLineSize*biHeight; // bugfix for RGB odd-width files
		if (size < scanLineSize*biHeight) //check minimum size (fixed frame length format)
			throw new Exception("Data chunk size "+size+" too short ("+(scanLineSize*biHeight)+" required)");
		byte[] rawData = new byte[size];
		int	 n	= rFile.read(rawData, 0, size);
		if (n < rawData.length)
			throw new Exception("Frame ended prematurely after " + n + " bytes");

		boolean topDown = flipVertical ? !dataTopDown : dataTopDown;
		Object pixels = null;
		byte[] bPixels = null;
		int[] cPixels = null;
		short[] sPixels = null;
		if (biBitCount <=8 || convertToGray) {
			bPixels = new byte[dwWidth * biHeight];
			pixels = bPixels;
		} else if (biBitCount == 16 && dataCompression == NO_COMPRESSION) {
			sPixels = new short[dwWidth * biHeight];
			pixels = sPixels;
		} else {
			cPixels = new int[dwWidth * biHeight];
			pixels = cPixels;
		}
		if (isPlanarFormat && !convertToGray)
			unpackPlanarImage(rawData, cPixels, topDown);
		else {
			int	 offset		= topDown ? 0 : (biHeight-1)*dwWidth;
			int	 rawOffset	= 0;
			for (int i = biHeight - 1; i >= 0; i--) {  //for all lines
				if (biBitCount <=8 || isPlanarFormat)
					unpack8bit(rawData, rawOffset, bPixels, offset, dwWidth);
				else if (convertToGray)
					unpackGray(rawData, rawOffset, bPixels, offset, dwWidth);
				else if (biBitCount==16 && dataCompression == NO_COMPRESSION)
					unpackShort(rawData, rawOffset, sPixels, offset, dwWidth);
				else
					unpack(rawData, rawOffset, cPixels, offset, dwWidth);
				rawOffset += isPlanarFormat ? dwWidth : scanLineSize;
				offset += topDown ? dwWidth : -dwWidth;
			}
		}
		return pixels;
	}

	/** For one line: copy byte data into the byte array for creating a ByteProcessor */
	void unpack8bit(byte[] rawData, int rawOffset, byte[] pixels, int byteOffset, int w) {
		for (int i = 0; i < w; i++)
			pixels[byteOffset + i] = rawData[rawOffset + i];
	}

	/** For one line: Unpack and convert YUV or RGB video data to grayscale (byte array for ByteProcessor) */
	void unpackGray(byte[] rawData, int rawOffset, byte[] pixels, int byteOffset, int w) {
		int	 j	   = byteOffset;
		int	 k	   = rawOffset;
		if (dataCompression == 0) {
			for (int i = 0; i < w; i++) {
				int	 b0	 = (((int) (rawData[k++])) & 0xff);
				int	 b1	 = (((int) (rawData[k++])) & 0xff);
				int	 b2	 = (((int) (rawData[k++])) & 0xff);
				if (biBitCount==32) k++; // ignore 4th byte (alpha value)
				pixels[j++] = (byte)((b0*934 + b1*4809 + b2*2449 + 4096)>>13); //0.299*R+0.587*G+0.114*B
			}
		} else {
			if (dataCompression==UYVY_COMPRESSION || dataCompression==AYUV_COMPRESSION)
				k++; //skip first byte in these formats (chroma)
			int step = dataCompression==AYUV_COMPRESSION ? 4 : 2;
			for (int i = 0; i < w; i++) {
				pixels[j++] = rawData[k];	//Non-standard: no scaling from 16-235 to 0-255 here
				k+=step;
			}
		}
	}

	/** For one line: Unpack 16bit grayscale data and convert to short array for ShortProcessor */
	void unpackShort(byte[] rawData, int rawOffset, short[] pixels, int shortOffset, int w) {
		int	 j	   = shortOffset;
		int	 k	   = rawOffset;
		for (int i = 0; i < w; i++) {
			pixels[j++] = (short) ((int)(rawData[k++] & 0xFF)| (((int)(rawData[k++] & 0xFF))<<8));
		}
	}

	/** For one line: Read YUV, RGB or RGB+alpha data and writes RGB int array for ColorProcessor */
	void unpack(byte[] rawData, int rawOffset, int[] pixels, int intOffset, int w) {
		int	 j	   = intOffset;
		int	 k	   = rawOffset;
		switch (dataCompression) {
			case NO_COMPRESSION:
				for (int i = 0; i < w; i++) {
					int	 b0	 = (((int) (rawData[k++])) & 0xff);
					int	 b1	 = (((int) (rawData[k++])) & 0xff) << 8;
					int	 b2	 = (((int) (rawData[k++])) & 0xff) << 16;
					if (biBitCount==32) k++; // ignore 4th byte (alpha value)
					pixels[j++] = 0xff000000 | b0 | b1 | b2;
				}
				break;
			case YUY2_COMPRESSION:
				for (int i = 0; i < w/2; i++) {
					int y0 = rawData[k++] & 0xff;
					int u  = rawData[k++] ^ 0xffffff80; //converts byte range 0...ff to -128 ... 127
					int y1 = rawData[k++] & 0xff;
					int v  = rawData[k++] ^ 0xffffff80;
					writeRGBfromYUV(y0, u, v, pixels, j++);
					writeRGBfromYUV(y1, u, v, pixels, j++);
				}
				break;
			case UYVY_COMPRESSION:
				for (int i = 0; i < w/2; i++) {
					int u  = rawData[k++] ^ 0xffffff80;
					int y0 = rawData[k++] & 0xff;
					int v  = rawData[k++] ^ 0xffffff80;
					int y1 = rawData[k++] & 0xff;
					writeRGBfromYUV(y0, u, v, pixels, j++);
					writeRGBfromYUV(y1, u, v, pixels, j++);
				}
				break;
			case YVYU_COMPRESSION:
				for (int i = 0; i < w/2; i++) {
					int y0 = rawData[k++] & 0xff;
					int v  = rawData[k++] ^ 0xffffff80;
					int y1 = rawData[k++] & 0xff;
					int u  = rawData[k++] ^ 0xffffff80;
					writeRGBfromYUV(y0, u, v, pixels, j++);
					writeRGBfromYUV(y1, u, v, pixels, j++);
				}
				break;
			case AYUV_COMPRESSION:
				for (int i = 0; i < w; i++) {
					k++;	//ignore alpha channel
					int y  = rawData[k++] & 0xff;
					int v  = rawData[k++] ^ 0xffffff80;
					int u  = rawData[k++] ^ 0xffffff80;
					writeRGBfromYUV(y, u, v, pixels, j++);
				}
				break;

		}
	}

	/** Unpack planar YV12 or I420 format (full frame). */
	void unpackPlanarImage(byte[] rawData, int[] cPixels, boolean topDown) {
		int w = dwWidth, h = dwHeight;
		int uP =  w*h, vP = w*h;					// pointers in U, V array
		int uvInc = (dataCompression==NV12_COMPRESSION || dataCompression==NV21_COMPRESSION) ?
				2 : 1;	// NV12, NV21 have interleaved u,v
		if (dataCompression == YV12_COMPRESSION)	// separate planes for U and V, 2-fold subsampling in x&y
			uP += w*h/4; // first V, then U
		else if (dataCompression == I420_COMPRESSION)
			vP += w*h/4; // first U, then V
		else if (dataCompression == NV12_COMPRESSION)
			vP++;  //interleaved U, then V
		else //NV21_COMPRESSION
			uP++;
		int lineOutInc = topDown ? w : -w;
		for (int line=0; line<h; line+=2) {
			int pRaw0 = line*w;
			int pRawEnd = pRaw0 + w;
			int pOut = topDown ? line*w : (h-line-1)*w;
			for (int pRaw = pRaw0; pRaw < pRawEnd; ) {
				int u = rawData[uP] ^ 0xffffff80;	// u and v for 2x2-pixel block
				int v = rawData[vP] ^ 0xffffff80;
				writeRGBfromYUV(rawData[pRaw] & 0xff, u, v, cPixels, pOut);
				writeRGBfromYUV(rawData[pRaw+w] & 0xff, u, v, cPixels, pOut+lineOutInc);
				pRaw++; pOut++;
				writeRGBfromYUV(rawData[pRaw] & 0xff, u, v, cPixels, pOut);
				writeRGBfromYUV(rawData[pRaw+w] & 0xff, u, v, cPixels, pOut+lineOutInc);
				pRaw++; pOut++;
				uP+=uvInc; vP+=uvInc;
			}
		}
	}

	/** Write an intData RGB value converted from YUV,
	 *	The y range between 16 and 235 becomes 0...255
	 *	u, v should be between -112 and +112
	 */
	final void writeRGBfromYUV(int y, int u, int v, int[]pixels, int intArrayIndex) {
		//int r = (int)(1.164*(y-16)+1.596*v+0.5);
		//int g = (int)(1.164*(y-16)-0.391*u-0.813*v+0.5);
		//int b = (int)(1.164*(y-16)+2.018*u+0.5);
		int r = (9535*y + 13074*v -148464) >> 13;
		int g = (9535*y - 6660*v - 3203*u -148464) >> 13;
		int b = (9535*y + 16531*u -148464) >> 13;
		if (r>255) r=255; if (r<0) r=0;
		if (g>255) g=255; if (g<0) g=0;
		if (b>255) b=255; if (b<0) b=0;
		pixels[intArrayIndex] = 0xff000000 | (r<<16) | (g<<8) | b;
	}

	/** Read 8-byte int with Intel (little-endian) byte order
	 * (note: RandomAccessFile.readLong has other byte order than AVI) */

	final long readLong() throws IOException {
		long low = readInt() & 0x00000000FFFFFFFFL;
		long high = readInt() & 0x00000000FFFFFFFFL;
		long result = high <<32 | low;
		return (long) result; //(high << 32 | low);
	}
	/** Read 4-byte int with Intel (little-endian) byte order
	 * (note: RandomAccessFile.readInt has other byte order than AVI) */

	final int readInt() throws IOException {
		int	 result = 0;
		for (int shiftBy = 0; shiftBy < 32; shiftBy += 8)
			result |= (raFile.readByte() & 0xff) << shiftBy;
		return result;
	}

	/** Read 2-byte short with Intel (little-endian) byte order
	 * (note: RandomAccessFile.readShort has other byte order than AVI) */
	final short readShort() throws IOException {
		int	 low   = raFile.readByte() & 0xff;
		int	 high  = raFile.readByte() & 0xff;
		return (short) (high << 8 | low);
	}

	/** Read type of next chunk that is not JUNK.
	 *	Returns type (or 0 if no non-JUNK chunk until endPosition) */
	private int readType(long endPosition) throws IOException {
		while (true) {
			long pos = raFile.getFilePointer();
			if (pos%paddingGranularity!=0) {
				pos = (pos/paddingGranularity+1)*paddingGranularity;
				raFile.seek(pos);	 //pad to even address
			}
			if (pos >= endPosition) return 0;
			int type = readInt();
			if (type != FOURCC_JUNK)
				return type;
			long size = readInt()&SIZE_MASK;
			if (verbose)
				IJ.log("Skip JUNK: "+posSizeString(size));
			raFile.seek(raFile.getFilePointer()+size);	//skip junk
		}
	}

	private void setFramesPerSecond (ImagePlus imp) {
		if (dwMicroSecPerFrame<1000 && dwStreamRate>0)	//if no reasonable frame time, get it from rate
			dwMicroSecPerFrame = (int)(dwStreamScale*1e6/dwStreamRate);
		if (dwMicroSecPerFrame>=1000)
			imp.getCalibration().fps = 1e6 / dwMicroSecPerFrame;
	}

	private String frameLabel(long timeMicroSec) {
		return IJ.d2s(timeMicroSec/1.e6)+" s";
	}

	private String posSizeString(long size) throws IOException {
		return posSizeString(raFile.getFilePointer(), size);
	}

	private String posSizeString(long pos, long size) {
		return "0x"+Long.toHexString(pos)+"-0x"+Long.toHexString(pos+size-1)+" ("+size+" Bytes)";
	}

	private String timeString() {
		return " (t="+(System.currentTimeMillis()-startTime)+" ms)";
	}

	/** returns a string of a four-cc code corresponding to an int (Intel byte order) */
	private String fourccString(int fourcc) {
		String s = "";
		for (int i=0; i<4; i++) {
			int c = fourcc&0xff;
			s += Character.toString((char)c);
			fourcc >>= 8;
		}
		return s;
	}

	/** tries to close the given file (if not null) */
	private void closeFile(RandomAccessFile rFile) {
		if (rFile != null) try {
			rFile.close();
        } catch (Exception e) {}
	}

	private void error(String msg) {
		 aborting = true;
		 IJ.error("AVI Reader", msg);
	}

	private String exceptionMessage (Exception e) {
		String	msg;
		if (e.getClass() == Exception.class)	//for "home-built" exceptions: message only
			msg = e.getMessage();
		else
			msg = e + "\n" + e.getStackTrace()[0]+"\n"+e.getStackTrace()[1];
		return "An error occurred reading the AVI file.\n \n" + msg;
	}

	/** An input stream reading from a RandomAccessFile (starting at the current position).
	 *	This class also adds 'Define Huffman Table' (DHT) segments to convert MJPG to JPEG.
	 */
	final private static int BUFFERSIZE = 4096; //should be large enough to hold the full JFIF header
							// up to beginning of the image data and the Huffman tables
	final private static byte[] HUFFMAN_TABLES = new byte[] {	//the 'DHT' segment
			(byte)0xFF,(byte)0xC4,0x01,(byte)0xA2,	//these 4 bytes are tag & length; data follow
			0x00,0x00,0x01,0x05,0x01,0x01,0x01,0x01,0x01,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x01,0x00,0x03,0x01,0x01,0x01,0x01,
			0x01,0x01,0x01,0x01,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
			0x08,0x09,0x0A,0x0B,0x10,0x00,0x02,0x01,0x03,0x03,0x02,0x04,0x03,0x05,0x05,0x04,0x04,0x00,
			0x00,0x01,0x7D,0x01,0x02,0x03,0x00,0x04,0x11,0x05,0x12,0x21,0x31,0x41,0x06,0x13,0x51,0x61,
			0x07,0x22,0x71,0x14,0x32,(byte)0x81,(byte)0x91,(byte)0xA1,0x08,0x23,0x42,
			(byte)0xB1,(byte)0xC1,0x15,0x52,(byte)0xD1,(byte)0xF0,0x24,
			0x33,0x62,0x72,(byte)0x82,0x09,0x0A,0x16,0x17,0x18,0x19,0x1A,0x25,0x26,0x27,0x28,0x29,0x2A,0x34,
			0x35,0x36,0x37,0x38,0x39,0x3A,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4A,0x53,0x54,0x55,0x56,
			0x57,0x58,0x59,0x5A,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6A,0x73,0x74,0x75,0x76,0x77,0x78,
			0x79,0x7A,(byte)0x83,(byte)0x84,(byte)0x85,(byte)0x86,(byte)0x87,(byte)0x88,(byte)0x89,
			(byte)0x8A,(byte)0x92,(byte)0x93,(byte)0x94,(byte)0x95,(byte)0x96,(byte)0x97,(byte)0x98,(byte)0x99,
			(byte)0x9A,(byte)0xA2,(byte)0xA3,(byte)0xA4,(byte)0xA5,(byte)0xA6,(byte)0xA7,(byte)0xA8,(byte)0xA9,
			(byte)0xAA,(byte)0xB2,(byte)0xB3,(byte)0xB4,(byte)0xB5,(byte)0xB6,(byte)0xB7,(byte)0xB8,(byte)0xB9,
			(byte)0xBA,(byte)0xC2,(byte)0xC3,(byte)0xC4,(byte)0xC5,(byte)0xC6,(byte)0xC7,(byte)0xC8,(byte)0xC9,
			(byte)0xCA,(byte)0xD2,(byte)0xD3,(byte)0xD4,(byte)0xD5,(byte)0xD6,(byte)0xD7,(byte)0xD8,(byte)0xD9,
			(byte)0xDA,(byte)0xE1,(byte)0xE2,(byte)0xE3,(byte)0xE4,(byte)0xE5,(byte)0xE6,(byte)0xE7,(byte)0xE8,
			(byte)0xE9,(byte)0xEA,(byte)0xF1,(byte)0xF2,(byte)0xF3,(byte)0xF4,(byte)0xF5,(byte)0xF6,(byte)0xF7,
			(byte)0xF8,(byte)0xF9,(byte)0xFA,0x11,0x00,0x02,0x01,0x02,0x04,0x04,0x03,0x04,0x07,0x05,0x04,0x04,0x00,0x01,
			0x02,0x77,0x00,0x01,0x02,0x03,0x11,0x04,0x05,0x21,0x31,0x06,0x12,0x41,0x51,0x07,0x61,0x71,
			0x13,0x22,0x32,(byte)0x81,0x08,0x14,0x42,(byte)0x91,(byte)0xA1,(byte)0xB1,(byte)0xC1,0x09,0x23,0x33,
			0x52,(byte)0xF0,0x15,0x62,
			0x72,(byte)0xD1,0x0A,0x16,0x24,0x34,(byte)0xE1,0x25,(byte)0xF1,0x17,0x18,0x19,0x1A,0x26,0x27,0x28,0x29,0x2A,
			0x35,0x36,0x37,0x38,0x39,0x3A,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4A,0x53,0x54,0x55,0x56,
			0x57,0x58,0x59,0x5A,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6A,0x73,0x74,0x75,0x76,0x77,0x78,
			0x79,0x7A,(byte)0x82,(byte)0x83,(byte)0x84,(byte)0x85,(byte)0x86,(byte)0x87,(byte)0x88,(byte)0x89,
			(byte)0x8A,(byte)0x92,(byte)0x93,(byte)0x94,(byte)0x95,(byte)0x96,(byte)0x97,(byte)0x98,
			(byte)0x99,(byte)0x9A,(byte)0xA2,(byte)0xA3,(byte)0xA4,(byte)0xA5,(byte)0xA6,(byte)0xA7,(byte)0xA8,
			(byte)0xA9,(byte)0xAA,(byte)0xB2,(byte)0xB3,(byte)0xB4,(byte)0xB5,(byte)0xB6,(byte)0xB7,(byte)0xB8,
			(byte)0xB9,(byte)0xBA,(byte)0xC2,(byte)0xC3,(byte)0xC4,(byte)0xC5,(byte)0xC6,(byte)0xC7,(byte)0xC8,
			(byte)0xC9,(byte)0xCA,(byte)0xD2,(byte)0xD3,(byte)0xD4,(byte)0xD5,(byte)0xD6,(byte)0xD7,(byte)0xD8,
			(byte)0xD9,(byte)0xDA,(byte)0xE2,(byte)0xE3,(byte)0xE4,(byte)0xE5,(byte)0xE6,(byte)0xE7,(byte)0xE8,
			(byte)0xE9,(byte)0xEA,(byte)0xF2,(byte)0xF3,(byte)0xF4,(byte)0xF5,(byte)0xF6,(byte)0xF7,(byte)0xF8,
			(byte)0xF9,(byte)0xFA };
	final private static int HUFFMAN_LENGTH = 420;

	class raInputStream extends InputStream {
		RandomAccessFile rFile; //where to read the data from
		int readableSize;		//number of bytes that one should expect to be readable
		boolean fixMJPG;		//whether to use an ugly hack to convert MJPG frames to JPEG
		byte[] buffer;			//holds beginning of data for fixing Huffman tables
		int bufferPointer;		//next position in buffer to read
		int bufferLength;		//bytes allocated in buffer

		/** Constructor */
		raInputStream (RandomAccessFile rFile, int readableSize, boolean fixMJPG) throws IOException {
			this.rFile = rFile;
			this.readableSize = readableSize;
			this.fixMJPG = fixMJPG;
			if (fixMJPG) {
				buffer = new byte[BUFFERSIZE];
				bufferLength = Math.min(BUFFERSIZE-HUFFMAN_LENGTH, readableSize);
				bufferLength = rFile.read(buffer, 0, bufferLength);
				addHuffmanTables();
			}
		}

		public int available () {
			return readableSize;
		}

		// Read methods:
		// There is no check against reading beyond the allowed range, which is
		// start position + readableSize
		// (i.e., reading beyond the frame in the avi file would be possible).
		/** Read a single byte */
		public int read () throws IOException {
			readableSize--;
			if (fixMJPG) {
				int result = buffer[bufferPointer] & 0xff;
				bufferPointer++;
				if (bufferPointer >= bufferLength) fixMJPG = false; //buffer exhausted, no more attempt to fix it
				return result;
			} else
				return rFile.read();
		}

		/** Read bytes into an array */
		public int read (byte[] b, int off, int len) throws IOException {
			//IJ.log("read "+len+" bytes, fix="+fixMJPG);
			int nBytes;
			if (fixMJPG) {
				nBytes = Math.min(len, bufferLength-bufferPointer);
				System.arraycopy(buffer, bufferPointer, b, off, nBytes);
				bufferPointer += nBytes;
				if (bufferPointer >= bufferLength) {
					fixMJPG = false;
					if (len-nBytes > 0)
						nBytes += rFile.read(b, off+nBytes, len-nBytes);
				}
			} else
				nBytes = rFile.read(b, off, len);
			readableSize -= nBytes;
			return nBytes;
		}
		// Add Huffman table if not present yet
		private void addHuffmanTables() {
			if (readShort(0)!=0xffd8 || bufferLength<6) return;	  //not a start of JPEG-like data
			int offset = 2;
			int segmentLength = 0;
			do {
				int code = readShort(offset);				//read segment type
				//IJ.log("code=0x"+Long.toHexString(code));
				if (code==0xffc4)							//Huffman table found, nothing to do
					return;
				else if (code==0xffda || code==0xffd9) {	//start of image data or end of image?
					insertHuffmanTables(offset);
					return;									//finished
				}
				offset += 2;
				segmentLength = readShort(offset);			//read length of this segment
				offset += segmentLength;					//and skip the segment contents
			} while (offset<bufferLength-4 && segmentLength>=0);
		}

		// read a short from the buffer
		private int readShort(int offset) {
			return ((buffer[offset]&0xff)<<8) | (buffer[offset+1]&0xff);
		}

		// insert Huffman tables at the given position
		private void insertHuffmanTables(int position) {
			//IJ.log("inserting Huffman tables");
			System.arraycopy(buffer, position, buffer, position+HUFFMAN_LENGTH, bufferLength-position);
			System.arraycopy(HUFFMAN_TABLES, 0, buffer, position, HUFFMAN_LENGTH);
			bufferLength += HUFFMAN_LENGTH;
			readableSize += HUFFMAN_LENGTH;
		}
	}

	public void displayDialog(boolean displayDialog) {
		this.displayDialog = displayDialog;
	}

}
