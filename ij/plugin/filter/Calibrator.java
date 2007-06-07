package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.util.*;
import java.awt.*;
import java.util.*;

/** Implements the Analyze/Calibrate command. */
public class Calibrator implements PlugInFilter, Measurements {

	private static final String NONE = "None";
	private static final String INVERTER = "Pixel Inverter";
	private static final String UNCALIBRATED_OD = "Uncalibrated OD";
	private static boolean showSettings;
	static boolean global;
	private static boolean oldGlobal;
    private ImagePlus imp;
	private int choiceIndex;
	private String[] functions;
	private	int nFits = CurveFitter.fitList.length;
	private int spacerIndex = nFits+1;
	private int inverterIndex = nFits+2;
	private int odIndex = nFits+3;
	private String xText;
	private static String yText="";
	private String unit;
	private double lx=0.02, ly=0.1;
	private int oldFunction;
	private String sumResiduals, fitGoodness;
	
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(Calibrator.class);
		return DOES_8G+DOES_8C+DOES_16+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		oldGlobal = global;
		if (!showDialog(imp))
			return;
		calibrate(imp);
		if (global || global!=oldGlobal) {
			int[] list = WindowManager.getIDList();
			if (list==null)
				return;
			for (int i=0; i<list.length; i++) {
				ImagePlus imp2 = WindowManager.getImage(list[i]);
				if (imp2!=null)
					imp2.getWindow().repaint();
			}
		} else
			imp.getWindow().repaint();
	}

	public boolean showDialog(ImagePlus imp) {
		String defaultChoice;
		Calibration cal = imp.getCalibration();
		functions = getFunctionList();
		int function = cal.getFunction();
		oldFunction = function;
		double[] p = cal.getCoefficients();
		unit = cal.getValueUnit();
		if (function==Calibration.NONE)
			defaultChoice=NONE;
		else if (function<nFits&&function==Calibration.STRAIGHT_LINE&&p!=null&& p[0]==255.0&&p[1]==-1.0)
			defaultChoice=INVERTER;
		else if (function<nFits)
			defaultChoice = CurveFitter.fitList[function];
		else if (function==Calibration.UNCALIBRATED_OD)
			defaultChoice=UNCALIBRATED_OD;
		else
			defaultChoice=NONE;
			
		xText = getMeans();	
		GenericDialog gd = new GenericDialog("Calibrate...");
		gd.addChoice("Function:", functions, defaultChoice);
		gd.addStringField("Unit:", unit, 16);
		gd.addTextAreas(xText, yText, 12, 10);
		gd.addCheckbox("Global Calibration", global);
		//gd.addCheckbox("Show Simplex Settings", showSettings);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		else {
			choiceIndex = gd.getNextChoiceIndex();
			unit = gd.getNextString();
			xText = gd.getNextText();
			yText = gd.getNextText();
			global = gd.getNextBoolean();
			//showSettings = gd.getNextBoolean();
			return true;
		}
	}

	public void calibrate(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		int function = Calibration.NONE;
		boolean is16Bits = imp.getType()==ImagePlus.GRAY16;
		double[] parameters = null;
		double[] x=null, y=null;
		if (choiceIndex<=0) {
			if (oldFunction==Calibration.NONE&&!yText.equals("")&&!xText.equals(""))
				IJ.showMessage("Calibrator", "Please select a function");
			function = Calibration.NONE;
		} else if (choiceIndex<=nFits) {
			function = choiceIndex - 1;
			if (function>0 && is16Bits) {
				IJ.error("Curve fitting currently not supported on 16-bit images.");
				return;
			}
			x = getData(xText);
			y = getData(yText);
			if (!cal.calibrated() || y.length!=0 || function!=oldFunction) {
				parameters = doCurveFitting(x, y, function);
				if (parameters==null)
					return;
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
				IJ.error("Uncalibrated OD is not supported on 16-bit images.");
				return;
			}
			function = Calibration.UNCALIBRATED_OD;
			unit = "Uncalibrated OD";
		}
		cal.setFunction(function, parameters, unit);
		if (global)
			imp.setGlobalCalibration(cal);
		else
			imp.setCalibration(cal);
		if (function!=Calibration.NONE)
			showPlot(x, y, cal, sumResiduals, fitGoodness);
		//IJ.write("cal: "+cal);
	}

	double[] doCurveFitting(double[] x, double[] y, int fitType) {
		if (x.length!=y.length || y.length==0) {
			IJ.showMessage("Calibrator",
				"To create a calibration curve, the left column must\n"+
				"contain a list of measured mean pixel values and the\n"+
				"right column must contain the same number of calibration\n"+
				"standard values. Use the Measure command to add mean\n"+
				"pixel value measurements to the left column.\n"
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
		//IJ.write("");
		//IJ.write("n: "+n);
		//IJ.write("iterations: "+cf.getIterations());
		//IJ.write("max iterations: "+cf.getMaxIterations());
		//IJ.write("function: "+cf.fList[fitType]);
		int np = cf.getNumParams();
		double[] p = cf.getParams();
		double sumResidualsSqr = p[np];
		//IJ.write("sum of residuals: "+IJ.d2s(Math.sqrt(sumResidualsSqr),6));
		double sumY = 0.0;
		for (int i=0; i<n; i++)
			sumY += y[i];
		sumResiduals = IJ.d2s(Math.sqrt(sumResidualsSqr/n),6);
		double mean = sumY/n;
		double sumMeanDiffSqr = 0.0;
		int degreesOfFreedom = n-np;
		double goodness=1.0;
		for (int i=0; i<n; i++) {
			sumMeanDiffSqr += sqr(y[i]-mean);
			if (sumMeanDiffSqr>0.0 && degreesOfFreedom!=0)
				goodness = 1.0-(sumResidualsSqr/degreesOfFreedom)*((n-1)/sumMeanDiffSqr);
		}
		fitGoodness = IJ.d2s(goodness,6);
		double[] parameters = new double[np];
		for (int i=0; i<np; i++)
			parameters[i] = p[i];
		return parameters;									
	}
	
	void showPlot(double[] x, double[] y, Calibration cal, String sumResiduals, String fitGoodness) {
		if (!cal.calibrated())
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
		PlotWindow pw = new PlotWindow("Calibration Function","pixel value",unit,px,py);
		pw.setLimits(xmin,xmax,ymin,ymax);
		if (x!=null&&y!=null&&x.length>0&&y.length>0)
			pw.addPoints(x, y, PlotWindow.CIRCLE);
		double[] p = cal.getCoefficients();
		if (fit<=Calibration.GAMMA_VARIATE) {
			drawLabel(pw, CurveFitter.fList[fit]);
			ly += 0.04;
		}
		if (p!=null) {
			int np = p.length;
			drawLabel(pw, "a="+IJ.d2s(p[0],6));
			drawLabel(pw, "b="+IJ.d2s(p[1],6));
			if (np>=3)
				drawLabel(pw, "c="+IJ.d2s(p[2],6));
			if (np>=4)
				drawLabel(pw, "d="+IJ.d2s(p[3],6));
			if (np>=5)
				drawLabel(pw, "e="+IJ.d2s(p[4],6));
			ly += 0.04;
		}
		if (sumResiduals!=null)
			{drawLabel(pw,"S.D.="+sumResiduals); sumResiduals=null;}
		if (fitGoodness!=null)
			{drawLabel(pw, "R^2="+fitGoodness); fitGoodness=null;}
		pw.draw();
	}

	void drawLabel(PlotWindow pw, String label) {
		pw.addLabel(lx, ly, label);
		ly += 0.08;
	}
	

	double sqr(double x) {return x*x;}

	String[] getFunctionList() {
		String[] list = new String[nFits+4];
		list[0] = NONE;
		for (int i=0; i<nFits; i++)
			list[1+i] = CurveFitter.fitList[i];
		list[spacerIndex] = "-";
		list[inverterIndex] = INVERTER;
		list[odIndex] = UNCALIBRATED_OD;
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
		return s;
	}

	double[] getData(String xData) {
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
		try {d = new Double(token);}
		catch (NumberFormatException e){d = null;}
		if (d!=null)
			return(d.doubleValue());
		else
			return 0.0;
	}

}