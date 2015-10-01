package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import java.awt.datatransfer.*;	
import ij.*;
import ij.plugin.PlugIn;
import ij.plugin.frame.*;
import ij.text.*;
import ij.gui.*;
import ij.util.*;
import ij.io.*;
import ij.process.*;
import ij.measure.*;

/** ImageJ plugin that does curve fitting using the modified CurveFitter class.
 *  Includes simplex settings dialog option.
 *
 * @author  Kieran Holland (email: holki659@student.otago.ac.nz)
 *
 * 2013-10-01: fit not in EventQueue, setStatusAndEsc, error if nonnumeric data
 */
public class Fitter extends PlugInFrame implements PlugIn, ItemListener, ActionListener, KeyListener, ClipboardOwner {

	Choice fit;
	Button doIt, open, apply;
	Checkbox settings;
	String fitTypeStr = CurveFitter.fitList[0];
	TextArea textArea;

	double[] dx = {0,1,2,3,4,5};
	double[] dy = {0,.9,4.5,8,18,24};
	double[] x,y;

	static CurveFitter cf;
	static int fitType = -1;
	static String equation = "y = a + b*x + c*x*x";
	static final int USER_DEFINED = -1;

	public Fitter() {
		super("Curve Fitter");
		WindowManager.addWindow(this);
		addKeyListener(this);
		Panel panel = new Panel();
		fit = new Choice();
		for (int i=0; i<CurveFitter.fitList.length; i++)
			fit.addItem(CurveFitter.fitList[CurveFitter.sortedTypes[i]]);
		fit.addItem("*User-defined*");
		fit.addItemListener(this);
		panel.add(fit);
		doIt = new Button(" Fit ");
		doIt.addActionListener(this);
		doIt.addKeyListener(this);
		panel.add(doIt);
		open = new Button("Open");
		open.addActionListener(this);
		panel.add(open);
		apply = new Button("Apply");
		apply.addActionListener(this);
		panel.add(apply);
		settings = new Checkbox("Show settings", false);
		panel.add(settings);
		add("North", panel);
		String text = "";
		for (int i=0; i<dx.length; i++)
			text += IJ.d2s(dx[i],2)+"  "+IJ.d2s(dy[i],2)+"\n";
		textArea = new TextArea("",15,30,TextArea.SCROLLBARS_VERTICAL_ONLY);
		//textArea.setBackground(Color.white);
		textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		if (IJ.isLinux()) textArea.setBackground(Color.white);
		textArea.append(text);
		add("Center", textArea);
		pack();
		GUI.center(this);
		show();
		IJ.register(Fitter.class);
	}

    /** Fit data in the textArea, show result in log and create plot.
     *  @param fitType as defined in CurveFitter constants
     *  @return false on error.
     */
	public boolean doFit(int fitType) {
		if (!getData()) {
            IJ.beep();
			return false;
		}
		cf = new CurveFitter(x, y);
		cf.setStatusAndEsc("Optimization: Iteration ", true);
		try {
            if (fitType==USER_DEFINED) {
                String eqn = getEquation();
                if (eqn==null) return false;
                int params = cf.doCustomFit(eqn, null, settings.getState());
                if (params==0) {
                    IJ.beep();
                    IJ.log("Bad formula; should be:\n   y = function(x, a, ...)");
                    return false;
                }
            } else
                cf.doFit(fitType, settings.getState());
            if (cf.getStatus() == Minimizer.INITIALIZATION_FAILURE) {
                IJ.beep();
                IJ.showStatus(cf.getStatusString());
                IJ.log("Curve Fitting Error:\n"+cf.getStatusString());
                return false;
            }
            if (Double.isNaN(cf.getSumResidualsSqr())) {
                IJ.beep();
                IJ.showStatus("Error: fit yields Not-a-Number");
                return false;
            }
            
		} catch (Exception e) {
            IJ.handleException(e);
            return false;
		}
        IJ.log(cf.getResultString());
		plot(cf);
		this.fitType = fitType; 
		return true;
	}
	
	String getEquation() {
		GenericDialog gd = new GenericDialog("Formula");
		gd.addStringField("Formula:", equation, 38);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		equation = gd.getNextString();
		return equation;
	}
	
	public static void plot(CurveFitter cf) {
		plot(cf, false);
	}
	
	public static void plot(CurveFitter cf, boolean eightBitCalibrationPlot) {
		double[] x = cf.getXPoints();
		double[] y = cf.getYPoints();
		if (cf.getParams().length<cf.getNumParams()) {
			Plot plot = new Plot(cf.getFormula(),"X","Y",x,y);
			plot.setColor(Color.BLUE);
			plot.addLabel(0.02, 0.1, cf.getName());
			plot.addLabel(0.02, 0.2, cf.getStatusString());
			plot.show();
			return;
		}
		int npoints = 100;
		if (npoints<x.length)
			npoints = x.length; //or 2*x.length-1; for 2 values per data point
		if (npoints>1000)
			npoints = 1000;
		double[] a = Tools.getMinMax(x);
		double xmin=a[0], xmax=a[1];
		if (eightBitCalibrationPlot) {
			npoints = 256;
			xmin = 0;
			xmax = 255;
		}
		a = Tools.getMinMax(y);
		double ymin=a[0], ymax=a[1]; //y range of data points
		float[] px = new float[npoints];
		float[] py = new float[npoints];
		double inc = (xmax-xmin)/(npoints-1);
		double tmp = xmin;
		for (int i=0; i<npoints; i++) {
			px[i]=(float)tmp;
			tmp += inc;
		}
		double[] params = cf.getParams();
		for (int i=0; i<npoints; i++)
			py[i] = (float)cf.f(params, px[i]);
		a = Tools.getMinMax(py);
		double dataRange = ymax - ymin;
		ymin = Math.max(ymin - dataRange, Math.min(ymin, a[0])); //expand y range for curve, but not too much
		ymax = Math.min(ymax + dataRange, Math.max(ymax, a[1]));
		Plot plot = new Plot(cf.getFormula(),"X","Y",px,py);
		plot.setLimits(xmin, xmax, ymin, ymax);
		plot.setColor(Color.RED);
		plot.addPoints(x, y, PlotWindow.CIRCLE);
		plot.setColor(Color.BLUE);

		StringBuffer legend = new StringBuffer(100);
		legend.append(cf.getName()); legend.append('\n');
		legend.append(cf.getFormula()); legend.append('\n');
        double[] p = cf.getParams();
        int n = cf.getNumParams();
        char pChar = 'a';
        for (int i = 0; i < n; i++) {
			legend.append(pChar+" = "+IJ.d2s(p[i],5,9)+'\n');
			pChar++;
        }
		legend.append("R^2 = "+IJ.d2s(cf.getRSquared(),4)); legend.append('\n');
		plot.addLabel(0.02, 0.1, legend.toString());
		plot.setColor(Color.BLUE);
		plot.show();									
	}
	
	double sqr(double x) {return x*x;}
	
	boolean getData() {
		textArea.selectAll();
		String text = textArea.getText();
		text = zapGremlins(text);
		textArea.select(0,0);
		StringTokenizer st = new StringTokenizer(text, " \t\n\r,");
		int nTokens = st.countTokens();
		if (nTokens<4 || (nTokens%2)!=0) {
		    IJ.showStatus("Data error: min. two (x,y) pairs needed");
			return false;
		}
		int n = nTokens/2;
		x = new double[n];
		y = new double[n];
		for (int i=0; i<n; i++) {
			String xString = st.nextToken();
			String yString = st.nextToken();
			x[i] = Tools.parseDouble(xString);
			y[i] = Tools.parseDouble(yString);
			if (Double.isNaN(x[i]) || Double.isNaN(y[i])) {
				IJ.showStatus("Data error:  Bad number at "+i+": "+xString+" "+yString);
				return false;
			}
		}
		return true;
	}

	/** create a duplicate of an image where the fit function is applied to the pixel values */
	void applyFunction() {
		if (cf==null || fitType < 0) {
			IJ.error("No function available");
			return;
		}
		ImagePlus img = WindowManager.getCurrentImage();
		if (img==null) {
			IJ.noImage();
			return;
		}
		if (img.getTitle().matches("y\\s=.*")) { //title looks like a fit function
			IJ.error("First select the image to be transformed");
			return;
		}
		double[] p = cf.getParams();
		int width = img.getWidth();
		int height = img.getHeight();
		int size = width*height;
		float[] data = new float[size];
		ImageProcessor ip = img.getProcessor();
		float value;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				value = ip.getPixelValue(x,y);
				data[y*width+x] = (float)cf.f(p, value);
			}
		}
		ImageProcessor ip2 = new FloatProcessor(width, height, data, ip.getColorModel());
		new ImagePlus(img.getTitle()+"-transformed", ip2).show();
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
				if (s==null ||(s.length()>100))
					break;
				textArea.append(s+"\n");
			}
			r.close();
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
		if (e.getSource() instanceof MenuItem) {
			String cmd = e.getActionCommand();
			if (cmd==null) return;
			if (cmd.equals("Cut"))
				cut();
			else if (cmd.equals("Copy"))
				copy();
			else if (cmd.equals("Paste"))
				paste();
			return;
		}
		try {
            if (e.getSource()==doIt) {
                final int fitType = CurveFitter.getFitCode(fit.getSelectedItem());
	            Thread thread = new Thread(
	                new Runnable() {
                        final public void run() {
                            doFit(fitType);
                        }
                    }, "CurveFitting"
                );
                thread.setPriority(Thread.currentThread().getPriority());
                thread.start();
            } else if (e.getSource()==apply)
                applyFunction();
            else
                open();
		} catch (Exception ex) {IJ.log(""+ex);}
	}
	
	String zapGremlins(String text) {
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=0;
		for (int i=0; i<chars.length; i++) {
			char c = chars[i];
			if (c!='\n' && c!='\t' && (c<32||c>127)) {
				count++;
				chars[i] = ' ';
			}
		}
		if (count>0)
			return new String(chars);
		else
			return text;
	}

    public void keyTyped (KeyEvent e) {}
    public void keyReleased (KeyEvent e) {}
    public void keyPressed (KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
            IJ.getInstance().keyPressed(e);
    }
    
	private boolean copy() { 
		String s = textArea.getSelectedText();
		Clipboard clip = getToolkit().getSystemClipboard();
		if (clip!=null) {
			StringSelection cont = new StringSelection(s);
			clip.setContents(cont,this);
			return true;
		} else
			return false;
	}
 
	  
	private void cut() {
		if (copy()) {
			int start = textArea.getSelectionStart();
			int end = textArea.getSelectionEnd();
			textArea.replaceRange("", start, end);
		}	
	}

	private void paste() {
		String s;
		s = textArea.getSelectedText();
		Clipboard clipboard = getToolkit( ). getSystemClipboard(); 
		Transferable clipData = clipboard.getContents(s);
		try {
			s = (String)(clipData.getTransferData(DataFlavor.stringFlavor));
		} catch  (Exception e)  {
			s  = e.toString( );
		}
		int start = textArea.getSelectionStart( );
		int end = textArea.getSelectionEnd( );
		textArea.replaceRange(s, start, end);
		if (IJ.isMacOSX())
			textArea.setCaretPosition(start+s.length());
    }
    
	public void lostOwnership (Clipboard clip, Transferable cont) {}

}