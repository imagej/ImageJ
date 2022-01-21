package ij.io;

import loci.formats.codec.CodecOptions;
import loci.formats.codec.LZWCodec;

/**
 * TODO
 *
 * @author Barry DeZonia
 */
public class LzwEncoder {

	LzwEncoder() {}

	static public byte[] encode(byte[] input)
	{
		byte[] output = null;
		try {
			output = new LZWCodec().compress(input, CodecOptions.getDefaultOptions()); // compress the output data
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return output;
	}

}
