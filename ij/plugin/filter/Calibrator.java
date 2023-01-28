package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.util.*;
import ij.io.*;
import ij.plugin.TextReader;
import ij.plugin.frame.Fitter;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.io.*;


/** Implements the Analyze/Calibrate command. */
public class Calibrator implements PlugInFilter, Measurements, ActionListener {

	private static final String NONE = "None";
	private static final String INVERTER = "Pixel Inverter";
	private static final String UNCALIBRATED_OD = "Uncalibrated OD";
	private static final String CUSTOM = "Custom";
	private static boolean showSettings;
	private boolean global1, global2;
    private ImagePlus imp;
	private int choiceIndex;
	private String[] functions;
	private	int nFits = Calibration.EXP_RECOVERY+1;   //don't set to CurveFitter.fitList.length; Calibration can't cope with it
	private String curveFitError;
	private int spacerIndex = nFits+1;
	private int inverterIndex = nFits+2;
	private int odIndex = nFits+3;
	private int customIndex = nFits+4;
	private static String xText = "";
	private static String yText = "";
	private static boolean importedValues;
	private String unit;
	private double lx=0.02, ly=0.1;
	private int oldFunction;
	private String sumResiduals, fitGoodness;
	private Button open, save;
	private GenericDialog gd;
	private static boolean showPlotFlagSaved = true;
	private boolean showPlotFlag;
	private static String unitSaved = Calibration.DEFAULT_VALUE_UNIT;
	private CurveFitter curveFitter;
	
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL-DOES_RGB+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		global1 = imp.getGlobalCalibration()!=null;
		if (!showDialog(imp))
			return;
		if (choiceIndex==customIndex) {
			showPlot(null, null, imp.getCalibration(), null);
			return;
		} else if (imp.getType()==ImagePlus.GRAY32) {
			if (choiceIndex==0)
				imp.getCalibration().setValueUnit(unit);
			else
				IJ.error("Calibrate", "Function must be \"None\" for 32-bit images,\nbut you can change the Unit.");
		} else
			calibrate(imp);
	}

	public boolean showDialog(ImagePlus imp) {
		String defaultChoice;
		Calibration cal = imp.getCalibration();
		functions = getFunctionList(cal.getFunction()==Calibration.CUSTOM);
		int function = cal.getFunction();
		oldFunction = function;
		double[] p = cal.getCoefficients();
		unit = cal.getValueUnit();
		if (unit == Calibration.DEFAULT_VALUE_UNIT)
		    unit = unitSaved;
		if (function==Calibration.NONE)
			defaultChoice=NONE;
		else if (function<nFits&&function==Calibration.STRAIGHT_LINE&&p!=null&& p[0]==255.0&&p[1]==-1.0)
			defaultChoice=INVERTER;
		else if (function<nFits)
			defaultChoice = CurveFitter.fitList[function];
		else if (function==Calibration.UNCALIBRATED_OD)
			defaultChoice=UNCALIBRATED_OD;
		else if (function==Calibration.CUSTOM)
			defaultChoice=CUSTOM;
		else
			defaultChoice=NONE;
			
		String tmpText = getMeans();
		if (!importedValues && !tmpText.equals(""))	
			xText = tmpText;	
		gd = new GenericDialog("Calibrate...");
		gd.addChoice("Function:", functions, defaultChoice);
		gd.addStringField("Unit:", unit, 16);
		gd.addTextAreas(xText, yText, 20, 14);
		//gd.addMessage("Left column contains uncalibrated measured values,\n right column contains known values (e.g., OD).");
		gd.addPanel(makeButtonPanel(gd));
		gd.addCheckbox("Global calibration", IJ.isMacro()?false:global1);
		gd.addCheckbox("Show plot", IJ.isMacro()?false:showPlotFlagSaved);
		//gd.addCheckbox("Show Simplex Settings", showSettings);
		gd.addHelp(IJ.URL2+"/docs/menus/analyze.html#cal");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		else {
			choiceIndex = gd.getNextChoiceIndex();
			unit = gd.getNextString();
			xText = gd.getNextText();
			yText = gd.getNextText();
			global2 = gd.getNextBoolean();
			showPlotFlag = gd.getNextBoolean();
			//showSettings = gd.getNextBoolean();
			showPlotFlagSaved = showPlotFlag;
			unitSaved = unit;
			return true;
		}
	}

	/** Creates a panel containing "Open..." and "Save..." buttons. */
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

    /** Calibrate an image with the function type defined previously.
     *  Sets the function to Calibration.NONE on error */
	public void calibrate(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		Calibration calOrig = cal.copy();
		int function = Calibration.NONE;
		boolean is16Bits = imp.getType()==ImagePlus.GRAY16;
		double[] parameters = null;
		double[] x=null, y=null;
		boolean zeroClip=false;
		curveFitter = null;
		if (choiceIndex<=0) {
			if (oldFunction==Calibration.NONE&&!yText.equals("")&&!xText.equals("")) {
				IJ.error("Calibrate", "Please select a function");
			    return;
			}
			function = Calibration.NONE;
		} else if (choiceIndex<=nFits) {
			function = choiceIndex - 1;
			x = getData(xText);
			y = getData(yText);
			if (cal.isSigned16Bit() || imp.getProperty("WasSigned")!=null) {
				for (int i=0; i<x.length; i++)
					x[i] += 32768;
				imp.setProperty("WasSigned", "WasSigned");
			}
			if (!validateXValues(imp, x))
				return;
			if (!cal.calibrated() || y.length!=0 || function!=oldFunction) {
				parameters = doCurveFitting(x, y, function);
				if (parameters==null) { //minimization failed
				    IJ.error(curveFitError);
				    function = Calibration.NONE;
					return;
				}
			}
			if (!is16Bits && function!=Calibration.STRAIGHT_LINE) {
				zeroClip = true;
				for (int i=0; i<y.length; i++)
					if (y[i]<0.0) zeroClip = false;
			}
		} else if (choiceIndex==inverterIndex) {
			function = Calibration.STRAIGHT_LINE;
			parameters = new double[2];
			if (is16Bits)
				parameters[0] = 65535;
			else
				parameters[0] = 255;
			parameters[1] = -1.0;
			unit = "Inverted Gray Value";
		} else if (choiceIndex==odIndex) {
			if (is16Bits) {
				IJ.error("Calibrate", "Uncalibrated OD is not supported on 16-bit images.");
				return;
			}
			function = Calibration.UNCALIBRATED_OD;
			unit = "Uncalibrated OD";
		}
		cal.setFunction(function, parameters, unit, zeroClip);
		if (function==Calibration.NONE)
			cal.setValueUnit(unit);
		if (!cal.equals(calOrig))
			imp.setCalibration(cal);
		int bitDepth = imp.getBitDepth();
		imp.setGlobalCalibration(global2?cal:null);
		if (function!=Calibration.NONE && bitDepth!=8 && imp.getNChannels()==1 && !(bitDepth==16&&imp.getDefault16bitRange()>0)) {
			ImageStatistics stats = imp.getProcessor().getStats();
			if (imp.getDisplayRangeMin()<stats.min || imp.getDisplayRangeMax()>stats.max) {
				imp.resetDisplayRange();
				imp.updateAndDraw();
			}
		}
		if (global2 || global2!=global1)
			WindowManager.repaintImageWindows();
		else
			imp.repaintWindow();
		if (global2 && global2!=global1)
			FileOpener.setShowConflictMessage(true);
		if (function!=Calibration.NONE && showPlotFlag) {
			if (curveFitter!=null)
				Fitter.plot(curveFitter, bitDepth==8);
			else
				showPlot(x, y, cal, fitGoodness);
		}
	}
	
	private boolean validateXValues(ImagePlus imp, double[] x) {
		int bitDepth = imp.getBitDepth();
		if (bitDepth==32 || x==null)
			return true;
		int max = 255;
		if (bitDepth==16)
			max = 65535;
		for (int i=0; i<x.length; i++) {
			if (x[i]<0 || x[i]>max) {
			    String title = (bitDepth==8?"8-bit":"16-bit") + " Calibration";
				String msg = "Measured (uncalibrated) values in the left\ncolumn must be in the range 0-";
				IJ.error(title, msg+max+".");
				return false;
			}
		}
		return true;
	}

	double[] doCurveFitting(double[] x, double[] y, int fitType) {
		if (x.length!=y.length || y.length==0) {
			IJ.error("Calibrate",
				"To create a calibration curve, the left column must\n"
				+"contain a list of measured mean pixel values and the\n"
				+"right column must contain the same number of calibration\n"
				+"standard values. Use the Measure command to add mean\n"
				+"pixel value measurements to the left column.\n"
				+" \n"
				+"    Left column: "+x.length+" values\n"
				+"    Right column: "+y.length+" values\n"
				);
			return null;
		}
		int n = x.length;
		double xmin=0.0,xmax;
		if (imp.getType()==ImagePlus.GRAY16)
			xmax=65535.0; 
		else
			xmax=255.0;
		double[] a = Tools.getMinMax(y);
		double ymin=a[0], ymax=a[1]; 
		CurveFitter cf = new CurveFitter(x, y);
		cf.doFit(fitType, showSettings);
		if (cf.getStatus() == Minimizer.INITIALIZATION_FAILURE) {
		    curveFitError = cf.getStatusString();
		    return null;
		}
        if (IJ.debugMode) IJ.log(cf.getResultString());
		int np = cf.getNumParams();
		double[] p = cf.getParams();
		fitGoodness = IJ.d2s(cf.getRSquared(),6);
		curveFitter = cf;
		double[] parameters = new double[np];
		for (int i=0; i<np; i++)
			parameters[i] = p[i];
		return parameters;									
	}
	
	void showPlot(double[] x, double[] y, Calibration cal, String rSquared) {
		if (!showPlotFlag || !cal.calibrated())
			return;
		int xmin,xmax,range;
		float[] ctable = cal.getCTable();
		if (ctable.length==256) { //8-bit image
			xmin = 0;
			xmax = 255;
		} else {  // 16-bit image
			xmin = 0;
			xmax = 65535;
		}
		range = 256;
		float[] px = new float[range];
		float[] py = new float[range];
		for (int i=0; i<range; i++)
			px[i]=(float)((i/255.0)*xmax);
		for (int i=0; i<range; i++)
			py[i]=ctable[(int)px[i]];
		double[] a = Tools.getMinMax(py);
		double ymin = a[0];
		double ymax = a[1];
		int fit = cal.getFunction();
		String unit = cal.getValueUnit();
		Plot plot = new Plot("Calibration Function","pixel value",unit,px,py);
		plot.setLimits(xmin,xmax,ymin,ymax);
		if (x!=null&&y!=null&&x.length>0&&y.length>0)
			plot.addPoints(x, y, PlotWindow.CIRCLE);
		double[] p = cal.getCoefficients();
		if (fit<=Calibration.LOG2) {
			drawLabel(plot, CurveFitter.fList[fit]);
			ly += 0.04;
		}
		if (p!=null) {
			int np = p.length;
			drawLabel(plot, "a="+IJ.d2s(p[0],6,10));
			drawLabel(plot, "b="+IJ.d2s(p[1],6,10));
			if (np>=3)
				drawLabel(plot, "c="+IJ.d2s(p[2],6,10));
			if (np>=4)
				drawLabel(plot, "d="+IJ.d2s(p[3],6,10));
			if (np>=5)
				drawLabel(plot, "e="+IJ.d2s(p[4],6,10));
			ly += 0.04;
		}
		if (rSquared!=null)
			{drawLabel(plot, "R^2="+rSquared); rSquared=null;}
		plot.show();
	}
	
	void drawLabel(Plot plot, String label) {
		plot.addLabel(lx, ly, label);
		ly += 0.08;
	}
	

	double sqr(double x) {return x*x;}

	String[] getFunctionList(boolean custom) {
		int n = nFits+4;
		if (custom) n++;
		String[] list = new String[n];
		list[0] = NONE;
		for (int i=0; i<nFits; i++)
			list[1+i] = CurveFitter.fitList[i];
		list[spacerIndex] = "-";
		list[inverterIndex] = INVERTER;
		list[odIndex] = UNCALIBRATED_OD;
		if (custom) 
			list[customIndex] = CUSTOM;
		return list;
 	}
	
	String getMeans() {
		float[] umeans = Analyzer.getUMeans();
		int count = Analyzer.getCounter();
		if (umeans==null || count==0)
			return "";
		if (count>MAX_STANDARDS)
			count = MAX_STANDARDS;
		String s = "";
		for (int i=0; i<count; i++)
			s += IJ.d2s(umeans[i],2)+"\n";
		importedValues = false;
		return s;
	}

	double[] getData(String xData) {
		int len = xData.length();
		StringBuffer sb = new StringBuffer(len);
		for (int i=0; i<len; i++) {
			char c = xData.charAt(i);
			if ((c>='0'&&c<='9') || c=='-'  || c=='.' || c==',' || c=='\n' || c=='\r' || c==' ')
				sb.append(c);
		}
		xData = sb.toString();

		StringTokenizer st = new StringTokenizer(xData);
		int nTokens = st.countTokens();
		if (nTokens<1)
			return new double[0];
		int n = nTokens;
		double data[] = new double[n];
		for (int i=0; i<n; i++) {
			data[i] = getNum(st);
		}
		return data;
	}
	
	double getNum(StringTokenizer st) {
		Double d;
		String token = st.nextToken();
		try {d = Double.valueOf(token);}
		catch (NumberFormatException e){d = null;}
		if (d!=null)
			return(d.doubleValue());
		else
			return 0.0;
	}
	
	void save() {
		TextArea ta1 = gd.getTextArea1();
		TextArea ta2 = gd.getTextArea2();
		ta1.selectAll();
		String text1 = ta1.getText();
		ta1.select(0, 0);
		ta2.selectAll();
		String text2 = ta2.getText();
		ta2.select(0, 0);
		double[] x = getData(text1);
		double[] y = getData(text2);
		SaveDialog sd = new SaveDialog("Save as Text...", "calibration", ".txt");
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
		int n = Math.max(x.length, y.length);
		for (int i=0; i<n; i++) {
			String xs = x.length==0?"":i<x.length?""+x[i]:"0";
			String ys = y.length==0?"":i<y.length?""+y[i]:"0";
			pw.println(xs + "\t"+ ys);
		}
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
		if (!((width==1||width==2)&&height>1)) {
			IJ.error("Calibrate", "This appears to not be a one or two column text file");
			return;
		}
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<height; i++) {
			sb.append(""+ip.getPixelValue(0, i));
			sb.append("\n");
		}
		String s1=null, s2=null;
		if (width==2) {
			s1 = new String(sb);
			sb = new StringBuffer();
			for (int i=0; i<height; i++) {
				sb.append(""+ip.getPixelValue(1, i));
				sb.append("\n");
			}
			s2 = new String(sb);
		} else
			s2 = new String(sb);
		if (s1!=null) {
			TextArea ta1 = gd.getTextArea1();
			ta1.selectAll();
			ta1.setText(s1);
		}
		TextArea ta2 = gd.getTextArea2();
		ta2.selectAll();
		ta2.setText(s2);
		importedValues = true;
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source==save)
			save();
		else if (source==open)
			open();
	}
	
}
