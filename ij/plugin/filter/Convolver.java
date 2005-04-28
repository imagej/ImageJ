package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.TextReader;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.io.*;

/** This plugin does convolutions on real images using user user defined kernels. */

public class Convolver implements PlugInFilter, ActionListener {

	static final int BYTE=0, SHORT=1, FLOAT=2, RGB=3;
	
	ImagePlus imp;
	int kw, kh;
	int slice = 1;
	boolean canceled;
	float[] kernel;
	boolean isLineRoi;
	Button open, save;
	GenericDialog gd;
	boolean normalize = true;
	
	static String kernelText = "-1 -1 -1 -1 -1\n-1 -1 -1 -1 -1\n-1 -1 24 -1 -1\n-1 -1 -1 -1 -1\n-1 -1 -1 -1 -1\n";
	static boolean normalizeFlag = true;

	public int setup(String arg, ImagePlus imp) {
 		IJ.register(Convolver.class);
		this.imp = imp;
		canceled = false;
		if (imp==null)
			{IJ.noImage(); return DONE;}
		IJ.resetEscape();
		Roi roi = imp.getRoi();
		isLineRoi= roi!=null && roi.isLine();
		kernel = getKernel();
		if (kernel==null)
			return DONE;
		if ((kw&1)==0) {
			IJ.error("Convolver","The kernel must be square and have an\n"
				+"odd width. This kernel is "+kw+"x"+kh+".");
			return DONE;
		}
		int flags = IJ.setupDialog(imp, DOES_ALL);
		if ((flags&DONE)!=0)
			return DONE;
		IJ.showStatus("Convolve: "+kw+"x"+kh+" kernel");
		imp.startTiming();
		return flags;
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		if (isLineRoi)
			ip.resetRoi();
		convolve(ip, kernel, kw, kh);
		if (slice>1)
			IJ.showStatus("Convolve: "+slice+"/"+imp.getStackSize());
		if (slice==imp.getStackSize()) {
			ip.resetMinAndMax();
		}
		slice++;
		if (canceled) Undo.undo();
	}
	
	float[] getKernel() {
		gd = new GenericDialog("Convolver...", IJ.getInstance());
		gd.addTextAreas(kernelText, null, 10, 30);
		gd.addPanel(makeButtonPanel(gd));
		gd.addCheckbox("Normalize Kernel", normalizeFlag);
		gd.showDialog();
		if (gd.wasCanceled()) {
			canceled = true;
			return null;
		}
		kernelText = gd.getNextText();
		normalizeFlag = gd.getNextBoolean();
		normalize = normalizeFlag;
		StringTokenizer st = new StringTokenizer(kernelText);
		int n = st.countTokens();
		kw = (int)Math.sqrt(n);
		kh = kw;
		n = kw*kh;
		float[] k = new float[n];
		for (int i=0; i<n; i++)
			k[i] = (float)getNum(st);
		//IJ.write("kw: "+kw);
		return k;
	}

	/** Creates a panel containing "Save..." and "Save..." buttons. */
	Panel makeButtonPanel(GenericDialog gd) {
		Panel buttons = new Panel();
    	buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		open = new Button("Open...");
		open.addActionListener(this);
		buttons.add(open);
		save = new Button("Save...");
		save.addActionListener(this);
		buttons.add(save);
		return buttons;
	}

	double getNum(StringTokenizer st) {
		Double d;
		String token = st.nextToken();
		try {d = new Double(token);}
		catch (NumberFormatException e){d = null;}
		if (d!=null)
			return(d.doubleValue());
		else
			return 0.0;
	}

	public boolean convolve(ImageProcessor ip, float[] kernel, int kw, int kh) {
		if (canceled) return false;
		if ((kw&1)!=1 || (kh&1)!=1)
			throw new IllegalArgumentException("Kernel width or height not odd");
		int type;
		if (ip instanceof ByteProcessor)
			type = BYTE;
		else if (ip instanceof ShortProcessor)
			type = SHORT;
		else if (ip instanceof FloatProcessor)
			type = FLOAT;
		else
			type = RGB;
		if (type==RGB) {
			convolveRGB(ip, kernel, kw, kh);
			return !canceled;
		}
		ip.setCalibrationTable(null);
		ImageProcessor ip2 = ip.convertToFloat();
		ip2.setMask(ip.getMask());
		ip2.setRoi(ip.getRoi());
		convolveFloat(ip2, kernel, kw, kh);
		switch (type) {
			case BYTE:
				ip2 = ip2.convertToByte(false);
				byte[] pixels = (byte[])ip.getPixels();
				byte[] pixels2 = (byte[])ip2.getPixels();
				System.arraycopy(pixels2, 0, pixels, 0, pixels.length);
				break;
			case SHORT:
				ip2 = ip2.convertToShort(false);
				short[] pixels16 = (short[])ip.getPixels();
				short[] pixels16b = (short[])ip2.getPixels();
				System.arraycopy(pixels16b, 0, pixels16, 0, pixels16.length);
				break;
			case FLOAT:
				break;
		}
		return !canceled;
	}
	
	public void setNormalize(boolean normalizeKernel) {
		normalize = normalizeKernel;
	}

	public void convolveRGB(ImageProcessor ip, float[] kernel, int kw, int kh) {
		int width = ip.getWidth();
		int height = ip.getHeight();
        Rectangle roi = ip.getRoi();
        ImageProcessor mask = ip.getMask();
		int size = width*height;
		if (slice==1) IJ.showStatus("Convolve (red)");
		byte[] r = new byte[size];
		byte[] g = new byte[size];
		byte[] b = new byte[size];
		((ColorProcessor)ip).getRGB(r,g,b);
		ImageProcessor rip = new ByteProcessor(width, height, r, null);
		ImageProcessor gip = new ByteProcessor(width, height, g, null);
		ImageProcessor bip = new ByteProcessor(width, height, b, null);
		Rectangle rect = ip.getRoi();
		ImageProcessor ip2 = rip.convertToFloat();
        ip2.setMask(mask); ip2.setRoi(roi); 
		convolveFloat(ip2, kernel, kw, kh);
		ImageProcessor r2 = ip2.convertToByte(false);
		if (slice==1) IJ.showStatus("Convolve (green)");
		ip2 = gip.convertToFloat();
        ip2.setMask(mask); ip2.setRoi(roi); 
		convolveFloat(ip2, kernel, kw, kh);
		ImageProcessor g2 = ip2.convertToByte(false);
		ip2 = bip.convertToFloat();
        ip2.setMask(mask); ip2.setRoi(roi); 
		if (slice==1) IJ.showStatus("Convolve (blue)");
		convolveFloat(ip2, kernel, kw, kh);
		ImageProcessor b2 = ip2.convertToByte(false);
		((ColorProcessor)ip).setRGB((byte[])r2.getPixels(), (byte[])g2.getPixels(), (byte[])b2.getPixels());
	}

	/** Convolves the image <code>ip</code> with a kernel of width 
		<code>kw</code> and height <code>kh</code>. Returns false if 
		the user cancels the operation by pressing 'Esc'. */
	public boolean convolveFloat(ImageProcessor ip, float[] kernel, int kw, int kh) {
		if (canceled) return false;
		int width = ip.getWidth();
		int height = ip.getHeight();
		Rectangle r = ip.getRoi();
		boolean isRoi = r.width!=width||r.height!=height;
		boolean nonRectRoi = ip.getMask()!=null;
		if (nonRectRoi)
			ip.snapshot();
		int x1 = r.x;
		int y1 = r.y;
		int x2 = x1 + r.width;
		int y2 = y1 + r.height;
		int uc = kw/2;    
		int vc = kh/2;
		float[] pixels = (float[])ip.getPixels();
		float[] pixels2 = (float[])ip.getPixelsCopy();
		//for (int i=0; i<width*height; i++)
		//	pixels[i] = 0f;

		double scale = 1.0;
		if (normalize) {
			double sum = 0.0;
			for (int i=0; i<kernel.length; i++)
				sum += kernel[i];
			if (sum!=0.0)
				scale = (float)(1.0/sum);
		}

 		int progress = Math.max((y2-y1)/25,1);
		double sum;
		int offset, i;
		boolean edgePixel;
		int xedge = width-uc;
		int yedge = height-vc;
		for(int y=y1; y<y2; y++) {
			if (y%progress==0) {
				IJ.showProgress((double)y/height);
				if (IJ.escapePressed()) {
					canceled=true; 
					IJ.beep(); 
   					IJ.showProgress(1.0);
					return false;
				}
			}
			for(int x=x1; x<x2; x++) {
				sum = 0.0;
				i = 0;
				edgePixel = y<vc || y>=yedge || x<uc || x>=xedge;
				for(int v=-vc; v <= vc; v++) {
					offset = x+(y+v)*width;
					for(int u = -uc; u <= uc; u++) {
						if (edgePixel)
   							sum += getPixel(x+u, y+v, pixels2, width, height)*kernel[i++];
     					else
 							sum += pixels2[offset+u]*kernel[i++];
        				}
		    	}
				pixels[x+y*width] = (float)(sum*scale);
			}
    	}
		if (nonRectRoi)
			ip.reset(ip.getMask());
   		IJ.showProgress(1.0);
   		return true;
   	 }

	private float getPixel(int x, int y, float[] pixels, int width, int height) {
		if (x<=0) x = 0;
		if (x>=width) x = width-1;
		if (y<=0) y = 0;
		if (y>=height) y = height-1;
		return pixels[x+y*width];
	}
	
	void save() {
		TextArea ta1 = gd.getTextArea1();
		ta1.selectAll();
		String text = ta1.getText();
		ta1.select(0, 0);
		if (text==null || text.length()==0)
			return;
		text += "\n";
		SaveDialog sd = new SaveDialog("Save as Text...", "kernel", ".txt");
		String name = sd.getFileName();
		if (name == null)
			return;
		String directory = sd.getDirectory();
		PrintWriter pw = null;
		try {
			FileOutputStream fos = new FileOutputStream(directory+name);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			pw = new PrintWriter(bos);
		}
		catch (IOException e) {
			IJ.error("" + e);
			return;
		}
		IJ.wait(250);  // give system time to redraw ImageJ window
		pw.print(text);
		pw.close();
	}
	
	void open() {
		OpenDialog od = new OpenDialog("Open Calibration...", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		String path = directory + name;
		TextReader tr = new TextReader();
		ImageProcessor ip = tr.open(path);
		if (ip==null)
			return;
		int width = ip.getWidth();
		int height = ip.getHeight();
		if ((width&1)!=1 || width!=height) {
			IJ.error("Convolver", "Kernel must be square and have an odd width");
			return;
		}
		StringBuffer sb = new StringBuffer();
		boolean integers = true;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				double v = ip.getPixelValue(x, y);
				if ((int)v!=v)
					integers = false;
			}
		}
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				if (x!=0) sb.append(" ");
				double v = ip.getPixelValue(x, y);
				if (integers)
					sb.append(IJ.d2s(ip.getPixelValue(x, y),0));
				else
					sb.append(""+ip.getPixelValue(x, y));
			}
			if (y!=height-1)
				sb.append("\n");
		}
		gd.getTextArea1().setText(new String(sb));
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==save)
			save();
		else if (source==open)
			open();
	}

}


