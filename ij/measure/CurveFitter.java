package ij.measure;
import ij.*;
import ij.gui.*;
import ij.macro.*;
import ij.util.Tools;
import ij.util.IJMath;
import java.util.Arrays;
import java.util.Hashtable;

/** Curve fitting class based on the Simplex method in the Minimizer class
 *
 *
 *	Notes on fitting polynomial functions:
 *	(i) The range of x values should not be too far from 0, especially for higher-order polynomials.
 *	For polynomials of 4th order, the average x value should fulfill |xMean| < 2*(xMax-xMin).
 *	For polynomials of 5th order or higher, the x range should encompass x=0; and for
 *	7th and 8th order it is desirable to have x=0 near the center of the x range.
 *	(ii) In contrast to some fitting algorithms using the normal equations and matrix inversion, the
 *	simplex algorithm used here can cope with parameters having very different orders of magnitude,
 *	as long as the coefficients of the polynomial are within a reasonable range (say, 1e-80 to 1e80).
 *	Thus, it is usually not needed to scale the x values, even for high-order polynomials.
 *
 *	Version history:
 *
 *	2008-01-21: Modified to do Gaussian fitting by Stefan Woerz (s.woerz at dkfz.de).
 *	2012-01-30: Modified for external Minimizer class and UserFunction fit by Michael Schmid.
 *			  - Also fixed incorrect equation string for 'Gamma Variate' & 'Rodbard (NIH Image)',
 *			  - Added 'Inverse Rodbard', 'Exponential (linear regression)', 'Power (linear regression)'
 *				functions and polynomials of order 5-8. Added 'nicely' sorted list of types.
 *			  - Added absolute error for minimizer to avoid endless minimization if exact fit is possible.
 *			  - Added 'initialParamVariations' (order of magnitude if parameter variations) -
 *				this is important for safer and better convergence.
 *			  - Linear regression for up to 2 linear parameters, reduces the number of parameters handled
 *				by the simplex Minimizer and improves convergence.	These parameters can be an offset and
 *				either a linear slope or a factor that the full function is multiplied with.
 *	2012-10-07: added GAUSSIAN_NOOFFSET fit type
 *	2012-11-20: Bugfix: exception on Gaussian&Rodbard with initial params, bad initial params for Gaussian
 *  2013-09-24: Added "Exponential Recovery (no offset)" and "Chapman-Richards" (3-parameter;
 *              used e.g. to describe forest growth) fit types.
 *  2013-10-11: bugfixes, added setStatusAndEsc to show iterations and enable abort by ESC
 *  2015-03-26: bugfix, did not use linear regression for RODBARD
 *  2016-11-28: added static getNumParams methods
 *  2018-03-23: fixes NullPointerException for custom fit without initialParamVariations
 *  2018-07-19: added error function erf (=integral over Gaussian)
 */

public class CurveFitter implements UserFunction{
	/** Constants for the built-in fit functions */
	public static final int STRAIGHT_LINE=0, POLY2=1, POLY3=2, POLY4=3,
	EXPONENTIAL=4, POWER=5, LOG=6, RODBARD=7, GAMMA_VARIATE=8, LOG2=9,
	RODBARD2=10, EXP_WITH_OFFSET=11, GAUSSIAN=12, EXP_RECOVERY=13,
	INV_RODBARD=14, EXP_REGRESSION=15, POWER_REGRESSION=16,
	POLY5=17, POLY6=18, POLY7=19, POLY8=20,
	GAUSSIAN_NOOFFSET=21, EXP_RECOVERY_NOOFFSET=22, CHAPMAN=23, ERF=24;

	/** Nicer sequence of the built-in function types */
	public static final int[] sortedTypes = { STRAIGHT_LINE, POLY2, POLY3, POLY4,
			POLY5, POLY6, POLY7, POLY8,
			POWER, POWER_REGRESSION,
			EXPONENTIAL, EXP_REGRESSION, EXP_WITH_OFFSET,
			EXP_RECOVERY, EXP_RECOVERY_NOOFFSET,
			LOG, LOG2,
			GAUSSIAN, GAUSSIAN_NOOFFSET, ERF,
			RODBARD, RODBARD2, INV_RODBARD,
			GAMMA_VARIATE,CHAPMAN
	};

	/** Names of the built-in fit functions */
	public static final String[] fitList = {"Straight Line","2nd Degree Polynomial",
	"3rd Degree Polynomial", "4th Degree Polynomial","Exponential","Power",
	"Log","Rodbard", "Gamma Variate", "y = a+b*ln(x-c)","Rodbard (NIH Image)",
	"Exponential with Offset","Gaussian", "Exponential Recovery",
	"Inverse Rodbard", "Exponential (linear regression)", "Power (linear regression)",
	"5th Degree Polynomial","6th Degree Polynomial","7th Degree Polynomial","8th Degree Polynomial",
	"Gaussian (no offset)", "Exponential Recovery (no offset)",
	"Chapman-Richards", "Error Function"
	}; // fList, doFit(), getNumParams() and makeInitialParamsAndVariations() must also be updated

	/** Equations of the built-in fit functions */
	public static final String[] fList = {
	"y = a+bx","y = a+bx+cx^2",									//STRAIGHT_LINE,POLY2
	"y = a+bx+cx^2+dx^3","y = a+bx+cx^2+dx^3+ex^4",
	"y = a*exp(bx)","y = a*x^b", "y = a*ln(bx)",				//EXPONENTIAL,POWER,LOG
	"y = d+(a-d)/(1+(x/c)^b)", "y = b*(x-a)^c*exp(-(x-a)/d)",	//RODBARD,GAMMA_VARIATE
	"y = a+b*ln(x-c)", "x = d+(a-d)/(1+(y/c)^b) [y = c*((x-a)/(d-x))^(1/b)]",  //LOG2,RODBARD2
	"y = a*exp(-bx) + c", "y = a + (b-a)*exp(-(x-c)*(x-c)/(2*d*d))", //EXP_WITH_OFFSET,GAUSSIAN
	"y = a*(1-exp(-b*x)) + c", "y = c*((x-a)/(d-x))^(1/b)",		//EXP_RECOVERY, INV_RODBARD
	"y = a*exp(bx)", "y = a*x^b",								//EXP_REGRESSION, POWER_REGRESSION
	"y = a+bx+cx^2+dx^3+ex^4+fx^5", "y = a+bx+cx^2+dx^3+ex^4+fx^5+gx^6",
	"y = a+bx+cx^2+dx^3+ex^4+fx^5+gx^6+hx^7", "y = a+bx+cx^2+dx^3+ex^4+fx^5+gx^6+hx^7+ix^8",
	"y = a*exp(-(x-b)*(x-b)/(2*c*c)))",						//GAUSSIAN_NOOFFSET
	"y = a*(1-exp(-b*x))",										//EXP_RECOVERY_NOOFFSET
	"y = a*(1-exp(-b*x))^c",									//CHAPMAN
	"y = a+b*erf((x-c)/d)"										//ERF; note that the c parameter is sqrt2 times the Gaussian
	};

	/** @deprecated now in the Minimizer class (since ImageJ 1.46f).
	 *	(probably of not much value for anyone anyhow?) */
	public static final int IterFactor = 500;

	private static final int CUSTOM = 100;	 // user function defined in macro or plugin
	private static final int GAUSSIAN_INTERNAL = 101;	// Gaussian with separate offset & multiplier
	private static final int RODBARD_INTERNAL = 102;	// Rodbard with separate offset & multiplier

	private int fitType = -1;		// Number of curve type to fit
	private double[] xData, yData;	// x,y data to fit
	private double[] xDataSave, yDataSave;	//saved original data after fitting modified data
	private int numPoints;			// number of data points in actual fit
	private double ySign = 0;		// remember sign of y data for power-law fit via regression
	private double sumY = Double.NaN, sumY2 = Double.NaN;  // sum(y), sum(y^2) of the data used for fitting
	private int numParams;			// number of parameters
	private double[] initialParams; // user specified or estimated initial parameters
	private double[] initialParamVariations; // estimate of range of parameters
	private double[] minimizerInitialParams;		  // modified initialParams of the minimizer
	private double[] minimizerInitialParamVariations; // modified initialParamVariations of the minimizer
	private double maxRelError = 1e-10;// maximum relative deviation of sum of residuals^2 from minimum
	private long time;				// elapsed time in ms
	private int customParamCount;	// number of parameters of user-supplied function (macro or plugin)
	private String customFormula;	// equation defined in macro or text
	private UserFunction userFunction; // plugin with custom fit function
	private Interpreter macro;		// holds macro with custom equation
	private int macroStartProgramCounter;	 // equation in macro starts here
	private int numRegressionParams;// number of parameters that can be calculated by linear regression
	private int offsetParam = -1;   // parameter number: function has this parameter as offset
	private int factorParam = -1;   // index of parameter that is slope or multiplicative factor of the function
	private boolean hasSlopeParam;	// whether the 'factorParam' is the slope of the function
	private double[] finalParams;	// parameters obtained by fit
	private boolean linearRegressionUsed;	// whether linear regression alone was used instead of the minimizer
	private boolean restrictPower;	// power via linear regression fit: (0,0) requires positive power
	private Minimizer minimizer = new Minimizer();
	private int minimizerStatus = Minimizer.INITIALIZATION_FAILURE; // status of the minimizer after minimizing
	private String errorString;		// in case of error before invoking the minimizer
	private static String[] sortedFitList; // names like fitList, but in more logical sequence
	private static Hashtable<String, Integer> namesTable; // converts fitList String into number

	/** Construct a new CurveFitter. */
	public CurveFitter (double[] xData, double[] yData) {
		this.xData = xData;
		this.yData = yData;
		numPoints = xData.length;
	}

	/** Perform curve fitting with one of the built-in functions
	 *			doFit(fitType) does the fit quietly
	 *	Use getStatus() and/or getStatusString() to see whether fitting was (probably) successful and
	 *	getParams() to access the result.
	 */
	public void doFit(int fitType) {
		doFit(fitType, false);
	}

	/** Perform curve fitting with one of the built-in functions
	 *			doFit(fitType, true) pops up a dialog allowing the user to set the initial
	 *						fit parameters and various numbers controlling the Minimizer
	 *	Use getStatus() and/or getStatusString() to see whether fitting was (probably) successful and
	 *	getParams() to access the result.
	 */
	public void doFit(int fitType, boolean showSettings) {
		if (!(fitType>=STRAIGHT_LINE && fitType<fitList.length || fitType==CUSTOM))
			throw new IllegalArgumentException("Invalid fit type");
		if (fitType==CUSTOM && macro==null && userFunction==null)
			throw new IllegalArgumentException("No custom formula!");
		this.fitType = fitType;
		if (isModifiedFitType(fitType))			// these fits don't use the original data and a different fit type (this.fitType)
			if (!prepareModifiedFitType(fitType)) return;
		numParams = getNumParams();
		if (fitType != CUSTOM)
			getOffsetAndFactorParams();
		//IJ.log("special params: off="+offsetParam+(hasSlopeParam ? " slo=" : " fac=")+factorParam+" numPar="+numParams+" numRegressPar="+numRegressionParams);
		calculateSumYandY2();					// sumY, sumY2 needed for regression, abs Error; R, goodness of modified fit functions
		long startTime = System.currentTimeMillis();
		if (this.fitType == STRAIGHT_LINE) {	// no minimizer needed
			finalParams = new double[] {0, 0, 0}; // (does not work with 0 initial slope)
			doRegression(finalParams);			// fit by regression; save sum(Residuals^2) as last array element
			linearRegressionUsed = true;
		} else {								// use simplex minimizer
			minimizer.setFunction(this, numParams-numRegressionParams);
			minimizer.setExtraArrayElements(numRegressionParams);  // reserve space for extra parameters if minimizer has fewer paramters
			if (macro != null) minimizer.setMaximumThreads(1);	// macro interpreter does not allow multithreading
			if (!makeInitialParamsAndVariations(fitType))		// also includes some data checking
				return;							// initialization failure
			if (showSettings) settingsDialog();
			if (numRegressionParams >0)
				modifyInitialParamsAndVariations();
			else {
				minimizerInitialParams = initialParams;
				minimizerInitialParamVariations = initialParamVariations;
			}
			startTime = System.currentTimeMillis();
			// The maximum absolute error of the fit must be specified in case the
			// fit function fits perfectly, i.e. the sume of residuals approaches 0.
			// In such a case, the maximum relative error is meaningless and the
			// minimizer would run until it reaches the maximum iteration count.
			double maxAbsError = Math.min(1e-6,maxRelError)*Math.sqrt(sumY2);
			minimizer.setMaxError(maxRelError, maxAbsError);
			//{String s="initVariations:";for(int ii=0;ii<numParams;ii++)s+=" ["+ii+"]:"+IJ.d2s(initialParamVariations[ii],5,9);IJ.log(s);}
			//{String s="minInitVariations:";for(int ii=0;ii<numParams;ii++)s+=" ["+ii+"]:"+IJ.d2s(minimizerInitialParamVariations[ii],5,9);IJ.log(s);}
			//{String s="minInitPars:";for(int ii=0;ii<numParams;ii++)s+=" ["+ii+"]:"+IJ.d2s(minimizerInitialParams[ii],5,9);IJ.log(s);}
			// m i n i m i z a t i o n	of squared residuals
			minimizerStatus = minimizer.minimize(minimizerInitialParams, minimizerInitialParamVariations);
			finalParams = minimizer.getParams();
			if (numRegressionParams > 0)
				minimizerParamsToFullParams(finalParams, false);
		}
		if (isModifiedFitType(fitType))         //params of actual fit to user params
			postProcessModifiedFitType(fitType);
		if (fitType == ERF)                     //make it nicer
			if (finalParams[3] < 0) {           // y = a+b*erf((x-c)/d) with negative d: invert b instead
				finalParams[1] = -finalParams[1];
				finalParams[3] = -finalParams[3];
			}

		switch (fitType) {		                //postprocessing for nicer output:
		    case GAUSSIAN:                      //Gaussians: width (std deviation) should be >0
                finalParams[3] = Math.abs(finalParams[3]); break;
            case GAUSSIAN_NOOFFSET:
                finalParams[2] = Math.abs(finalParams[2]); break;
		}
		time = System.currentTimeMillis()-startTime;
	}

	/** Fit a function defined as a macro String like "y = a + b*x + c*x*x".
	 *	Returns the number of parameters, or 0 in case of a macro syntax error.
	 *
	 *	For good performance, it is advisable to set also the typical variation range
	 *	of the initial parameters by the
	 *	getMinimizer().setInitialParamVariations(double[]) method (especially if one or
	 *	more of the initialParams are zero).
	 *	Use getStatus() and/or getStatusString() to see whether fitting was (probably) successful and
	 *	getParams() to access the result.
	 */
	public int doCustomFit(String equation, double[] initialParams, boolean showSettings) {
		customFormula = null;
		customParamCount = getNumParams(equation);
		if (customParamCount==0)
			return 0;
		customFormula = equation;
		String code =
			"var x, a, b, c, d, e, f;\n"+
			"function dummy() {}\n"+
			equation+";\n"; // starts at program counter location 21
		macroStartProgramCounter = 21;
		macro = new Interpreter();
		try {
			macro.run(code, null);
		} catch (Exception e) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);
		}
		if (macro.wasError())
			return 0;
		this.initialParams = initialParams;
		doFit(CUSTOM, showSettings);
		return customParamCount;
	}

	/** Fit a function defined in a user plugin implementing the UserFunction interface
	 *
	 *	Use getStatus() and/or getStatusString() to see whether fitting was (probably) successful and
	 *	getParams() to access the result.
	 *
	 *	@param userFunction		A plugin where the fit function is defined by the
	 *							userFunction(params, x) method.
	 *							This function must allow simultaneous calls in multiple threads.
	 *	@param numParams		Number of parameters of the fit function.
	 *	@param formula			A String describing the fit formula, may be null.
	 *	@param initialParams	Starting point for the parameters; may be null (than values
	 *							of 0 are used). The fit function with these parameters must
	 *							not return NaN for any of the data points given in the
	 *							constructor (xData).
	 *	@param initialParamVariations Each parameter is initially varied by up to +/- this value.
	 *							If not given (null), initial variations are taken as
	 *							10% of initial parameter value or 0.01 for parameters that are zero.
	 *							When this array is given, all elements must be positive (nonzero).
	 *							See Minimizer.minimize for details.
	 *	@param showSettings		Displays a popup dialog for modifying the initial parameters and
	 *							a few numbers controlling the minimizer.
	 */
	public void doCustomFit(UserFunction userFunction, int numParams, String formula,
		double[] initialParams, double[] initialParamVariations, boolean showSettings) {
		this.userFunction = userFunction;
		this.customParamCount = numParams;
		this.initialParams = initialParams;
		this.initialParamVariations = initialParamVariations;
		customFormula = formula==null ? "(defined in plugin)" : formula;
		doFit(CUSTOM, showSettings);
	}

	/** Sets the initial parameters, which override the default initial parameters. */
	public void setInitialParameters(double[] initialParams) {
		this.initialParams = initialParams;
	}

	/** Returns a reference to the Minimizer used, for accessing Minimizer methods directly.
	 *	Note that no Minimizer is used if fitType is any of STRAIGHT_LINE, EXP_REGRESSION,
	 *	and POWER_REGRESSION. */
	public Minimizer getMinimizer() {
		return minimizer;
	}

	/** For improved fitting performance when using a custom fit formula, one may
	 *	specify parameters that can be calculated directly by linear regression.
	 *	For values not used, set the index to -1
	 *
	 * @param offsetParam  Index of a parameter that is a pure offset:
	 *					   E.g. '0' if	f(p0, p1, p2...) = p0 + function(p1, p2, ...).
	 * @param multiplyParam	 Index of a parameter that is purely multiplicative.
	 *					   E.g. multiplyParams=1 if f(p0, p1, p2, p3...) can be expressed as
	 *					   p1*func(p0, p2, p3, ...) or p0 +p1*func(p0, p2, p3, ...) with '0' being
	 *					   the offsetparam.
	 * @param slopeParam   Index of a parameter that is multiplied with x and then summed to the function.
	 *					   E.g. '1' for f(p0, p1, p2, p3...) = p1*x + func(p0, p2, p3, ...)
	 *					   Only one, multiplyParam and slopeParam can be used (ie.e, the other
	 *					   should be set to -1)
	 */
	public void setOffsetMultiplySlopeParams(int offsetParam, int multiplyParam, int slopeParam) {
		this.offsetParam = offsetParam;
		hasSlopeParam = slopeParam >= 0;
		factorParam = hasSlopeParam ? slopeParam : multiplyParam;
		numRegressionParams = 0;
		if (offsetParam >=0) numRegressionParams++;
		if (factorParam >=0) numRegressionParams++;
	}

	/** Get number of parameters for current fit formula
	 *	Do not use before 'doFit', because the fit function would be undefined.	 */
	public int getNumParams() {
		if (fitType == CUSTOM)
			return customParamCount;
		else
			return getNumParams(fitType);
	}

	/** Returns the number of parameters for a given fit type, except for the 'custom' fit,
	 *  where the number of parameters is given by the equation: see getNumParams(String) */
	public static int getNumParams(int fitType) {
		switch (fitType) {
			case STRAIGHT_LINE: return 2;
			case POLY2: return 3;
			case POLY3: return 4;
			case POLY4: return 5;
			case POLY5: return 6;
			case POLY6: return 7;
			case POLY7: return 8;
			case POLY8: return 9;
			case EXPONENTIAL: case EXP_REGRESSION: return 2;
			case POWER: case POWER_REGRESSION:	   return 2;
			case EXP_RECOVERY_NOOFFSET: return 2;
			case LOG:	return 2;
			case LOG2:	return 3;
			case GAUSSIAN_NOOFFSET: return 3;
			case EXP_RECOVERY: return 3;
			case CHAPMAN: return 3;
			case EXP_WITH_OFFSET: return 3;
			case RODBARD: case RODBARD2: case INV_RODBARD: case RODBARD_INTERNAL: return 4;
			case GAMMA_VARIATE: return 4;
			case GAUSSIAN: case GAUSSIAN_INTERNAL: return 4;
			case ERF:   return 4;
		}
		return 0;
	}

	/** Returns the number of parameters for a custom equation given as a macro String,
	 *  like "y = a + b*x + c*x*x" .  Restricted to 6 parameters "a" ... "f"
	 *  (fitting more parameters is not likely to yield an accurate result anyhow).
	 *  Returns 0 if a very basic check does not find a formula of this type. */
	public static int getNumParams(String customFormula) {
		Program pgm = (new Tokenizer()).tokenize(customFormula);
		if (!pgm.hasWord("y") ||  !pgm.hasWord("x"))
		    return 0;
		String[] params = {"a","b","c","d","e","f"};
		int customParamCount = 0;
		for (int i=0; i<params.length; i++) {
			if (pgm.hasWord(params[i])) {
				customParamCount++;
			}
		}
		return customParamCount;
	}

	/** Returns the formula value for parameters 'p' at 'x'.
	 *	Do not use before 'doFit', because the fit function would be undefined. */
	public final double f(double x) {
		if (finalParams==null)
			finalParams = minimizer.getParams();
		return f(finalParams, x);
	}

	/** Returns the formula value for parameters 'p' at 'x'.
	 *	Do not use before 'doFit', because the fit function would be undefined.	 */
	public final double f(double[] p, double x) {
		if (fitType!=CUSTOM)
			return f(fitType, p, x);
		else {
			if (macro==null) {	// function defined in plugin
				return userFunction.userFunction(p, x);
			} else {				// function defined in macro
				macro.setVariable("x", x);
				macro.setVariable("a", p[0]);
				if (customParamCount>1) macro.setVariable("b", p[1]);
				if (customParamCount>2) macro.setVariable("c", p[2]);
				if (customParamCount>3) macro.setVariable("d", p[3]);
				if (customParamCount>4) macro.setVariable("e", p[4]);
				if (customParamCount>5) macro.setVariable("f", p[5]);
				macro.run(macroStartProgramCounter);
				return macro.getVariable("y");
			}
		}
	}

	/** Returns value of built-in 'fitType' formula value for parameters "p" at "x" */
	public static double f(int fitType, double[] p, double x) {
		switch (fitType) {
			case STRAIGHT_LINE:
				return p[0] + x*p[1];
			case POLY2:
				return p[0] + x*(p[1] + x*p[2]);
			case POLY3:
				return p[0] + x*(p[1] + x*(p[2] + x*p[3]));
			case POLY4:
				return p[0] + x*(p[1] + x*(p[2] + x*(p[3] + x*p[4])));
			case POLY5:
				return p[0] + x*(p[1] + x*(p[2] + x*(p[3] + x*(p[4] + x*p[5]))));
			case POLY6:
				return p[0] + x*(p[1] + x*(p[2] + x*(p[3] + x*(p[4] + x*(p[5] + x*p[6])))));
			case POLY7:
				return p[0] + x*(p[1] + x*(p[2] + x*(p[3] + x*(p[4] + x*(p[5] + x*(p[6] + x*p[7]))))));
			case POLY8:
				return p[0] + x*(p[1] + x*(p[2] + x*(p[3] + x*(p[4] + x*(p[5] + x*(p[6] + x*(p[7]+x*p[8])))))));
			case EXPONENTIAL:
			case EXP_REGRESSION:
				return p[0]*Math.exp(p[1]*x);
			case EXP_WITH_OFFSET:
				return p[0]*Math.exp(-p[1]*x)+p[2];
			case EXP_RECOVERY:
				return p[0]*(1-Math.exp(-p[1]*x))+p[2];
			case EXP_RECOVERY_NOOFFSET:
			    return p[0]*(1-Math.exp(-p[1]*x));
			case CHAPMAN:                               // a*(1-exp(-b*x))^c
				double value =  p[0]*(Math.pow((1-(Math.exp(-p[1]*x))), p[2]));
			//	Log.e("test", "values = " + value);
				return value;
			case GAUSSIAN:
				return p[0]+(p[1]-p[0])*Math.exp(-(x-p[2])*(x-p[2])/(2.0*p[3]*p[3]));
			case GAUSSIAN_INTERNAL:						// replaces GAUSSIAN for the fitting process
				return p[0]+p[1]*Math.exp(-(x-p[2])*(x-p[2])/(2.0*p[3]*p[3]));
			case GAUSSIAN_NOOFFSET:
				return p[0]*Math.exp(-(x-p[1])*(x-p[1])/(2.0*p[2]*p[2]));
			case POWER:									// ax^b
			case POWER_REGRESSION:
				return p[0]*Math.pow(x,p[1]);
			case LOG:
				if (x == 0.0)
					return -1000*p[0];
				return p[0]*Math.log(p[1]*x);
			case RODBARD: {								// d+(a-d)/(1+(x/c)^b)
				double ex = Math.pow(x/p[2], p[1]); //(x/c)^b
				return p[3]+(p[0]-p[3])/(1.0+ex); }
			case RODBARD_INTERNAL: {					// d+a/(1+(x/c)^b) , replaces RODBARD of the fitting process
				double ex = Math.pow(x/p[2], p[1]); //(x/c)^b
				return p[3]+p[0]/(1.0+ex); }
			case GAMMA_VARIATE:							// b*(x-a)^c*exp(-(x-a)/d)
				if (p[0] >= x) return 0.0;
				if (p[1] <= 0) return Double.NaN;
				if (p[2] <= 0) return Double.NaN;
				if (p[3] <= 0) return Double.NaN;

				double pw = Math.pow((x - p[0]), p[2]);
				double e = Math.exp((-(x - p[0]))/p[3]);
				return p[1]*pw*e;
			case LOG2:
				double tmp = x-p[2];
				if (tmp<=0)
					return Double.NaN;
				return p[0]+p[1]*Math.log(tmp);
			case INV_RODBARD:		// c*((x-a)/(d-x))^(1/b), the inverse Rodbard function
			case RODBARD2:			// also used after the 'Rodbard NIH Image' fit
				double y;
				if (p[3]-x < 2*Double.MIN_VALUE || x<p[0]) // avoid x>=d (singularity) and x<a (negative exponent)
					y = fitType==INV_RODBARD ? Double.NaN : 0.0;
				else {
					y = (x-p[0])/(p[3]-x);		//(x-a)/(d-x) = ( (a-d)/(x-d) -1 )
					y = Math.pow(y,1.0/p[1]);	//y=y**(1/b)
					y = y*p[2];
				}
				return y;
            case ERF:
				return p[0] + p[1]*IJMath.erf((x-p[2])/p[3]);	//y=a+b*erf((x-c)/d)
			default:
				return 0.0;
		}
	}

	/** Get the result of fitting, i.e. the set of parameter values for the best fit.
	 *	Note that the array returned may have more elements than numParams; ignore the rest.
	 *	May return an array with only NaN values if the minimizer could not start properly,
	 *	i.e., if getStatus() returns Minimizer.INITILIZATION_FAILURE.
	 *	See Minimizer.getParams() for details.
	 */
	public double[] getParams() {
		return finalParams==null ? minimizer.getParams() : finalParams; //if we have no result, take all_NaN result from the Minimizer
	}

	/** Returns residuals array, i.e., differences between data and curve.
	 *	The residuals are with respect to the real data, also for fit types where the data are
	 *	modified before fitting (power&exp fit by linear regression, 'Rodbard NIH Image' ).
	 *	This is in contrast to sum of squared residuals, which is for the fit that was actually done.
	 */
	public double[] getResiduals() {
		double[] params = getParams();
		double[] residuals = new double[xData.length];
		for (int i=0; i<xData.length; i++)
			residuals[i] = yData[i] - f(params, xData[i]);
		return residuals;
	}

	/* Get the sum of the residuals (may be NaN if the minimizer could not start properly
	 *	i.e., if getStatus() returns Minimizer.INITILIZATION_FAILURE).
	 */
	public double getSumResidualsSqr() {
		return getParams()[numParams];	// value is stored as last element by the minimizer
	}

	/** Returns the standard deviation of the residuals.
	 *	Here, the standard deviation is defined here as the root-mean-square of the residuals
	 *	times sqrt(n/(n-1)); where n is the number of points.
	 */
	public double getSD() {
		double[] residuals = getResiduals();
		int n = residuals.length;
		double sum=0.0, sum2=0.0;
		for (int i=0; i<n; i++) {
			sum += residuals[i];
			sum2 += residuals[i]*residuals[i];
		}
		double stdDev = (sum2-sum*sum/n); //sum of squared residuals
		return Math.sqrt(stdDev/(n-1.0));
	}

	/** Returns R^2, where 1.0 is best.
	<pre>
	 r^2 = 1 - SSE/SSD

	 where:	 SSE = sum of the squared errors
				 SSD = sum of the squared deviations about the mean.
	</pre>
	 *	For power, exp by linear regression and 'Rodbard NIH Image', this is calculated for the
	 *	fit actually done, not for the residuals of the original data.
	*/
	public double getRSquared() {
		if (Double.isNaN(sumY)) calculateSumYandY2();
		double sumMeanDiffSqr = sumY2 - sumY*sumY/numPoints;
		double rSquared = 0.0;
		if (sumMeanDiffSqr > 0.0)
			rSquared = 1.0 - getSumResidualsSqr()/sumMeanDiffSqr;
		return rSquared;
	}

	/** Get a measure of "goodness of fit" where 1.0 is best.
	 *	Approaches R^2 if the number of points is much larger than the number of fit parameters.
	 *	For power, exp by linear regression and 'Rodbard NIH Image', this is calculated for the
	 *	fit actually done, not for the residuals of the original data.
	 */
	public double getFitGoodness() {
		if (Double.isNaN(sumY)) calculateSumYandY2();
		double sumMeanDiffSqr = sumY2 - sumY*sumY/numPoints;
		double fitGoodness = 0.0;
		int degreesOfFreedom = numPoints - getNumParams();
		if (sumMeanDiffSqr > 0.0 && degreesOfFreedom > 0)
			fitGoodness = 1.0 - (getSumResidualsSqr()/ sumMeanDiffSqr) * numPoints / (double)degreesOfFreedom;

		return fitGoodness;
	}

	public int getStatus() {
		return linearRegressionUsed ? Minimizer.SUCCESS : minimizerStatus;
	}

	/** Get a short text with a short description of the status. Should be preferred over
	 *	Minimizer.STATUS_STRING[getMinimizer().getStatus()] because getStatusString()
	 *	better explains the problem in some cases of initialization failure (data not
	 *	compatible with the fit function chosen) */
	public String getStatusString() {
		return errorString != null ? errorString : minimizer.STATUS_STRING[getStatus()];
	}

	/** Get a string with detailed description of the curve fitting results (several lines,
	 *	including the fit parameters).
	 */
	public String getResultString() {
		String resultS =  "\nFormula: " + getFormula() +
				"\nStatus: "+getStatusString();
		if (!linearRegressionUsed) resultS += "\nNumber of completed minimizations: " + minimizer.getCompletedMinimizations();
		resultS += "\nNumber of iterations: " + getIterations();
		if (!linearRegressionUsed) resultS += " (max: " + minimizer.getMaxIterations() + ")";
		resultS += "\nTime: "+time+" ms" +
				"\nSum of residuals squared: " + IJ.d2s(getSumResidualsSqr(),5,9) +
				"\nStandard deviation: " + IJ.d2s(getSD(),5,9) +
				"\nR^2: " + IJ.d2s(getRSquared(),5) +
				"\nParameters:";
		char pChar = 'a';
		double[] pVal = getParams();
		for (int i = 0; i < numParams; i++) {
			resultS += "\n	" + pChar + " = " + IJ.d2s(pVal[i],5,9);
			pChar++;
		}
		return resultS;
	}

	/** Set maximum number of simplex restarts to do. See Minimizer.setMaxRestarts for details. */
	public void setRestarts(int maxRestarts) {
		minimizer.setMaxRestarts(maxRestarts);
	}

	/** Set the maximum error. by which the sum of residuals may deviate from the true value
	 *	(relative w.r.t. full sum of rediduals). Possible range: 0.1 ... 10^-16 */
	public void setMaxError(double maxRelError) {
		if (Double.isNaN(maxRelError)) return;
		if (maxRelError > 0.1)	 maxRelError = 0.1;
		if (maxRelError < 1e-16) maxRelError = 1e-16;	// can't be less than numerical accuracy
		this.maxRelError = maxRelError;
	}

    /** Create output on the number of iterations in the ImageJ Status line, e.g.
     *  "<ijStatusString> 50 (max 750); ESC to stop"
     *  @param ijStatusString Displayed in the beginning of the status message. No display if null.
     *  E.g. "Curve Fit: Iteration "
     *  @param checkEscape When true, the Minimizer stops if escape is pressed and the status
     *  becomes ABORTED. Note that checking for ESC does not work in the Event Queue thread. */
    public void setStatusAndEsc(String ijStatusString, boolean checkEscape) {
        minimizer.setStatusAndEsc(ijStatusString, checkEscape);
    }

	/** Get number of iterations performed. Returns 1 in case the fit was done by linear regression only. */
	public int getIterations() {
		return linearRegressionUsed ? 1 : minimizer.getIterations();
	}

	/** Get maximum number of iterations allowed (sum of iteration count for all restarts) */
	public int getMaxIterations() {
		return minimizer.getMaxIterations();
	}

	/** Set maximum number of iterations allowed (sum of iteration count for all restarts) */
	public void setMaxIterations(int maxIter) {
		minimizer.setMaxIterations(maxIter);
	}

	/** Get maximum number of simplex restarts to do. See Minimizer.setMaxRestarts for details. */
	public int getRestarts() {
		return minimizer.getMaxRestarts();
	}

	/** Returns the status of the Minimizer after doFit.  Minimizer.SUCCESS indicates a
	 *	successful completion. In case of Minimizer.INITIALIZATION_FAILURE, fitting could
	 *	not be performed because the data and/or initial parameters are not compatible
	 *	with the function value.  In that case, getStatusString may explain the problem.
	 *	For further status codes indicating problems during fitting, see the status codes
	 *	of the Minimzer class. */

	/** returns the array with the x data */
	public double[] getXPoints() {
		return xData;
	}

	/** returns the array with the y data */
	public double[] getYPoints() {
		return yData;
	}

	/** returns the code of the fit type of the fit performed */
	public int getFit() {
		return fitType;
	}

	/** returns the name of the fit function of the fit performed */
	public String getName() {
		if (fitType==CUSTOM)
			return "User-defined";
		if (fitType==GAUSSIAN_INTERNAL)
			fitType = GAUSSIAN;
		else if (fitType==RODBARD_INTERNAL)
			fitType = RODBARD;
		return fitList[fitType];
	}

	/** returns a String with the formula of the fit function used */
	public String getFormula() {
		if (fitType==CUSTOM)
			return customFormula;
		else
			return fList[fitType];
	}

	/** Returns an array of fit names with nicer sorting */
	public static String[] getSortedFitList() {
		if (sortedFitList == null) {
			String[] tmpList = new String[fitList.length];
			for (int i=0; i<fitList.length; i++)
				tmpList[i] = fitList[sortedTypes[i]];
			sortedFitList = tmpList;
		}
		return sortedFitList;
	}

	/** Returns the code for a fit with given name as defined in fitList, or -1 if not found */
	public static int getFitCode(String fitName) {
		if (namesTable == null) {
			Hashtable<String,Integer> h = new Hashtable<String,Integer>();
			for (int i=0; i<fitList.length; i++)
				h.put(fitList[i], new Integer(i));
			namesTable = h;
		}
		Integer i = (Integer)namesTable.get(fitName);
		return i!=null? i.intValue() : -1;
	}

	/** This function is called by the Minimizer and calculates the sum of squared
	 *	residuals for given parameters.
	 *	To improve the efficiency, simple linear dependencies are solved directly
	 *	by linear regression; in that case the corresponding parameters are modified.
	 *	This effectively reduces the number of free parameters by one or two and thereby
	 *	significantly improves the performance of minimization.
	 */
	public final double userFunction(double[] params, double dummy) {
		double sumResidualsSqr = 0.0;
		if (numRegressionParams == 0) {		// simply calculate sum of residuals
			for (int i=0; i<numPoints; i++) {
				double fValue = f(params,xData[i]);
				sumResidualsSqr += sqr(fValue-yData[i]);
			}
            //IJ.log(IJ.d2s(params[0],3,5)+","+IJ.d2s(params[1],3,5)+": r="+IJ.d2s(sumResidualsSqr,3,5)+Thread.currentThread().getName() );
		} else {	// handle simple linear dependencies by linear regression:
			//if(getIterations()<1){String s="minimizerPar:";for(int ii=0;ii<=numParams;ii++)s+=" ["+ii+"]:"+IJ.d2s(params[ii],5,9);IJ.log(s);}
			minimizerParamsToFullParams(params, true);
			doRegression(params);
			sumResidualsSqr = fullParamsToMinimizerParams(params);
		}
		return sumResidualsSqr;
	}

	/** For fits where linear regression is used to reduce the number of parameters handled
	 *	by the Minimizer, convert Minimizer parameters to the complete set of parameters.
	 *	When not for calculating regression, we use the sum of squared residuals,
	 *	offset and multiplication factor stored in the extra array elements:
	 *	The Minimizer stores the sum of squared residuals directly behind its last parameter.
	 *	The next element is the value of the offsetParam (if any).
	 *	The final element is the value of the factorParam (if any).
	 */
	private void minimizerParamsToFullParams(double[] params, boolean forRegression) {
		boolean shouldTransformToSmallerParams = false;
		double offset = 0;
		double factor = hasSlopeParam ? 0 : 1; //for regression, calculate function value without x*slope, but multiplied with 1
		double sumResidualsSqr = 0;
		if (!forRegression) {				// recover regression-calculated parameters from extra array elements
			int i = params.length - 1;
			if (factorParam >= 0)
				factor = params[i--];
			if (offsetParam >= 0)
				offset = params[i];
			sumResidualsSqr = params[numParams-numRegressionParams];	// sum of squared residuals has been calculated already
			params[numParams] = sumResidualsSqr;						// ... and is now stored in its new (proper) place
		}
		// move array elements to position in array with full number of parameters
		for (int i=numParams-1, iM=numParams-numRegressionParams-1; i>=0; i--) {
			if (i == offsetParam)
				params[i] = offset;
			else if (i == factorParam)
				params[i] = factor;
			else
				params[i] = params[iM--];
		}
		params[numParams] = sumResidualsSqr;
	}

	/** Determine sum of squared residuals with linear regression.
	 *	The sum of squared residuals is written to the array element with index 'numParams',
	 *	the offset and factor params (if any) are written to their proper positions in the
	 *	params array */
	private void doRegression(double[] params) {
		double sumX=0, sumX2=0, sumXY=0; //sums for regression; here 'x' are function values
		double sumY=0, sumY2=0;			//only calculated for 'slope', otherwise we use the values calculated already
		for (int i=0; i<numPoints; i++) {
			double fValue = fitType == STRAIGHT_LINE ? 0 : f(params, xData[i]);	 // function value
			if (Double.isNaN(fValue)) { //check for NaN now; later we need NaN checking for division-by-zero check.
				params[numParams] = Double.NaN;
				return;					//sum of squared residuals is NaN if any value is NaN
			}
			//if(getIterations()==0)IJ.log(xData[i]+"\t"+yData[i]+"\t"+fValue); //x,y,function
			if (hasSlopeParam) {		// fit y = offset + slope*x + function(of other params)
				double x = xData[i];
				double y = yData[i] - fValue;
				sumX += x;
				sumX2 += x*x;
				sumXY += x*y;
				sumY2 += y*y;
				sumY += y;
			} else {					// fit y = offset + factor * function(of other params)
				double x = fValue;
				double y = yData[i];
				sumX += fValue;
				sumX2 += fValue*fValue;
				sumXY += fValue*yData[i];
			}
		}
		if (!hasSlopeParam) {
			sumY = this.sumY;
			sumY2 = this.sumY2;
		}
		double factor = 0; // factor or slope
		double sumResidualsSqr = 0;
		if (offsetParam<0) {			// only 'factor' parameter, no offset:
			factor = sumXY/sumX2;
			if (Double.isNaN(factor) || Double.isInfinite(factor))
				factor = 0;				// all 'x' values are 0, any factor (slope) will fit
			sumResidualsSqr = sumY2 + factor*(factor*sumX2 - 2*sumXY);
			if (sumResidualsSqr < 2e-15*sumY2)
				sumResidualsSqr = 2e-15*sumY2;
		} else {						// full linear regression or offset only. Slope is named 'factor' here
			if (factorParam >= 0) {
				factor = (sumXY-sumX*sumY/numPoints)/(sumX2-sumX*sumX/numPoints);
				if (restrictPower & factor<=0)	// power-law fit with (0,0) point: power must be >0
					factor = 1e-100;
				else if (Double.isNaN(factor) || Double.isInfinite(factor))
					factor = 0;			// all 'x' values are equal, any factor (slope) will fit
			}
			double offset = (sumY-factor*sumX)/numPoints;
			params[offsetParam] = offset;
			sumResidualsSqr = sqr(factor)*sumX2 + numPoints*sqr(offset) + sumY2 +
					2*factor*offset*sumX - 2*factor*sumXY - 2*offset*sumY;
			// check for accuracy problem: large difference of small numbers?
			// Don't report unrealistic or even negative values, otherwise minimization could lead
			// into parameters where we have a numeric problem
			if (sumResidualsSqr < 2e-15*(sqr(factor)*sumX2 + numPoints*sqr(offset) + sumY2))
				sumResidualsSqr = 2e-15*(sqr(factor)*sumX2 + numPoints*sqr(offset) + sumY2);
			//if(){IJ.log("sumX="+sumX+" sumX2="+sumX2+" sumXY="+sumXY+" factor="+factor+" offset=="+offset);}
		}
		params[numParams] = sumResidualsSqr;
		if (factorParam >= 0)
			params[factorParam] = factor;
	}
	/** Convert full set of parameters to minimizer parameters and returns the sum of residuals squared.
	 *	The last array elements, not used by the minimizer, are the offset and factor parameters (if any)
	 */
	private double fullParamsToMinimizerParams(double[] params) {
		double offset = offsetParam >=0 ? params[offsetParam] : 0;
		double factor = factorParam >=0 ? params[factorParam] : 0;
		double sumResidualsSqr = params[numParams];

		for (int i=0, iNew=0; i<numParams; i++)		// leave only the parameters for the minimizer in the beginning of the array
			if (i != factorParam && i != offsetParam)
				params[iNew++] = params[i];
		int i = params.length - 1;
		if (factorParam >= 0)
			params[i--] = factor;
		if (offsetParam >= 0)
			params[i--] = offset;
		params[i--] = sumResidualsSqr;
		return sumResidualsSqr;
	}

	/** In case one or two parameters are calculated by regression and not by the minimizer:
	 *	Make modified initialParams and initialParamVariations for the Minimizer
	 */
	private void modifyInitialParamsAndVariations() {
		minimizerInitialParams = initialParams.clone();
		minimizerInitialParamVariations = initialParamVariations.clone();
		if (numRegressionParams >  0) // convert to shorter arrays with only the parameters used by the minimizer
			for (int i=0, iNew=0; i<numParams; i++)
				if (i != factorParam && i != offsetParam) {
					minimizerInitialParams[iNew] = minimizerInitialParams[i];
					minimizerInitialParamVariations[iNew] = minimizerInitialParamVariations[i];
					iNew++;
				}
	}

	/** Estimate values for initial parameters and their typical range for the built-in
	 *	function types.	 For fits with modifications for regression, 'fitType' is still
	 *	the type of the original (unmodified) fit.
	 *	Also checks for x values being non-negative for fit types that require this,
	 *	and returns false if the data cannot be fitted because of negative x.
	 *	In such a case, 'errorString' contains a message about the problem. */
	private boolean makeInitialParamsAndVariations(int fitType) {
		boolean hasInitialParams = initialParams != null;
		boolean hasInitialParamVariations = initialParamVariations != null;
		if (!hasInitialParams) {
			initialParams = new double[numParams];
			if (fitType==CUSTOM) {
            	for (int i=0; i<numParams; i++)
            		initialParams[i] = 1.0;
            }
		}
		if (!hasInitialParamVariations)
			initialParamVariations = new double[numParams];
		if (fitType==CUSTOM) {
			for (int i=0; i<numParams; i++) {
				initialParamVariations[i] = 0.1 * initialParams[i];
                if (initialParamVariations[i] == 0) initialParamVariations[i] = 0.01; //should not be zero
            }
            return true; // can't guess the initial parameters or initialParamVariations from the data
        }

		// Calculate some things that might be useful for predicting parameters
		double firstx = xData[0];
		double firsty = yData[0];
		double lastx = xData[numPoints-1];
		double lasty = yData[numPoints-1];
		double xMin=firstx, xMax=firstx;    //with this initialization, the loop starts at 1, not 0
		double yMin=firsty, yMax=firsty;
		double xMean=firstx, yMean=firsty;
		double xOfMax = firstx;
		for (int i=1; i<numPoints; i++) {
			xMean += xData[i];
			yMean += yData[i];
			if (xData[i]>xMax) xMax = xData[i];
			if (xData[i]<xMin) xMin = xData[i];
			if (yData[i]>yMax) { yMax = yData[i]; xOfMax = xData[i]; }
			if (yData[i]<yMin) yMin = yData[i];
		}
		xMean /= numPoints;
		yMean /= numPoints;

		double slope = (lasty - firsty)/(lastx - firstx);
		if (Double.isNaN(slope) || Double.isInfinite(slope)) slope = 0;
		double yIntercept = yMean - slope * xMean;

		//We cannot fit the following cases because we would always get NaN function values
		if (xMin < 0 && (fitType==POWER||fitType==CHAPMAN)) {
			errorString = "Cannot fit "+fitList[fitType]+" when x<0";
			return false;
		} else if (xMin < 0 && xMax > 0 && fitType==RODBARD) {
			errorString = "Cannot fit "+fitList[fitType]+" to mixture of x<0 and x>0";
			return false;
		} else if (xMin <= 0 && fitType==LOG) {
			errorString = "Cannot fit "+fitList[fitType]+" when x<=0";
			return false;
		}

		if (!hasInitialParams) {
			switch (fitType) {
				//case POLY2: case POLY3: case POLY4: case POLY5: case POLY6: case POLY7: case POLY8:
				// offset&slope calculated via regression; leave the others at 0

				// also for the other cases, some initial parameters are unused; only to show them with 'showSettings'
				case EXPONENTIAL:			// a*exp(bx)   assuming change by factor of e between xMin & xMax
					initialParams[1] = 1.0/(xMax-xMin+1e-100) * Math.signum(yMean) * Math.signum(slope);
					initialParams[0] = yMean/Math.exp(initialParams[1]*xMean); //don't care, done by regression
					break;
				case EXP_WITH_OFFSET:		// a*exp(-bx) + c	assuming b>0, change by factor of e between xMin & xMax
				case EXP_RECOVERY:			// a*(1-exp(-bx)) + c
					initialParams[1] = 1./(xMax-xMin+1e-100);
					initialParams[0] = (yMax-yMin+1e-100)/Math.exp(initialParams[1]*xMean) * Math.signum(slope) *
							fitType==EXP_RECOVERY ? 1 : -1; // don't care, we will do this via regression
					initialParams[2] = 0.5*yMean;			// don't care, we will do this via regression
					break;
				case EXP_RECOVERY_NOOFFSET: // a*(1-exp(-bx))
				    initialParams[1] = 1.0/(xMax-xMin+1e-100) * Math.signum(yMean) * Math.signum(slope);
				    initialParams[0] = yMean/Math.exp(initialParams[1]*xMean); //don't care, done by regression
				    break;
				case POWER:					// ax^b, assume linear for the beginning
					initialParams[0] = yMean/(Math.abs(xMean+1e-100));	// don't care, we will do this via regression
					initialParams[1] = 1.0;
					break;
				case LOG:					// a*ln(bx), assume b=e/xMax
					initialParams[0] = yMean;				// don't care, we will do this via regression
					initialParams[1] = Math.E/(xMax+1e-100);
					break;
				case LOG2:					// y = a+b*ln(x-c)
					initialParams[0] = yMean;				// don't care, we will do this via regression
					initialParams[1] = (yMax-yMin+1e-100)/(xMax-xMin+1e-100); // don't care, we will do this via regression
					initialParams[2] = Math.min(0., -xMin-0.1*(xMax-xMin)-1e-100);
					break;
				case RODBARD:				// d+(a-d)/(1+(x/c)^b)
					initialParams[0] = firsty;	// don't care, we will do this via regression
					initialParams[1] = 1.0;
					initialParams[2] = xMin < 0 ? xMin : xMax; //better than xMean;
					initialParams[3] = lasty;	// don't care, we will do this via regression
					break;
				case INV_RODBARD: case RODBARD2: // c*((x-a)/(d-x))^(1/b)
					initialParams[0] = xMin - 0.1 * (xMax-xMin);
					initialParams[1] = slope >= 0 ? 1.0 : -1.0;
					initialParams[2] = yMax;	// don't care, we will do this via regression
					initialParams[3] = xMax + (xMax - xMin);
					break;
				case GAMMA_VARIATE:			// // b*(x-a)^c*exp(-(x-a)/d)
				//	First guesses based on following observations (naming the 'x' coordinate 't'):
				//	t0 [a] = time of first rise in gamma curve - so use the user specified first x value
				//	tmax = t0 + c*d where tmX is the time of the peak of the curve
				//	therefore an estimate for c and d is sqrt(tm-t0)
				//	K [a] can now be calculated from these estimates
					initialParams[0] = xMin;
					double cd = xOfMax - xMin;
					if (cd < 0.1*(xMax-xMin)) cd = 0.1*(xMax-xMin);
					initialParams[2] = Math.sqrt(cd);
					initialParams[3] = Math.sqrt(cd);
					initialParams[1] = yMax / (Math.pow(cd, initialParams[2]) * Math.exp(-cd/initialParams[3])); // don't care, we will do this via regression
					break;
				case GAUSSIAN:					// a + (b-a)*exp(-(x-c)^2/(2d^2))
					initialParams[0] = yMin;	//actually don't care, we will do this via regression
					initialParams[1] = yMax;	//actually don't care, we will do this via regression
					initialParams[2] = xOfMax;
					initialParams[3] = 0.39894 * (xMax-xMin) * (yMean-yMin)/(yMax-yMin+1e-100);
					break;
				case GAUSSIAN_NOOFFSET:			// a*exp(-(x-b)^2/(2c^2))
					initialParams[0] = yMax;	//actually don't care, we will do this via regression
					initialParams[1] = xOfMax;	  //actually don't care, we will do this via regression
					initialParams[2] = 0.39894 * (xMax-xMin) * yMean/(yMax+1e-100);
					break;
				case CHAPMAN:                   // a*(1-exp(-b*x))^c
					initialParams[0] = yMax;
					initialParams[2] = 1.5; // just assuming any reasonable value
					for (int i=1; i<numPoints; i++) //find when we reach 50% of maximum
					    if (yData[i]>0.5*yMax) {  //approximately (1-exp(-1))^1.5 = 0.5
					        initialParams[1] = 1./xData[i];
					        break;
					    }
					if(Double.isNaN(initialParams[1]) || initialParams[1]>1000./xMax) //in case an outlier at the beginning has fooled us
					    initialParams[1] = 10./xMax;
					break;
				case ERF:	// a+b*erf((x-c)/d)
					initialParams[0] = 0.5*(yMax+yMin);	//actually don't care, we will do this via regression
					initialParams[1] = 0.5*(yMax-yMin+1e-100) * (lasty>firsty ? 1 : -1);	//actually don't care, we will do this via regression
					initialParams[2] = xMin + (xMax-xMin)*(lasty>firsty ? yMax - yMean : yMean - yMin)/(yMax-yMin+1e-100);
					initialParams[3] = 0.1 * (xMax-xMin+1e-100);
					break;
				//no case CUSTOM: here, was done above
			}
		}
		if (!hasInitialParamVariations) {	// estimate initial range for parameters
			for (int i=0; i<numParams; i++)
				initialParamVariations[i] = 0.1 * initialParams[i]; //default, should be overridden if it can be zero
			switch (fitType) {
				case POLY2: case POLY3: case POLY4: case POLY5: case POLY6: case POLY7: case POLY8:
					double xFactor = 0.5*Math.max(Math.abs(xMax+xMin), (xMax-xMin));
					initialParamVariations[numParams-1] = (yMax-yMin)/(Math.pow(0.5*(xMax-xMin), numParams-1)+1e-100);
					for (int i=numParams-2; i>=0; i--)
						initialParamVariations[i] = initialParamVariations[i+1]*xFactor;
					break;
				case EXPONENTIAL:		 // a*exp(bx)		  a (and c) is calculated by regression
				case EXP_WITH_OFFSET:	 // a*exp(-bx) + c
				case EXP_RECOVERY:		 // a*(1-exp(-bx)) + c
					initialParamVariations[1] = 0.1/(xMax-xMin+1e-100);
					break;
				// case CHAPMAN:            // a*(1-exp(-b*x))^c use default (10% of value) for b, c
				// case POWER:				// ax^b; use default for b
				// case LOG:				// a*ln(bx); use default for b
				// case LOG2:				// y = a+b*ln(x-c); use default for c
				case RODBARD:				// d+(a-d)/(1+(x/c)^b); a and d calculated by regression
					initialParamVariations[2] = 0.5*Math.max((xMax-xMin), Math.abs(xMean));
					initialParamVariations[3] = 0.5*Math.max(yMax-yMin, Math.abs(yMax));
					break;
				case INV_RODBARD:			// c*((x-a)/(d-x))^(1/b); c calculated by regression
					initialParamVariations[0] = 0.01*Math.max(xMax-xMin, Math.abs(xMax));
					initialParamVariations[2] = 0.1*Math.max(yMax-yMin, Math.abs(yMax));
					initialParamVariations[3] = 0.1*Math.max((xMax-xMin), Math.abs(xMean));
					break;
				case GAMMA_VARIATE:			// // b*(x-a)^c*exp(-(x-a)/d); b calculated by regression
				//	First guesses based on following observations:
				//	t0 [b] = time of first rise in gamma curve - so use the user specified first limit
				//	tm = t0 + a*B [c*d] where tm is the time of the peak of the curve
				//	therefore an estimate for a and B is sqrt(tm-t0)
				//	K [a] can now be calculated from these estimates
					initialParamVariations[0] = 0.1*Math.max(yMax-yMin, Math.abs(yMax));
					double ab = xOfMax - firstx + 0.1*(xMax-xMin+1e-100);
					initialParamVariations[2] = 0.1*Math.sqrt(ab);
					initialParamVariations[3] = 0.1*Math.sqrt(ab);
					break;
				case GAUSSIAN:				// a + (b-a)*exp(-(x-c)^2/(2d^2)); a,b calculated by regression
					initialParamVariations[2] = 0.2*initialParams[3]; //(and default for d)
					break;
				case GAUSSIAN_NOOFFSET:		// a*exp(-(x-b)^2/(2c^2))
					initialParamVariations[1] = 0.2*initialParams[2]; //(and default for c)
					break;
				case ERF:		            // a+b*erf((x-c)/d)
					initialParamVariations[2] = 0.1 * (xMax-xMin+1e-100);
					initialParamVariations[3] = 0.5 * initialParams[3];
					break;
			}
		}
		return true;
	}

	/** Set multiplyParams and offsetParam for built-in functions. This allows us to use linear
	 *	regression, reducing the number of parameters used by the Minimizer by up to 2, and
	 *	improving the speed and success rate of the minimization process */
	private void getOffsetAndFactorParams() {
		offsetParam = -1;
		factorParam = -1;
		hasSlopeParam = false;
		switch (fitType) {
			case STRAIGHT_LINE:
			case POLY2: case POLY3: case POLY4: case POLY5: case POLY6: case POLY7: case POLY8:
				offsetParam = 0;
				factorParam = 1;
				hasSlopeParam = true;
				break;
			case EXPONENTIAL:			// a*exp(bx)
				factorParam = 0;
				break;
			case EXP_WITH_OFFSET:		// a*exp(-bx) + c
			case EXP_RECOVERY:			// a*(1-exp(-bx)) + c
				offsetParam = 2;
				factorParam = 0;
				break;
			case EXP_RECOVERY_NOOFFSET:	// a*(1-exp(-bx))
				factorParam = 0;
				break;
			case CHAPMAN:               // a*(1-exp(-b*x))^c
				factorParam = 0;
				break;
			case POWER:					// ax^b
				factorParam = 0;
				break;
			case LOG:					// a*ln(bx)
				factorParam = 0;
				break;
			case LOG2:					// y = a+b*ln(x-c)
				offsetParam = 0;
				factorParam = 1;
				break;
			case RODBARD_INTERNAL:		// d+a/(1+(x/c)^b)
				offsetParam = 3;
				factorParam = 0;
				break;
			case INV_RODBARD:			// c*((x-a)/(d-x))^(1/b)
				factorParam = 2;
				break;
			case GAMMA_VARIATE:			// b*(x-a)^c*exp(-(x-a)/d)
				factorParam = 1;
				break;
			case GAUSSIAN_INTERNAL:		// a + b*exp(-(x-c)^2/(2d^2))
				offsetParam = 0;
				factorParam = 1;
				break;
			case GAUSSIAN_NOOFFSET:		// a*exp(-(x-b)^2/(2c^2))
				factorParam = 0;
				break;
			case ERF:					// a + b*erf((x-c)/d)
				offsetParam = 0;
				factorParam = 1;
				break;
		}
		numRegressionParams = 0;
		if (offsetParam >= 0) numRegressionParams++;
		if (factorParam >= 0) numRegressionParams++;
	}


	/** calculates the sum of y and y^2 */
	private void calculateSumYandY2() {
		sumY = 0.0; sumY2 = 0.0;
		for (int i=0; i<numPoints; i++) {
			double y = yData[i];
			sumY += y;
			sumY2 += y*y;
		}
	}

	/** returns whether this a fit type that acutally fits modified data with a modified function */
	private boolean isModifiedFitType(int fitType) {
		return fitType == POWER_REGRESSION || fitType == EXP_REGRESSION || fitType == RODBARD	||
				fitType == RODBARD2	|| fitType == GAUSSIAN;
	}

	/** For fits don't use the original data, prepare modified data and fit type.
	 *	Returns false if the data are incompatible with the fit type
	 *	In that case, 'errorString' is set to a message explaining the problem
	 */
	private boolean prepareModifiedFitType(int fitType) {
		if (fitType == GAUSSIAN) {
			this.fitType = GAUSSIAN_INTERNAL;	// different definition of parameters for using regression
			return true;
		} else if (fitType == RODBARD) {
			this.fitType = RODBARD_INTERNAL;	// different definition of parameters for using regression
			return true;
		} else if (fitType == POWER_REGRESSION || fitType == EXP_REGRESSION) {
			if (fitType == POWER_REGRESSION) {
				xDataSave = xData;
				xData = new double[numPoints];
			}
			yDataSave = yData;
			yData = new double[numPoints];
			ySign = 0;
			numPoints=0;  // we may have lower number of points if there is a (0,0) point that we ignore
			for (int i=0; i<xData.length; i++) {
				double y = yDataSave[i];
				if (fitType == POWER_REGRESSION) {
					double x = xDataSave[i];
					if (x==0 && y==0) {
						restrictPower = true;
						continue;	  // ignore (0,0) point in power law
					}
					if (x<=0) {
						errorString = "Cannot fit x<=0";
						return false;
					}
					xData[numPoints] = Math.log(x);
				}
				if (ySign == 0) ySign = Math.signum(y); //if unknown, determine whether y data are positive or negative
				if (y*ySign<=0) {
					errorString = "Cannot fit y=0 or mixture of y>0, y<0";
					return false;
				}
				yData[numPoints] = Math.log(y*ySign);
				numPoints++;
			}
			this.fitType = STRAIGHT_LINE;
		} else if (fitType == RODBARD2) { // 'Rodbard (NIH Image)' fit is inverse Rodbard function, by fitting x(y); for compatibility with NIH Image
			xDataSave = xData;
			yDataSave = yData;
			xData = yDataSave;	//swap
			yData = xDataSave;
			this.fitType = RODBARD_INTERNAL;
		}
		return true;
	}

	/** Get correct params and revert xData, yData if fit has been done via another function */
	private void postProcessModifiedFitType(int fitType) {
		if (fitType == POWER_REGRESSION || fitType == EXP_REGRESSION)	// ln y = ln (a*x^b) = ln a + b ln x
			finalParams[0] = ySign * Math.exp(finalParams[0]);			//or: ln (+,-)y = ln ((+,-)a*exp(bx)) = ln (+,-)a + bx
		if (fitType == GAUSSIAN)							// a + b exp(-...) to  a + (b-a)*exp(-...)
			finalParams[1] += finalParams[0];
		else if (fitType == RODBARD || fitType == RODBARD2) //d+a/(1+(x/c)^b) to d+(a-d)/(1+(x/c)^b)
			finalParams[0] += finalParams[3];

		if (xDataSave != null) {
			xData = xDataSave;
			numPoints = xData.length;
		}
		if (yDataSave != null) yData = yDataSave;
		this.fitType = fitType;
	}

	private final double sqr(double d) { return d * d; }


	/** Pop up a dialog allowing control over simplex starting parameters */
	private void settingsDialog() {
	    if (initialParamVariations == null)
	        initialParamVariations = new double[numParams];
		GenericDialog gd = new GenericDialog("Simplex Fitting Options");
		gd.addMessage("Function name: " + getName() + "\n" +
		"Formula: " + getFormula());
		char pChar = 'a';
		for (int i = 0; i < numParams; i++)
			gd.addNumericField("Initial_"+(char)(pChar+i)+":", initialParams[i], 2);
		gd.addNumericField("Maximum iterations:", minimizer.getMaxIterations(), 0);
		gd.addNumericField("Number of restarts:", minimizer.getMaxRestarts(), 0);
		gd.addNumericField("Error tolerance [1*10^(-x)]:", -(Math.log(maxRelError)/Math.log(10)), 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		// read initial parameters:
		for (int i = 0; i < numParams; i++) {
			double p = gd.getNextNumber();
			if (!Double.isNaN(p)) {
				initialParams[i] = p;
				initialParamVariations[i] = Math.max(0.01*p, 0.001*initialParamVariations[i]); //assume user-set params are accurate
			}
		}
		double n = gd.getNextNumber();
		if (n>0)
			minimizer.setMaxIterations((int)n);
		n = gd.getNextNumber();
		if (n>=0)
			minimizer.setMaxRestarts((int)n);
		n = gd.getNextNumber();
		setMaxError(Math.pow(10.0, -n));
	}

	 /**
	 * Gets index of highest value in an array.
	 *
	 * @param			   array the array.
	 * @return			   Index of highest value.
	 */
	public static int getMax(double[] array) {
		double max = array[0];
		int index = 0;
		for(int i = 1; i < array.length; i++) {
			if(max < array[i]) {
				max = array[i];
				index = i;
			}
		}
		return index;
	}

}
