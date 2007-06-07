package ij.macro;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.*;
import ij.plugin.frame.Editor;
import ij.text.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;

/** This class implements the built-in macro functions. */
public class Functions implements MacroConstants, Measurements {
	Interpreter interp;
	Program pgm;
    boolean updateNeeded;
    boolean autoUpdate = true;
    ImagePlus imp;
    ImageProcessor ip;
    int imageType;
    boolean colorSet;

	Functions(Interpreter interp, Program pgm) {
		this.interp = interp;
		this.pgm = pgm;
		Variable.doHash = false;
	}
 
	void doFunction(int type) {
		switch (type) {
			case RUN: doRun(); break;
			case SELECT: IJ.selectWindow(getStringArg()); resetImage(); break;
			case WAIT: IJ.wait((int)getArg()); break;
			case BEEP: interp.getParens(); IJ.beep(); break;
			case RESET_MIN_MAX: interp.getParens(); IJ.resetMinAndMax(); resetImage(); break;
			case RESET_THRESHOLD: interp.getParens(); IJ.resetThreshold(); resetImage(); break;
			case PRINT: IJ.log(getStringArg()); break;
			case WRITE: IJ.write(getStringArg()); break;
			case DO_WAND: IJ.doWand((int)getFirstArg(), (int)getLastArg()); resetImage(); break;
			case SET_MIN_MAX: IJ.setMinAndMax(getFirstArg(), getLastArg()); resetImage(); break;
			case SET_THRESHOLD: IJ.setThreshold(getFirstArg(), getLastArg()); resetImage(); break;
			case SET_TOOL: IJ.setTool((int)getArg()); break;
			case SET_FOREGROUND: setForegroundColor(); break;
			case SET_BACKGROUND: setBackgroundColor(); break;
			case SET_COLOR: setColor(); break;
			case MAKE_LINE: makeLine(); break;
			case MAKE_OVAL: makeOval(); break;
			case MAKE_RECTANGLE: makeRectangle(); break;
			case DUMP: interp.dump(); break;
			case LINE_TO: lineTo(); break;
			case MOVE_TO: moveTo(); break;
			case DRAW_LINE: drawLine(); break;
			case REQUIRES: requires(); break;
			case AUTO_UPDATE: autoUpdate = getBooleanArg(); break;
			case UPDATE_DISPLAY: interp.getParens(); updateDisplay(); break;
			case DRAW_STRING: drawString(); break;
			case SET_PASTE_MODE: IJ.setPasteMode(getStringArg()); break;
			case DO_COMMAND: IJ.doCommand(getStringArg()); break;
			case SHOW_STATUS: IJ.showStatus(getStringArg()); interp.statusUpdated=true; break;
			case SHOW_PROGRESS: showProgress(); break;
			case SHOW_MESSAGE: showMessage(false); break;
			case SHOW_MESSAGE_WITH_CANCEL: showMessage(true); break;
			case SET_PIXEL: case PUT_PIXEL: setPixel(); break;
			case SNAPSHOT: case RESET: case FILL: doIPMethod(type); break;
			case SET_LINE_WIDTH: getProcessor().setLineWidth((int)getArg()); break;
			case CHANGE_VALUES: changeValues(); break;
			case SELECT_IMAGE: IJ.selectWindow((int)getArg()); resetImage(); break;
			case EXIT: exit(); break;
			case SET_LOCATION: getImage().getWindow().setLocation((int)getFirstArg(), (int)getLastArg());
			case GET_CURSOR_LOC: getCursorLoc(); break;
			case GET_LINE: getLine(); break;
			case GET_VOXEL_SIZE: getVoxelSize(); break;
			case GET_HISTOGRAM: getHistogram(); break;
			case GET_STATISTICS: getStatistics(); break;
			case GRAPH: graph(); break;
			case GET_BOUNDING_RECT: getBoundingRect(); break;
			case GET_LUT: getLut(); break;
			case SET_LUT: setLut(); break;
			case GET_COORDINATES: getCoordinates(); break;
		}
	}

	final double getFunctionValue(int type) {
		double value = 0.0;
		switch (type) {
			case GET_PIXEL: value = getPixel(); break;
			case ABS: case COS: case EXP: case FLOOR: case LOG: case ROUND: 
			case SIN: case SQRT: case TAN: case ATAN:
				value = math(type);
				break;
			case MAX_OF: case MIN_OF: case POW: value=math2(type); break;
			case GET_TIME: interp.getParens(); value=System.currentTimeMillis(); break;
			case GET_WIDTH: interp.getParens(); value=getImage().getWidth(); break;
			case GET_HEIGHT: interp.getParens(); value=getImage().getHeight(); break;
			case RANDOM: value=random(); break;
			case GET_COUNT: case NRESULTS: value=getResultsCount(); break;
			case GET_RESULT: value=getResult(); break;
			case GET_NUMBER: value=getNumber(); break;
			case NIMAGES: value=getImageCount(); break;
			case NSLICES: value=getStackSize(); break;
			case LENGTH_OF: value=lengthOf(); break;
			case GET_ID: interp.getParens(); value = getImage().getID(); break;
			case BIT_DEPTH: interp.getParens(); value = getImage().getBitDepth(); break;
			case SELECTION_TYPE: value=getSelectionType(); break;
			case IS_OPEN: value=isOpen(); break;
			case IS_ACTIVE: value=isActive(); break;
			case INDEX_OF: value=indexOf(); break;
			case LAST_INDEX_OF: value=getFirstStringArg().lastIndexOf(getLastStringArg()); break;
			case CHAR_CODE_AT: value=getFirstStringArg().charAt((int)getLastArg()); break;
			default:
				interp.error("Numeric function expected");
		}
		return value;
	}

	String getStringFunction(int type) {
		String str;
		switch (type) {
			case D2S: str = d2s(); break;
			case TO_HEX: str = toString(16); break;
			case TO_BINARY: str = toString(2); break;
			case GET_TITLE: interp.getParens(); str = getImage().getTitle(); break;
			case GET_STRING: str = getStringDialog(); break;
			case SUBSTRING: str = substring(); break;
			case FROM_CHAR_CODE: str = fromCharCode(); break;
			case GET_INFO: str = getInfo(); break;			
			default:
				str="";
				interp.error("String function expected");
		}
		return str;
	}

	Variable[] getArrayFunction(int type) {
		Variable[] array;
		switch (type) {
			case GET_PROFILE: array=getProfile(); break;
			case NEW_ARRAY: array = newArray(); break;
			case SPLIT: array = split(); break;
			default:
				array = null;
				interp.error("Array function expected");
		}
		return array;
	}

	final double math(int type) {
		double arg = getArg();
		switch (type) {
			case ABS: return Math.abs(arg);
			case COS: return Math.cos(arg);
			case EXP: return Math.exp(arg);
			case FLOOR: return Math.floor(arg);
			case LOG: return Math.log(arg);
			case ROUND: return Math.round(arg);
			case SIN: return Math.sin(arg);
			case SQRT: return Math.sqrt(arg);
			case TAN: return Math.tan(arg);
			case ATAN: return Math.atan(arg);
			default: return 0.0;
		}
	}

	final double math2(int type) {
		double a1 = getFirstArg();
		double a2 = getLastArg();
		switch (type) {
			case MIN_OF: return Math.min(a1, a2);
			case MAX_OF: return Math.max(a1, a2);
			case POW: return Math.pow(a1, a2);
			default: return 0.0;
		}
	}

	final String getString() {
		String str = interp.getStringTerm();
		while (true) {
			interp.getToken();
			if (interp.token=='+')
				str += interp.getStringTerm();
			else {
				interp.putTokenBack();
				break;
			}
		};
		return str;
	}

	final boolean isStringFunction() {
		Symbol symbol = pgm.table[interp.tokenAddress];
		return symbol.type==D2S;
	}

	final double getArg() {
		interp.getLeftParen();
		double arg = interp.getExpression();
		interp.getRightParen();
		return arg;
	}

	final double getFirstArg() {
		interp.getLeftParen();
		return interp.getExpression();
	}

	final double getNextArg() {
		interp.getComma();
		return interp.getExpression();
	}

	final double getLastArg() {
		interp.getComma();
		double arg = interp.getExpression();
		interp.getRightParen();
		return arg;
	}

	String getStringArg() {
		interp.getLeftParen();
		String arg = getString();
		interp.getRightParen();
		return arg;
	}

	final String getFirstStringArg() {
		interp.getLeftParen();
		return getString();
	}

	final String getNextStringArg() {
		interp.getComma();
		return getString();
	}

	final String getLastStringArg() {
		interp.getComma();
		String arg = getString();
		interp.getRightParen();
		return arg;
	}

	boolean getBooleanArg() {
		interp.getLeftParen();
		double arg = interp.getBooleanExpression();
		interp.checkBoolean(arg);
		interp.getRightParen();
		return arg==0?false:true;
	}

	final Variable getFirstVariable() {
		interp.getLeftParen();
		return getVariable();
	}

	final Variable getNextVariable() {
		interp.getComma();
		return getVariable();
	}

	final Variable getLastVariable() {
		interp.getComma();
		Variable v = getVariable();
		interp.getRightParen();
		return v;
	}

	final Variable getVariable() {
		interp.getToken();
		if (interp.token!=WORD)
			interp.error("Variable expected");
		Variable v = interp.lookupVariable(interp.tokenAddress);
		if (v==null)
				v = interp.push(interp.tokenAddress, 0.0, null, interp);
		Variable[] array = v.getArray();
		if (array!=null) {
			int index = interp.getIndex();
			checkIndex(index, 0, array.length-1);
			v = array[index]; 
		}
		return v;
	}

	final Variable getFirstArrayVariable() {
		interp.getLeftParen();
		return getArrayVariable();
	}

	final Variable getNextArrayVariable() {
		interp.getComma();
		return getArrayVariable();
	}

	final Variable getLastArrayVariable() {
		interp.getComma();
		Variable v = getArrayVariable();
		interp.getRightParen();
		return v;
	}

	final Variable getArrayVariable() {
		interp.getToken();
		if (interp.token!=WORD)
			interp.error("Variable expected");
		Variable v = interp.lookupVariable(interp.tokenAddress);
		if (v==null)
				v = interp.push(interp.tokenAddress, 0.0, null, interp);
		return v;
	}

	final double[] getFirstArray() {
		interp.getLeftParen();
		return getArray();
	}

	final double[] getNextArray() {
		interp.getComma();
		return getArray();
	}

	final double[] getLastArray() {
		interp.getComma();
		double[] a = getArray();
		interp.getRightParen();
		return a;
	}

	double[] getArray() {
		interp.getToken();
		if (interp.token!=WORD)
			interp.error("Array expected");
		Variable v = interp.lookupVariable(interp.tokenAddress);
		if (v==null)
			interp.error("Undefined variable");		
		Variable[] a1= v.getArray();
		if (a1==null)
			interp.error("Array expected");
		double[] a2 = new double[a1.length];
		for (int i=0; i<a1.length; i++)
			a2[i] = a1[i].getValue();
		return a2;
	}
	
	void checkIndex(int index, int lower, int upper) {
		if (index<lower || index>upper)
			interp.error("Index ("+index+") is outside of the "+lower+"-"+upper+" range");
	}

	void doRun() {
		interp.getLeftParen();
		String arg1 = getString();
		interp.getToken();
		if (!(interp.token==')' || interp.token==','))
			interp.error("',' or ')'  expected");
		String arg2 = null;
		if (interp.token==',') {
			arg2 = getString();
			interp.getRightParen();
		}
		if (arg2!=null)
			IJ.run(arg1, arg2);
		else
			IJ.run(arg1);
		resetImage();
	}

	void setForegroundColor() {
		IJ.setForegroundColor((int)getFirstArg(), (int)getNextArg(), (int)getLastArg());
		resetImage(); 
	}

	void setBackgroundColor() {
		IJ.setBackgroundColor((int)getFirstArg(), (int)getNextArg(), (int)getLastArg());
		resetImage(); 
	}

	void setColor() {
		double color = getFirstArg();
		colorSet = true;
		if (interp.nextToken()==')')
			{interp.getRightParen(); setColor(color); return;}
		int red=(int)color, green=(int)getNextArg(), blue=(int)getLastArg();
	    if (red<0) red=0; if (green<0) green=0; if (blue<0) blue=0; 
	    if (red>255) red=255; if (green>255) green=255; if (blue>255) blue=255;  
		Color c = new Color(red, green, blue);
		getProcessor().setColor(c);
	}
	
	void setColor(double color) {
		ImageProcessor ip = getProcessor();
		switch (imp.getBitDepth()) {
			case 8:
				if (color<0 || color>255)
					interp.error("Argument out of 8-bit range (0-255)");
				ip.setValue(color);
				break;
			case 16:
				if (color<0 || color>65535)
					interp.error("Argument out of 16-bit range (0-65535)");
				ip.setValue(color);
				break;
			default:
				ip.setValue(color);
				break;
		}
	}

	void makeLine() {
		IJ.makeLine((int)getFirstArg(), (int)getNextArg(), (int)getNextArg(), (int)getLastArg());
		resetImage(); 
	}

	void makeOval() {
		IJ.makeOval((int)getFirstArg(), (int)getNextArg(), (int)getNextArg(), (int)getLastArg());
		resetImage(); 
	}

	void makeRectangle() {
		IJ.makeRectangle((int)getFirstArg(), (int)getNextArg(), (int)getNextArg(), (int)getLastArg());
		resetImage(); 
	}
	
	ImagePlus getImage() {
		if (imp==null)
			imp = IJ.getImage();
		if (imp.getWindow()==null)
			throw new RuntimeException("Macro canceled");			
		return imp;
	}
	
	void resetImage() {
		imp = null;
		ip = null;
	}

	ImageProcessor getProcessor() {
		if (ip==null)
			ip = getImage().getProcessor();
		return ip;
	}

	int getType() {
		if (imp==null)
			imp = IJ.getImage();
		imageType = imp.getType();
		return imageType;
	}

	double getPixel() {
		interp.getLeftParen();
		int a1 = (int)interp.getExpression();
		interp.getComma();
		int a2 = (int)interp.getExpression();
		interp.getRightParen();
		double value = 0.0;
		ImageProcessor ip = getProcessor();
		if (getType()==ImagePlus.GRAY32)
			value = ip.getPixelValue(a1, a2);
		else
			value = ip.getPixel(a1, a2);
		return value;
	}

	void setPixel() {
		interp.getLeftParen();
		int a1 = (int)interp.getExpression();
		interp.getComma();
		int a2 = (int)interp.getExpression();
		interp.getComma();
		double a3 = interp.getExpression();
		interp.getRightParen();
		if (getType()==ImagePlus.GRAY32)
			getProcessor().putPixelValue(a1, a2, a3);
		else
			getProcessor().putPixel(a1, a2, (int)a3);
		updateNeeded = true;
	}

	void moveTo() {
		interp.getLeftParen();
		int a1 = (int)(interp.getExpression()+0.5);
		interp.getComma();
		int a2 = (int)(interp.getExpression()+0.5);
		interp.getRightParen();
		getProcessor().moveTo(a1, a2);
	}

	void lineTo() {
		interp.getLeftParen();
		int a1 = (int)(interp.getExpression()+0.5);
		interp.getComma();
		int a2 = (int)(interp.getExpression()+0.5);
		interp.getRightParen();
		ImageProcessor ip = getProcessor();
		if (!colorSet) setForegroundColor(ip);
		ip.lineTo(a1, a2);
		updateAndDraw(imp);
	}

	void drawLine() {
		interp.getLeftParen();
		int a1 = (int)(interp.getExpression()+0.5);
		interp.getComma();
		int a2 = (int)(interp.getExpression()+0.5);
		interp.getComma();
		int a3 = (int)(interp.getExpression()+0.5);
		interp.getComma();
		int a4 = (int)(interp.getExpression()+0.5);
		interp.getRightParen();
		ImageProcessor ip = getProcessor();
		if (!colorSet) setForegroundColor(ip);
		ip.drawLine(a1, a2, a3, a4);
		updateAndDraw(imp);
	}
	
	void setForegroundColor(ImageProcessor ip) {
		ip.setColor(Toolbar.getForegroundColor());
		colorSet = true;
	}

	void doIPMethod(int type) {
        interp.getParens(); 
		ImageProcessor ip = getProcessor();
		switch (type) {
			case SNAPSHOT: ip.snapshot(); break;
			case RESET: ip.reset(); break;
			case FILL: 
				ImagePlus imp = getImage();
				Roi roi = imp.getRoi();
				if (!colorSet) setForegroundColor(ip);
				if (roi==null) {
					ip.resetRoi();
					ip.fill();
				} else {
					ip.setRoi(roi.getBoundingRect());
					ip.fill(imp.getMask());
				}
				updateAndDraw(imp);
				break;
		}
	}

	void updateAndDraw(ImagePlus imp) {
		if (autoUpdate)
			imp.updateAndDraw();
		else
			updateNeeded = true;
	}
	
	void updateDisplay() {
		if (updateNeeded) {
			ImagePlus imp = getImage();
			imp.updateAndDraw();
			updateNeeded = false;
		}
	}

	void drawString() {
		interp.getLeftParen();
		String str = getString();
		interp.getComma();
		int x = (int)(interp.getExpression()+0.5);
		interp.getComma();
		int y = (int)(interp.getExpression()+0.5);
		interp.getRightParen();
		ImageProcessor ip = getProcessor();
		if (!colorSet) setForegroundColor(ip);
		ip.drawString(str, x, y);
		updateAndDraw(imp);
	}

	void changeValues() {
		double darg1 = getFirstArg();
		double darg2 = getNextArg();
		double darg3 = getLastArg();
		ImagePlus imp = getImage();
		ImageProcessor ip = getProcessor();
		Roi roi = imp.getRoi();
		int[] mask = null;
		if (roi==null || roi.getType()>Roi.TRACED_ROI) {
			ip.resetRoi();
			roi = null;
		} else {
			ip.setRoi(roi.getBoundingRect());
			mask = imp.getMask();
			ip.setMask(mask);
			if (mask!=null) ip.snapshot();
		}
		int xmin=0, ymin=0, xmax=imp.getWidth(), ymax=imp.getHeight();
		if (roi!=null) {
			Rectangle r = roi.getBoundingRect();
			xmin=r.x; ymin=r.y; xmax=r.x+r.width; ymax=r.y+r.height;
		}
		boolean isFloat = getType()==ImagePlus.GRAY32;
		double v;
		for (int y=ymin; y<ymax; y++) {
			for (int x=xmin; x<xmax; x++) {
				v = isFloat?ip.getPixelValue(x,y):ip.getPixel(x,y)&0xffffff;
				if (v>=darg1 && v<=darg2) {
					if (isFloat)
						ip.putPixelValue(x, y, darg3);
					else
						ip.putPixel(x, y, (int)darg3);
				}
			}
		}
		if (mask!=null) ip.reset(mask);
		if (imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32)
			ip.resetMinAndMax();
		imp.updateAndDraw();
		updateNeeded = false;
	}

	void requires() {
		if (IJ.versionLessThan(getStringArg()))
			interp.done = true;
	}

	Random ran;	
	double random() {
		interp.getParens(); 
 		if (ran==null)
			ran = new Random();
		return ran.nextDouble();
	}

	double getResult() {
		interp.getLeftParen();
		String kind = getString();
		interp.getComma();
		int row = (int)interp.getExpression();
		interp.getRightParen();
		ResultsTable rt = Analyzer.getResultsTable();
		int col = rt.getColumnIndex(kind);
		if (col==ResultsTable.COLUMN_NOT_FOUND)
			interp.error("'"+kind+"' not found");
		if (row<0 || row>=rt.getCounter())
			interp.error("Row ("+row+") out of range");
		//IJ.log(col+" "+row+" "+rt.getValue(col,row)+" "+rt);
   		return rt.getValue(col, row);
	}

	double getNumber() {
		interp.getLeftParen();
		String prompt = getString();
		interp.getComma();
		double defaultValue = interp.getExpression();
		interp.getRightParen();
		String title = interp.macroName!=null?interp.macroName:"";
		if (title.endsWith(" Options"))
			title = title.substring(0, title.length()-8);
		GenericDialog gd = new GenericDialog(title);
		int decimalPlaces = (int)defaultValue==defaultValue?0:2;
		gd.addNumericField(prompt, defaultValue, decimalPlaces);
		gd.showDialog();
		if (gd.wasCanceled()) {
			interp.done = true;
			return defaultValue;
		}
		double v = gd.getNextNumber();
		if (gd.invalidNumber())
			return defaultValue;
		else
			return v;
	}

	String getStringDialog() {
		interp.getLeftParen();
		String prompt = getString();
		interp.getComma();
		String defaultStr = getString();
		interp.getRightParen();
		
		String title = interp.macroName!=null?interp.macroName:"";
		if (title.endsWith(" Options"))
			title = title.substring(0, title.length()-8);
		GenericDialog gd = new GenericDialog(title);
		gd.addStringField(prompt, defaultStr, 20);
		gd.showDialog();
		String str = "";
		if (gd.wasCanceled())
			interp.done = true;
		else
			str = gd.getNextString();
   		return str;
	}

	String d2s() {
		return IJ.d2s(getFirstArg(), (int)getLastArg());
	}

	String toString(int base) {
		int arg = (int)getArg();
		if (base==2)
			return Integer.toBinaryString(arg);
		else
			return Integer.toHexString(arg);
	}
	
	double getStackSize() {
		if (interp.nextToken()=='(') interp.getParens();
		return getImage().getStackSize();
	}
	
	double getImageCount() {
		if (interp.nextToken()=='(') interp.getParens();
		return WindowManager.getWindowCount();
	}
	
	double getResultsCount() {
		if (interp.nextToken()=='(') interp.getParens();
		return Analyzer.getResultsTable().getCounter();
	}

	void getCoordinates() {
		Variable xCoordinates = getFirstArrayVariable();
		Variable yCoordinates = getLastArrayVariable();
		resetImage();
		ImageProcessor ip = getProcessor();
		Roi roi = imp.getRoi();
		if (roi==null || !(roi instanceof PolygonRoi))
			interp.error("Polygonal selection required");
		int[] x = ((PolygonRoi)roi).getXCoordinates();
		int[] y = ((PolygonRoi)roi).getYCoordinates();
		int n = ((PolygonRoi)roi).getNCoordinates();
		Rectangle r = roi.getBoundingRect();
		int[] x2 = new int[n];
		int[] y2 = new int[n];
		for (int i=0; i<n; i++) {
			x2[i] = r.x+x[i];
			y2[i] = r.y+y[i];
		}			
		xCoordinates.setArray(new Variable(x2).getArray());
		yCoordinates.setArray(new Variable(y2).getArray());
	}
	
	Variable[] getProfile() {
		interp.getParens();
		ImagePlus imp = getImage();
		ProfilePlot pp = new ProfilePlot(imp, false);
		double[] array = pp.getProfile();
		if (array==null)
			{interp.done=true; return null;}
		else
			return new Variable(array).getArray();
	}

	Variable[] newArray() {
		interp.getLeftParen();
		if (interp.nextNonEolToken()==STRING_CONSTANT || interp.nextNextNonEolToken()==',')
			return initNewArray();
		int size = (int)interp.getExpression();
		interp.getRightParen();
    	Variable[] array = new Variable[size];
    	for (int i=0; i<size; i++)
    		array[i] = new Variable();
    	return array;
	}
	
	Variable[] split() {
		String s1 = getFirstStringArg();
		String s2 = getLastStringArg();
		StringTokenizer t = s2.equals("")?new StringTokenizer(s1):new StringTokenizer(s1, s2);
		int tokens = t.countTokens();
		String[] strings;
		if (tokens>0) {
       		strings = new String[tokens];
        	for(int i=0; i<tokens; i++) 
        		strings[i] = t.nextToken();
        } else {
        	strings = new String[1];
        	strings[0] = s1;
        	tokens = 1;
        }
    	Variable[] array = new Variable[tokens];
    	for (int i=0; i<tokens; i++)
    		array[i] = new Variable(0, 0.0, strings[i]);
    	return array;
	}

	Variable[] initNewArray() {
		Vector vector = new Vector();
		int size = 0;
		do {
		    Variable v = new Variable();
			if (interp.nextNonEolToken()==STRING_CONSTANT)
				v.setString(getString());
			else
				v.setValue(interp.getExpression());
			vector.addElement(v);
			size++;
			interp.getToken();				
		} while (interp.token==',');
		if (interp.token!=')')
			interp.error("';' expected");
    	Variable[] array = new Variable[size];
		vector.copyInto((Variable[])array);
    	return array;
	}

	String fromCharCode() {
		char[] chars = new char[100];
		int count = 0;
		interp.getLeftParen();
		while(interp.nextToken()!=')') {
			int value = (int)interp.getExpression();
			if (value<0 || value>65535)
				interp.error("Value (" + value + ") out of 0-65535 range");
			chars[count++] = (char)value;
			if (interp.nextToken()==',')
				interp.getToken();
		}
		interp.getRightParen();		
    	return new String(chars);
	}

	public String getInfo() {
		interp.getParens();
		Frame frame = WindowManager.getFrontWindow();
		if (frame!=null && frame instanceof TextWindow) {
			TextPanel tp = ((TextWindow)frame).getTextPanel();
			return tp.getText();			
		} else if (frame!=null && frame instanceof Editor)
			return ((Editor)frame).getText();			
		else {
			ImagePlus imp = getImage();
			Info infoPlugin = new Info();
			return infoPlugin.getImageInfo(imp, getProcessor());
		}		
	}

	double getSelectionType() {
		interp.getParens();
		double type = -1;
		ImagePlus imp = getImage();
		Roi roi = imp.getRoi();
		if (roi!=null)
			type = roi.getType();
		return type;
	}

	void showMessage(boolean withCancel) {
		String message;
		interp.getLeftParen();
		String title = getString();
		if (interp.nextToken()==',') {
			interp.getComma();
			message = getString();
		} else {
			message = title;
			title = "";
		}
		interp.getRightParen();
		if (withCancel)
			IJ.showMessageWithCancel(title, message);
		else
			IJ.showMessage(title, message);
	}
	
	double lengthOf() {
		int length = 0;
		interp.getLeftParen();
		switch (interp.nextToken()) {
			case STRING_CONSTANT:
			case STRING_FUNCTION:
			case USER_FUNCTION:
				length = getString().length();
				break; 
			case WORD:
				interp.getToken();
				Variable v = interp.lookupNumericVariable();
				if (v==null)
					return 0.0;
				String s = v.getString();
				if (s!=null)
					length = s.length();
				else {
					Variable[] array = v.getArray();
					if (array!=null)
						length = array.length;
					else
						interp.error("String or array expected");
				}					
				break;
			default:
				interp.error("String or array expected");
		}
		interp.getRightParen();
		return length;
	}
	
	void getCursorLoc() {
		Variable x = getFirstVariable();
		Variable y = getNextVariable();
		Variable z = getNextVariable();
		Variable flags = getLastVariable();
		ImagePlus imp = getImage();
		ImageWindow win = imp.getWindow();
		ImageCanvas ic = win.getCanvas();
		Point p = ic.getCursorLoc();
		x.setValue(p.x);
		y.setValue(p.y);
		z.setValue(imp.getCurrentSlice()-1);
		flags.setValue(ic.getModifiers());
		
	}
	
	void getLine() {
		Variable vx1 = getFirstVariable();
		Variable vy1 = getNextVariable();
		Variable vx2 = getNextVariable();
		Variable vy2 = getNextVariable();
		Variable lineWidth = getLastVariable();
		resetImage();
		ImagePlus imp = getImage();
		int x1=-1, y1=-1, x2=-1, y2=-1;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.getType()==Roi.LINE) {
			Line line = (Line)roi;
			x1=line.x1; y1=line.y1; x2=line.x2; y2=line.y2;
		}
		vx1.setValue(x1);
		vy1.setValue(y1);
		vx2.setValue(x2);
		vy2.setValue(y2);				
		lineWidth.setValue(Line.getWidth());				
	}
	
	void getVoxelSize() {
		Variable width = getFirstVariable();
		Variable height = getNextVariable();
		Variable depth = getNextVariable();
		Variable unit = getLastVariable();
		resetImage();
		ImagePlus imp = getImage();
		Calibration cal = imp.getCalibration();
		width.setValue(cal.pixelWidth);
		height.setValue(cal.pixelHeight);
		depth.setValue(cal.pixelDepth);
		unit.setString(cal.getUnit());
	}
	
	void getHistogram() {
		interp.getLeftParen();
		Variable values = null;
		if (interp.nextToken()==NUMBER)
			interp.getExpression();
		else
			values = getArrayVariable();
		Variable counts = getNextArrayVariable();
		int nBins = (int)getLastArg();
		ImagePlus imp = getImage();
		int bitDepth = imp.getBitDepth();
		if (bitDepth!=32 && nBins!=256)
			interp.error("Bin count ("+nBins+") must be 256 for non-float images");
		ImageStatistics stats = imp.getStatistics(AREA+MEAN+MODE+MIN_MAX, nBins);
		if (values!=null) {
			Calibration cal = imp.getCalibration();
			double[] array = new double[nBins];
			double value = cal.getCValue(stats.histMin);
			double inc = 1.0;
			if (bitDepth==16 || bitDepth==32 || cal.calibrated())
				inc = (cal.getCValue(stats.histMax) - cal.getCValue(stats.histMin))/stats.nBins;
			for (int i=0; i<nBins; i++) {
				array[i] = value;
				value += inc;
			}
			values.setArray(new Variable(array).getArray());
		}
		Variable[] array = counts.getArray();
		if (array!=null && array.length==nBins) {
			for (int i=0; i<nBins; i++)
				array[i].setValue(stats.histogram[i]);
		} else
			counts.setArray(new Variable(stats.histogram).getArray());
	}
	
	void getLut() {
		Variable reds = getFirstArrayVariable();
		Variable greens = getNextArrayVariable();
		Variable blues = getLastArrayVariable();
		resetImage();
		ImageProcessor ip = getProcessor();
		if (ip instanceof ColorProcessor)
			interp.error("Non-RGB image expected");
		IndexColorModel cm = (IndexColorModel)ip.getColorModel();
		int mapSize = cm.getMapSize();
		byte[] rLUT = new byte[mapSize];
		byte[] gLUT = new byte[mapSize];
		byte[] bLUT = new byte[mapSize];
		cm.getReds(rLUT); 
		cm.getGreens(gLUT); 
		cm.getBlues(bLUT); 
		reds.setArray(new Variable(rLUT).getArray());
		greens.setArray(new Variable(gLUT).getArray());
		blues.setArray(new Variable(bLUT).getArray());
	}

	void setLut() {
		double[] reds = getFirstArray();
		double[] greens = getNextArray();
		double[] blues = getLastArray();
		int length = reds.length;		
		if (greens.length!=length || blues.length!=length)
			interp.error("Arrays are not the same length");
		resetImage();
		ImagePlus imp = getImage();
		if (imp.getBitDepth()==24)
			interp.error("Non-RGB image expected");
		ImageProcessor ip = getProcessor();
		byte[] r = new byte[length];
		byte[] g = new byte[length];
		byte[] b = new byte[length];
		for (int i=0; i<length; i++) {
			r[i] = (byte)reds[i];
			g[i] = (byte)greens[i];
			b[i] = (byte)blues[i];
		}		
		ip.setColorModel(new IndexColorModel(8, length, r, g, b));
		imp.updateAndDraw();
		updateNeeded = false;
	}

	void graph() {
		interp.getLeftParen();
		String title = getString();
		interp.getComma();
		double[] x = getArray();
		interp.getComma();
		double[] y = getArray();
		interp.getComma();
		String xLabel = getString();
		interp.getComma();
		String yLabel = getString();
		interp.getRightParen();
		PlotWindow pw = new PlotWindow(title, xLabel, yLabel, x, y);
		pw.draw();									
	}

	void getBoundingRect() {
		Variable x = getFirstVariable();
		Variable y = getNextVariable();
		Variable width = getNextVariable();
		Variable height = getLastVariable();
		resetImage();
		ImagePlus imp = getImage();
		Roi roi = imp.getRoi();
		if (roi!=null) {
			Rectangle r = roi.getBoundingRect();
			x.setValue(r.x);
			y.setValue(r.y);
			width.setValue(r.width);
			height.setValue(r.height);
		} else {
			x.setValue(0);
			y.setValue(0);
			width.setValue(imp.getWidth());
			height.setValue(imp.getHeight());
		}
	}

	void getStatistics() {
	}
	
	String substring() {
		String s = getFirstStringArg();
		int index1 = (int)getNextArg();
		int index2 = (int)getLastArg();
		if (index1>index2)
			interp.error("beginIndex>endIndex");
		checkIndex(index1, 0, s.length());
		checkIndex(index2, 0, s.length());
		return s.substring(index1, index2);
	}

	int indexOf() {
		String s1 = getFirstStringArg();
		String s2 = getNextStringArg();
		int fromIndex = 0;
		if (interp.nextToken()==',') {
			fromIndex = (int)getLastArg();
			checkIndex(fromIndex, 0, s1.length()-1);
		} else
			interp.getRightParen();			
		if (fromIndex==0)
			return s1.indexOf(s2);
		else
			return s1.indexOf(s2, fromIndex);
	}

	double isActive() {
		int id = (int)getArg();
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || imp.getID()!=id)
			return 0.0; //false
		else
			return 1.0; //true
	}
	
	double isOpen() {
		interp.getLeftParen();
		if (interp.nextToken()==STRING_CONSTANT) {
			String title = getString();
			interp.getRightParen();
			return WindowManager.getFrame(title)==null?0.0:1.0;
		} else {
			int id = (int)interp.getExpression();
			interp.getRightParen();
			return WindowManager.getImage(id)==null?0.0:1.0;
		}
	}

	void exit() {
		String msg = null;
		if (interp.nextToken()=='(') {
			interp.getLeftParen();
			if (interp.nextToken()==STRING_CONSTANT || interp.nextToken()==STRING_FUNCTION)
				msg = getString();
			interp.getRightParen();
		}
		IJ.showStatus("");
		IJ.showProgress(1.0);
		if (msg!=null)
			IJ.showMessage("Macro", msg);
		throw new RuntimeException("Macro canceled");
	}
	
	void showProgress() {
		interp.getLeftParen();
		double arg1 = interp.getExpression();
		if (interp.nextToken()==',') {
			interp.getComma();
			double arg2 = interp.getExpression();
			IJ.showProgress((int)arg1, (int)arg2);						
		} else 
			IJ.showProgress(arg1);			
		interp.getRightParen();
		interp.showingProgress = true; 	
	}
	
} // class Functions





