package ij.io;

/**
 * TODO
 *
 * @author Barry DeZonia
 */
public class Rgb48PlanarFormat extends PixelFormat {

	Rgb48PlanarFormat()
	{
		super("Rgb48Planar",3,16,3);  // super(String name, int numSamples, int bitsPerSample, int planes)
	}
	
	@Override
	boolean canDoImageCombo(int compression, ByteOrder.Value byteOrder, int headerBytes, boolean stripped)
	{
		if (compression == FileInfo.COMPRESSION_UNKNOWN)
			return false;
		if (compression == FileInfo.JPEG)  // TODO: remove this restriction to test jpeg compression
			return false;
		if (compression == FileInfo.PACK_BITS)
			return false;
		
		// this method always exercises strips
		if (stripped == false)
			return false;
		
		return true;
	}

	@Override
	byte[] nativeBytes(long pix, ByteOrder.Value byteOrder)
	{
		byte[] output = new byte[6];
		
		long channel1 = ((pix & 0x00000000ffffL) >> 0);
		long channel2 = ((pix & 0x0000ffff0000L) >> 16);
		long channel3 = ((pix & 0xffff00000000L) >> 32);
		//
		// divide the long into three channels
		if (byteOrder == ByteOrder.Value.INTEL)
		{
			output[0] = (byte) ((channel1 & 0x00ff) >> 0);
			output[1] = (byte) ((channel1 & 0xff00) >> 8);
			output[2] = (byte) ((channel2 & 0x00ff) >> 0);
			output[3] = (byte) ((channel2 & 0xff00) >> 8);
			output[4] = (byte) ((channel3 & 0x00ff) >> 0);
			output[5] = (byte) ((channel3 & 0xff00) >> 8);
		}
		else
		{
			output[0] = (byte) ((channel1 & 0xff00) >> 8);
			output[1] = (byte) ((channel1 & 0x00ff) >> 0);
			output[2] = (byte) ((channel2 & 0xff00) >> 8);
			output[3] = (byte) ((channel2 & 0x00ff) >> 0);
			output[4] = (byte) ((channel3 & 0xff00) >> 8);
			output[5] = (byte) ((channel3 & 0x00ff) >> 0);
		}
		
		return output;
	}
	
	@Override
	byte[] getBytes(long[][] image, int compression, ByteOrder.Value byteOrder, int headerBytes, boolean inStrips, FileInfo fi)
	{
		initializeFileInfo(fi,FileInfo.RGB48_PLANAR,compression,byteOrder,image.length,image[0].length);
		
		byte[] output = PixelArranger.arrangeAsPlanes(this, image, fi, inStrips, true);
		
		output = PixelArranger.attachHeader(fi,headerBytes,output);

		return output;
	}

	
	@Override
	Object expectedResults(long[][] inputImage)
	{
		int rows = inputImage.length;
		int cols = inputImage[0].length;

		short[][] planes = new short[3][];
		
		
		for (int i = 0; i < 3; i++)
			planes[i] = new short[rows*cols];
		
		int i = 0;
		for (int r = 0; r < rows; r++)
			for (int c = 0; c < cols; c++)
			{
				planes[0][i] = (short) ((inputImage[r][c] & 0x00000000ffffL) >> 0);
				planes[1][i] = (short) ((inputImage[r][c] & 0x0000ffff0000L) >> 16);
				planes[2][i] = (short) ((inputImage[r][c] & 0xffff00000000L) >> 32);
				i++;
			}
		
		Object[] output = new Object[3];
		
		output[0] = planes[0];
		output[1] = planes[1];
		output[2] = planes[2];
		
		return output;
	}		

	@Override
	Object pixelsFromBytes(byte[] bytes, ByteOrder.Value order)
	{
		// this method not tested by ImageWriter. Therefore no implementation until it will be used.
		return null;
	}
}
	
