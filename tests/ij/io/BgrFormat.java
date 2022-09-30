package ij.io;

/**
 * TODO
 *
 * @author Barry DeZonia
 */
public class BgrFormat extends PixelFormat {
	
	BgrFormat()
	{
		super("Bgr",3,8,1);  // super(String name, int numSamples, int bitsPerSample, int planes)
	}
	
	@Override
	boolean canDoImageCombo(int compression, ByteOrder.Value byteOrder, int headerBytes, boolean stripped)
	{
		if (compression == FileInfo.COMPRESSION_UNKNOWN)
			return false;
		if (compression == FileInfo.JPEG)  // TODO: remove this restriction to test jpeg compression
			return false;
		
		if (byteOrder == ByteOrder.Value.INTEL)
			return false;
		
		if (stripped && (compression == FileInfo.COMPRESSION_NONE))
			return false;
		
		return true;
	}
	
	@Override
	byte[] nativeBytes(long pix, ByteOrder.Value byteOrder)
	{
		byte[] output = new byte[3];
		
		output[0] = (byte)((pix & 0x0000ff) >> 0);
		output[1] = (byte)((pix & 0x00ff00) >> 8);
		output[2] = (byte)((pix & 0xff0000) >> 16);
		
		return output;
	}
	
	@Override
	byte[] getBytes(long[][] image, int compression, ByteOrder.Value byteOrder, int headerBytes, boolean inStrips, FileInfo fi)
	{
		initializeFileInfo(fi,FileInfo.BGR,compression,byteOrder,image.length,image[0].length);
		
		byte[] output;
		
		if (inStrips)
			output = PixelArranger.arrangeInStrips(this,image,fi);
		else
			output = PixelArranger.arrangeContiguously(this,image,fi);
		
		output = PixelArranger.attachHeader(fi,headerBytes,output);

		return output;
	}

	@Override
	Object expectedResults(long[][] inputImage)
	{
		int[] output = new int[inputImage.length * inputImage[0].length];
		
		// NOTICE that input is bgr but output is argb
		
		int i = 0;
		for (long[] row : inputImage)
			for (long pix : row)
				output[i++] = (int)(0xff000000 | (pix & 0xffffff));

		return output;
	}

	@Override
	Object pixelsFromBytes(byte[] bytes, ByteOrder.Value order)
	{
		// this method not tested by ImageWriter. Therefore no implementation until it will be used.
		return null;
	}
}

