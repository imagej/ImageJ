package ij.io;

import ij.IJ;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Decompresses Zstandard-compressed (RFC 8878) TIFF strips.
 *
 * The Java runtime has no built-in Zstandard codec, the way it has
 * java.util.zip.Inflater for Deflate, so this class binds at run time to
 * whichever Zstandard library happens to be on the class path:
 *
 *   1. zstd-jni     (com.github.luben.zstd.Zstd) - JNI wrapper around libzstd
 *   2. aircompressor (io.airlift.compress.zstd.ZstdDecompressor) - pure Java
 *
 * Both are already shipped with Fiji, so Zstandard TIFFs open there without
 * any extra installation. In a plain ImageJ distribution neither is present,
 * and openers report a message telling the user what to add.
 *
 * Binding reflectively keeps ij.jar free of compile-time dependencies.
 */
public class ZstdDecoder {

	/** Set once, on first use, by init(). */
	private static Backend backend;
	private static boolean initialized;

	/** Returns true if a Zstandard library was found on the class path. */
	public static synchronized boolean isAvailable() {
		init();
		return backend!=null;
	}

	/** Returns the name of the library being used, or null if there is none. */
	public static synchronized String getBackendName() {
		init();
		return backend!=null?backend.name():null;
	}

	/** Decompresses a Zstandard frame.
	 * @param input        the compressed bytes
	 * @param expectedSize the caller's estimate of the decompressed size (the
	 *                     size of one TIFF strip), or 0 if not known. Used only
	 *                     when the frame header does not declare its own size.
	 * @return the decompressed bytes, or null if decompression is not possible
	 */
	public static synchronized byte[] decompress(byte[] input, int expectedSize) {
		init();
		if (backend==null) {
			IJ.error("ZSTD Decompression",
				"This TIFF is Zstandard-compressed, but no Zstandard library was found.\n \n"
				+"Add zstd-jni.jar or aircompressor.jar to the ImageJ/jars folder,\n"
				+"or open the image using the Bio-Formats plugin.");
			return null;
		}
		if (input==null || input.length==0)
			return input;
		// Prefer the size declared in the frame header; it is exact. Fall back to
		// the caller's strip-size estimate, which may overshoot on the last strip.
		long declared = backend.declaredSize(input);
		boolean exact = declared>0 && declared<=Integer.MAX_VALUE;
		int capacity = exact?(int)declared:expectedSize;
		if (capacity<=0)
			capacity = Math.max(1<<16, input.length*4);
		for (int attempt=0; ; attempt++) {
			try {
				byte[] output = new byte[capacity];
				int n = backend.decompress(input, output);
				if (n<0 || n>capacity)
					throw new IllegalStateException("bad decompressed length "+n);
				return n==capacity?output:Arrays.copyOf(output, n);
			} catch (OutOfMemoryError e) {
				throw e;
			} catch (Throwable e) {
				// Only an estimated capacity is worth retrying; a declared size is
				// authoritative, so a failure there is a genuinely corrupt frame.
				if (exact || attempt>=6 || capacity>(1<<28)) {
					IJ.log("ZSTD decompression failed: "+e);
					return null;
				}
				capacity *= 4;
			}
		}
	}

	private static void init() {
		if (initialized)
			return;
		initialized = true;
		try {
			backend = new ZstdJni();
			return;
		} catch (Throwable e) {}
		try {
			backend = new AirCompressor();
		} catch (Throwable e) {}
	}

	private interface Backend {
		String name();
		/** Decompressed size declared in the frame header, or 0 if not declared. */
		long declaredSize(byte[] input);
		/** Decompresses into output; returns the number of bytes written. */
		int decompress(byte[] input, byte[] output) throws Exception;
	}

	/** zstd-jni: static methods on com.github.luben.zstd.Zstd. */
	private static class ZstdJni implements Backend {
		private final Method decompressByteArray, decompressedSize;

		ZstdJni() throws Exception {
			Class<?> c = Class.forName("com.github.luben.zstd.Zstd");
			decompressByteArray = c.getMethod("decompressByteArray",
				byte[].class, int.class, int.class, byte[].class, int.class, int.class);
			decompressedSize = c.getMethod("decompressedSize", byte[].class);
			// Call a native method now, so that a jar without a binary for this
			// platform makes us fall through to the pure-Java backend here rather
			// than failing later, in the middle of reading an image.
			c.getMethod("maxCompressionLevel").invoke(null);
		}

		public String name() {return "zstd-jni";}

		public long declaredSize(byte[] input) {
			try {
				Object r = decompressedSize.invoke(null, (Object)input);
				return ((Number)r).longValue();
			} catch (Throwable e) {
				return 0L;
			}
		}

		public int decompress(byte[] input, byte[] output) throws Exception {
			Object r = decompressByteArray.invoke(null,
				output, Integer.valueOf(0), Integer.valueOf(output.length),
				input, Integer.valueOf(0), Integer.valueOf(input.length));
			long n = ((Number)r).longValue();
			// zstd-jni signals errors by returning a large negative-coded value.
			if (n<0 || n>output.length)
				throw new java.io.IOException("zstd-jni error code "+n);
			return (int)n;
		}
	}

	/** aircompressor: pure-Java io.airlift.compress.zstd.ZstdDecompressor. */
	private static class AirCompressor implements Backend {
		private final Object instance;
		private final Method decompress, getDecompressedSize;

		AirCompressor() throws Exception {
			Class<?> c = Class.forName("io.airlift.compress.zstd.ZstdDecompressor");
			instance = c.getConstructor().newInstance();
			decompress = c.getMethod("decompress",
				byte[].class, int.class, int.class, byte[].class, int.class, int.class);
			getDecompressedSize = c.getMethod("getDecompressedSize",
				byte[].class, int.class, int.class);
		}

		public String name() {return "aircompressor";}

		public long declaredSize(byte[] input) {
			try {
				Object r = getDecompressedSize.invoke(null,
					input, Integer.valueOf(0), Integer.valueOf(input.length));
				return ((Number)r).longValue();
			} catch (Throwable e) {
				return 0L;
			}
		}

		public int decompress(byte[] input, byte[] output) throws Exception {
			Object r = decompress.invoke(instance,
				input, Integer.valueOf(0), Integer.valueOf(input.length),
				output, Integer.valueOf(0), Integer.valueOf(output.length));
			return ((Number)r).intValue();
		}
	}

}
