import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;

public class Test_Pixels implements PlugIn {
	int width=2000, height=2000;

	public void run(String arg) {
		ByteProcessor bp = new ByteProcessor(width, height);
		
		// set zero mark (load classes, etc)
		test1(bp);
		test2(bp);
		test3(bp);
		test4(bp);

		// start:
		long time0 = System.currentTimeMillis();
		test1(bp);
		long time1 = System.currentTimeMillis();
		test2(bp);
		long time2 = System.currentTimeMillis();
		test3(bp);
		long time3 = System.currentTimeMillis();
		test4(bp);
		long time4 = System.currentTimeMillis();

		IJ.log("setPixel(x,y): " + (time1 - time0) + " ms.");
		IJ.log("set(x,y): " + (time3 - time2) + " ms.");
		IJ.log("set(index): " + (time4 - time3) + " ms.");
		IJ.log("setPixels(): " + (time2 - time1) + " ms.");
		IJ.log("Ratio 1: " + IJ.d2s((double)(time1-time0)/(time2-time1),1));
		IJ.log("Ratio 2: " + IJ.d2s((double)(time3-time2)/(time2-time1),1));
		IJ.log("Ratio 3: " + IJ.d2s((double)(time4-time3)/(time2-time1),1));
	}

	private void test1(ImageProcessor ip) {
		for (int y=0; y<height; y++)
			for (int x=0; x<width; x++)
				ip.putPixel(x, y, 255 - ip.getPixel(x,y));
	}

	private void test2(ImageProcessor ip) {
		byte[] b = (byte[])ip.getPixels();
		for (int y=0; y<height; y++)
			for (int x=0; x<width; x++)
				b[y*width+x] = (byte)(255- b[y*width+x]&255);
	}

	private void test3(ImageProcessor ip) {
		for (int y=0; y<height; y++)
			for (int x=0; x<width; x++)
				ip.set(x, y, 255 - ip.get(x,y));
	}

	private void test4(ImageProcessor ip) {
		for (int i=0; i<height*height; i++)
			ip.set(i, 255 - ip.get(i));
	}
}
