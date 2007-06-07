package ij.macro;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.*;
import java.awt.*;
import java.util.*;

/** This class implements the built-in macro functions. */
public class Functions implements MacroConstants {
	Interpreter interp;
	Program pgm;
    boolean updateNeeded;
    boolean autoUpdate = true;
    ImagePlus imp;
    ImageProcessor ip;
    int imageType;

	Functions(Interpreter interp, Program pgm) {
		this.interp = interp;
		this.pgm = pgm;
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
			case SHOW_PROGRESS: IJ.showProgress(getArg()); interp.showingProgress=true; break;
			case SHOW_MESSAGE: showMessage(false); break;
			case SET_PIXEL: case PUT_PIXEL: setPixel(); break;
			case SNAPSHOT: case RESET: case FILL: doIPMethod(type); break;
			case SET_LINE_WIDTH: getProcessor().setLineWidth((int)getArg()); break;
			case CHANGE_VALUES: changeValues(); break;
			case SET_IMAGE: setImage(); break;
		}
	}

	final double getFunctionValue(int type) {
		double value = 0.0;
		switch (type) {
			case GET_PIXEL: value = getPixel(); break;
			case ABS: case COS: case EXP: case FLOOR: case LOG: case ROUND: case SIN: case SQRT: case TAN:
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
			case SHOW_MESSAGE_WITH_CANCEL: value=showMessage(true); break;
			case LENGTH_OF: value=lengthOf(); break;
			case NCOORDINATES: value=getCoordinateCount(); break;
			case XCOORDINATES: case YCOORDINATES: value=getCoordinate(type); break;
			case GET_ID: interp.getParens(); value = getImage().getID(); break;
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
			default:
				str="";
				interp.error("String function expected");
		}
		return str;
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

	boolean getBooleanArg() {
		interp.getLeftParen();
		double arg = interp.getBooleanExpression();
		interp.checkBoolean(arg);
		interp.getRightParen();
		return arg==0?false:true;
	}

	int getIndex() {
		interp.getToken();
		if (interp.token!='[')
			interp.error("'['expected");
		int index = (int)interp.getExpression();
		interp.getToken();
		if (interp.token!=']')
			interp.error("']' expected");
		return index;
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
		int red=(int)getFirstArg(), green=(int)getNextArg(), blue=(int)getLastArg();
	    if (red<0) red=0; if (green<0) green=0; if (blue<0) blue=0; 
	    if (red>255) red=255; if (green>255) green=255; if (blue>255) blue=255;  
		Color c = new Color(red, green, blue);
		getProcessor().setColor(c);
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
		getProcessor().lineTo(a1, a2);
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
		getProcessor().drawLine(a1, a2, a3, a4);
		updateAndDraw(imp);
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
		getProcessor().drawString(str, x, y);
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

	void setImage() {
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
		double value = IJ.getNumber(prompt, defaultValue);
		if (value==IJ.CANCELED) {
			interp.done = true;
			value = defaultValue;
		}
   		return value;
	}

	String getStringDialog() {
		interp.getLeftParen();
		String prompt = getString();
		interp.getComma();
		String defaultStr = getString();
		interp.getRightParen();
		String str = IJ.getString(prompt, defaultStr);
		if (str.equals("")) {
			interp.done = true;
			str = defaultStr;
		}
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
	
	double getCoordinateCount() {
		if (interp.nextToken()=='(') interp.getParens();
		ImagePlus imp = getImage();
		Roi roi = imp.getRoi();
		if (roi==null)
			return 0.0;
		if (roi instanceof PolygonRoi)
			return ((PolygonRoi)roi).getNCoordinates();
		else
			return 0.0;
	}

	double getResultsCount() {
		if (interp.nextToken()=='(') interp.getParens();
		return Analyzer.getResultsTable().getCounter();
	}

	double getCoordinate(int type) {
		int index = getIndex();
		ImagePlus imp = getImage();
		Roi roi = imp.getRoi();
		if (roi!=null && roi instanceof PolygonRoi) {
			int n = ((PolygonRoi)roi).getNCoordinates();
			checkIndex(index, 0, n-1);
			Rectangle r = roi.getBoundingRect();
			if (type==XCOORDINATES) {
				int[] x = ((PolygonRoi)roi).getXCoordinates();
				return r.x + x[index];
			} else {
				int[] y = ((PolygonRoi)roi).getYCoordinates();
				return r.y + y[index];
			}
		} else {
			interp.error("Polygonal ROI required");
			return 0.0;
		}
	}

	double showMessage(boolean withCancel) {
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
			return IJ.showMessageWithCancel(title, message)?1.0:0.0;
		else {
			IJ.showMessage(title, message);
			return 0.0;
		}
	}
	
	double lengthOf() {
		String s = getStringArg();
		return s.length();
	}
	
} // class Functions





