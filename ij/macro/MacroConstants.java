package ij.macro;

public interface MacroConstants {

	static final int PLUS_PLUS=1, MINUS_MINUS=2, EQ=3, NEQ=4, GT=5, GTE=6, LT=7, LTE=8,
		PLUS_EQUAL=9, MINUS_EQUAL=10, MUL_EQUAL=11, DIV_EQUAL=12, LOGICAL_AND=13, LOGICAL_OR=14,
		SHIFT_RIGHT=15, SHIFT_LEFT=16;

	// Token types
	public static final int EOF=128, WORD=129, NUMBER=130, NOP=131, EOL=132, STRING_CONSTANT=133, 
		PREDEFINED_FUNCTION=134, NUMERIC_FUNCTION=135, STRING_FUNCTION=136, USER_FUNCTION=137, ARRAY=138;

	// Keywords
	static final String[] keywords = {"macro", "var", "if", "else", "while", "do", "for", "function",
		"return", "break", "continue", "switch", "case", "true", "false", "PI"};
	public static final int MACRO=200, VAR=201, IF=202, ELSE=203, WHILE=204, DO=205, FOR=206, FUNCTION=207,
		RETURN=208, BREAK=209, CONTINUE=210, SWITCH=211, CASE=212, TRUE=213, FALSE=214, PI=215;
	static final int[] keywordIDs = {MACRO, VAR, IF, ELSE, WHILE, DO, FOR, FUNCTION,
		RETURN, BREAK, CONTINUE, SWITCH, CASE, TRUE, FALSE, PI};

	// Functions that don't return a value
	static final int RUN=300, INVERT=301, SELECT=302, WAIT=303, BEEP=304, RESET_MIN_MAX=305, RESET_THRESHOLD=306,
		PRINT=307, WRITE=308, DO_WAND=309, SET_MIN_MAX=310, SET_THRESHOLD=311, SET_TOOL=312,
		SET_FOREGROUND=313, SET_BACKGROUND=314, MAKE_LINE=315, MAKE_OVAL=316, MAKE_RECTANGLE=317,
		DUMP=318, MOVE_TO=319, LINE_TO=320, DRAW_LINE=321, REQUIRES=322, AUTO_UPDATE=323, UPDATE_DISPLAY=324, DRAW_STRING=325,
		SET_PASTE_MODE=326, DO_COMMAND=327, SHOW_STATUS=328, SHOW_PROGRESS=329, SHOW_MESSAGE=330, PUT_PIXEL=331, SET_PIXEL=332,
		SNAPSHOT=333, RESET=334, FILL=335, SET_COLOR=336, SET_LINE_WIDTH=337, CHANGE_VALUES=338, SET_IMAGE=339;
	static final String[] functions = {"run","invert","selectWindow","wait", "beep", "resetMinAndMax", "resetThreshold",
		"print", "write", "doWand", "setMinAndMax", "setThreshold", "setTool",
		"setForegroundColor", "setBackgroundColor", "makeLine", "makeOval", "makeRectangle",
		"dump", "moveTo", "lineTo", "drawLine", "requires", "autoUpdate", "updateDisplay", "drawString",
		"setPasteMode", "doCommand", "showStatus", "showProgress", "showMessage", "putPixel", "setPixel",
		"snapshot", "reset", "fill", "setColor", "setLineWidth", "changeValues", "setImage"};
	static final int[] functionIDs = {RUN, INVERT, SELECT, WAIT, BEEP, RESET_MIN_MAX, RESET_THRESHOLD,
		PRINT, WRITE,	 DO_WAND, SET_MIN_MAX, SET_THRESHOLD, SET_TOOL,
		SET_FOREGROUND, SET_BACKGROUND, MAKE_LINE, MAKE_OVAL, MAKE_RECTANGLE,
		DUMP, MOVE_TO, LINE_TO, DRAW_LINE, REQUIRES, AUTO_UPDATE, UPDATE_DISPLAY, DRAW_STRING,
		SET_PASTE_MODE, DO_COMMAND, SHOW_STATUS, SHOW_PROGRESS, SHOW_MESSAGE, PUT_PIXEL, SET_PIXEL,
		SNAPSHOT, RESET, FILL, SET_COLOR, SET_LINE_WIDTH, CHANGE_VALUES, SET_IMAGE};

	// Numeric functions
	static final int GET_PIXEL=1000, ABS=1001, COS=1002, EXP=1003, FLOOR=1004, LOG=1005, MAX_OF=1006, MIN_OF=1007, POW=1008,
		ROUND=1009, SIN=1010, SQRT=1011, TAN=1012, GET_TIME=1013, GET_WIDTH=1014, GET_HEIGHT=1015, RANDOM=1016,
		GET_RESULT=1017, GET_COUNT=1018, GET_NUMBER=1019, NIMAGES=1020, NSLICES=1021, SHOW_MESSAGE_WITH_CANCEL=1022,
		LENGTH_OF=1023, NCOORDINATES=1024, XCOORDINATES=1025, YCOORDINATES=1026, NRESULTS=1027, GET_ID=1028;
	static final String[] numericFunctions = { "getPixel", "abs", "cos", "exp", "floor", "log", "maxOf", "minOf", "pow",
		"round", "sin", "sqrt", "tan", "getTime", "getWidth", "getHeight", "random",
		"getResult", "getResultsCount", "getNumber", "nImages", "nSlices", "showMessageWithCancel",
		"lengthOf", "nCoordinates", "xCoordinates", "yCoordinates", "nResults", "getID"};
	static final int[] numericFunctionIDs = {GET_PIXEL, ABS, COS, EXP, FLOOR, LOG, MAX_OF, MIN_OF, POW,
		ROUND, SIN, SQRT, TAN, GET_TIME, GET_WIDTH, GET_HEIGHT, RANDOM,
		GET_RESULT, GET_COUNT, GET_NUMBER, NIMAGES, NSLICES, SHOW_MESSAGE_WITH_CANCEL,
		LENGTH_OF, NCOORDINATES, XCOORDINATES, YCOORDINATES, NRESULTS, GET_ID};

	// String functions
	static final int D2S=2000, TO_HEX=2001, TO_BINARY=2002, GET_TITLE=2003, GET_STRING=2004;
	static final String[] stringFunctions = {"d2s", "toHex", "toBinary", "getTitle", "getString"};
	static final int[] stringFunctionIDs = {D2S, TO_HEX, TO_BINARY, GET_TITLE, GET_STRING};

}  // interface MacroConstants
