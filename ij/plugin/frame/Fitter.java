package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import ij.*;
import ij.plugin.*;
import ij.text.*;
import ij.measure.*;
import ij.gui.*;
import ij.util.*;
import ij.io.*;
import ij.process.*;

/** Does curve fitting using ImageJ's CurveFitter class. */
public class Fitter extends PlugInFrame implements PlugIn, ItemListener, ActionListener {

	Choice fit;
	Button doIt, open, apply;
	String fitTypeStr = CurveFitter.fitList[0];
	TextArea textArea;

	double[] dx = {0,1,2,3,4,5};
	double[] dy = {0,.9,4.5,8,18,24};
	double[] x,y;

	static CurveFitter cf;
	static int fitType;
	static double[] c;

	public Fitter() {
		super("Curve Fitter");
		Panel panel = new Panel();
		fit = new Choice();
		for (int i=0; i<CurveFitter.fitList.length; i++)
			fit.addItem(CurveFitter.fitList[i]);
		fit.addItemListener(this);
		panel.add(fit);
		doIt = new Button(" Fit ");
		doIt.addActionListener(this);
		panel.add(doIt);
		open = new Button("Open");
		open.addActionListener(this);
		panel.add(open);
		apply = new Button("Apply");
		apply.addActionListener(this);
		panel.add(apply);
		add("North", panel);
		String text = "";
		for (int i=0; i<dx.length; i++)
			text += IJ.d2s(dx[i],2)+"  "+IJ.d2s(dy[i],2)+"\n";
		textArea = new TextArea("",15,30,TextArea.SCROLLBARS_VERTICAL_ONLY);
		textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		textArea.append(text);
		add("Center", textArea);
		pack();
		GUI.center(this);
		setVisible(true);
		IJ.register(Fitter.class);
	}

	public void doFit(int fitType) {
		this.fitType = fitType;
		if (!getData())
			return;
		double[] a = Tools.getMinMax(x);
		double xmin=a[0], xmax=a[1]; 
		a = Tools.getMinMax(y);
		double ymin=a[0], ymax=a[1]; 
		cf = new CurveFitter(x, y);
		cf.doFit(fitType);
		IJ.write("");
		int n = x.length;
		IJ.write("n: "+n);
		IJ.write("iterations: "+cf.getIterations());
		IJ.write("max iterations: "+cf.getMaxIterations());
		IJ.write("function: "+CurveFitter.fList[fitType]);
		int nc = cf.nCoefficients();
		c = cf.getCoefficients();
		IJ.write("a: "+IJ.d2s(c[0],8));
		IJ.write("b: "+IJ.d2s(c[1],8));
		if (nc>=3)
			IJ.write("c: "+IJ.d2s(c[2],8));
		if (nc>=4)
			IJ.write("d: "+IJ.d2s(c[3],8));
		if (nc>=5)
			IJ.write("e: "+IJ.d2s(c[4],8));
		double sumResidualsSqr = c[nc];
		IJ.write("sum of residuals: "+IJ.d2s(Math.sqrt(sumResidualsSqr),6));
		double sumY = 0.0;
		for (int i=0; i<n; i++)
			sumY += y[i];
		double sd = Math.sqrt(sumResidualsSqr/n);
		double mean = sumY/n;
		double sumMeanDiffSqr = 0.0;
		int degreesOfFreedom = n-nc;
		double fitGoodness=1.0;
		for (int i=0; i<n; i++) {
			sumMeanDiffSqr += sqr(y[i]-mean);
			if (sumMeanDiffSqr>0.0 && degreesOfFreedom!=0)
				fitGoodness = 1.0-(sumResidualsSqr/degreesOfFreedom)*((n-1)/sumMeanDiffSqr);
		}
		IJ.write("S.D.: "+IJ.d2s(sd,6));
		IJ.write("R^: "+IJ.d2s(fitGoodness,6));

		float[] px = new float[100];
		float[] py = new float[100];
		double inc = (xmax-xmin)/99.0;
		double tmp = xmin;
		for (int i=0; i<100; i++) {
			px[i]=(float)tmp;
			tmp += inc;
		}
		for (int i=0; i<100; i++)
			py[i] = (float)CurveFitter.f(fitType, c, px[i]);
		a = Tools.getMinMax(py);
		ymin = Math.min(ymin, a[0]);
		ymax = Math.max(ymax, a[1]);
		PlotWindow pw = new PlotWindow(cf.fList[fitType],"X","Y",px,py);
		pw.setLimits(xmin,xmax,ymin,ymax);
		pw.addPoints(x, y, PlotWindow.CIRCLE);
		//pw.addLabel(0.02, 0.1, cf.fList[fitType]);
		pw.draw();									
	}
	
	double sqr(double x) {return x*x;}
	
	boolean getData() {
		textArea.selectAll();
		String text = textArea.getText();
		textArea.select(0,0);
		StringTokenizer st = new StringTokenizer(text);
		int nTokens = st.countTokens();
		if (nTokens<4 || (nTokens%2)!=0)
			return false;
		int n = nTokens/2;
		x = new double[n];
		y = new double[n];
		for (int i=0; i<n; i++) {
			x[i] = getNum(st);
			y[i] = getNum(st);
		}
		return true;
	}
	
	void applyFunction() {
		if (cf==null) {
			IJ.error("No function available");
			return;
		}
		ImagePlus img = WindowManager.getCurrentImage();
		if (img==null) {
			IJ.noImage();
			return;
		}
		if (img.getTitle().startsWith("y=")) {
			IJ.error("First select the image to be transformed");
			return;
		}
		int width = img.getWidth();
		int height = img.getHeight();
		int size = width*height;
		float[] data = new float[size];
		ImageProcessor ip = img.getProcessor();
		float value;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				value = ip.getPixelValue(x,y);
				data[y*width+x] = (float)CurveFitter.f(fitType, c, value);
			}
		}
		ImageProcessor ip2 = new FloatProcessor(width, height, data, ip.getColorModel());
		new ImagePlus(img.getTitle()+"-transformed", ip2).show();
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

	void open() {
		OpenDialog od = new OpenDialog("Open Text File...", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		String path = directory + name;
		textArea.selectAll();
		textArea.setText("");
		try {
			BufferedReader r = new BufferedReader(new FileReader(directory+name));
			while (true) {
				String s=r.readLine();
				if (s==null) break;
				if (s.length()>50) break;
				textArea.append(s+"\n");
			}
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return;
		}
	}
	
	public void itemStateChanged(ItemEvent e) {
		fitTypeStr = fit.getSelectedItem();
	}
	
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==doIt)
			doFit(fit.getSelectedIndex());
		else if (e.getSource()==apply)
			applyFunction();
		else
			open();
		//if(e.getSource()==doIt) {
		//	try {doFit(fit.getSelectedIndex());}
		//	catch (Exception ex) {IJ.write(ex.getMessage());}
		//}
	}

}



