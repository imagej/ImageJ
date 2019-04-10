package ij.macro;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.Macro_Runner;
import ij.plugin.frame.*;
import ij.util.Tools;
import ij.text.*;
import ij.measure.ResultsTable;
import java.awt.*;
import java.util.*;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;


/** This is the recursive descent parser/interpreter for the ImageJ macro language. */
public class Interpreter implements MacroConstants {

	static final int STACK_SIZE=1000;
	static final int MAX_ARGS=20;

	int pc;
	int token;
	int tokenAddress;
	double tokenValue;
	String tokenString;
	boolean looseSyntax = true;
	int lineNumber;
	boolean statusUpdated;
	boolean showingProgress;
	boolean keysSet;
	boolean checkingType;
	int prefixValue;
	
	Variable[] stack;
	int topOfStack = -1;
	int topOfGlobals = -1;
	int startOfLocals = 0;

	static volatile Interpreter instance, previousInstance;
	public static boolean batchMode;
	static Vector imageTable; // images opened in batch mode
	static Vector imageActivations; // images ordered by activation time
	volatile boolean done;
	Program pgm;
	Functions func;
	boolean inFunction;
	String macroName;
	String argument;
	String returnValue;
	boolean calledMacro; // macros envoked by eval() or runMacro()
	boolean batchMacro; // macros envoked by Process/Batch commands
	double[] rgbWeights;
	boolean inPrint;
	static String additionalFunctions;
	Debugger debugger;
	int debugMode = Debugger.NOT_DEBUGGING;
	boolean showDebugFunctions;
	static boolean showVariables;
	boolean wasError;
	ImagePlus batchMacroImage;
	boolean inLoop;
	int loopDepth;
	static boolean tempShowMode;
	boolean waitingForUser;
	int selectCount;
	
	static TextWindow arrayWindow;
	int inspectStkIndex = -1;
	int inspectSymIndex = -1;
	boolean evaluating;
	ResultsTable applyMacroTable;
	int errorCount;
	volatile boolean ignoreErrors;
	String errorMessage;
	String evalOutput;

	/** Interprets the specified string. */
	public void run(String macro) {
		if (additionalFunctions!=null) {
			if (!(macro.endsWith("\n")|| additionalFunctions.startsWith("\n")))
				macro = macro + "\n" + additionalFunctions;
			else
				macro = macro + additionalFunctions;
		}
		IJ.resetEscape();
		Tokenizer tok = new Tokenizer();
		Program pgm = tok.tokenize(macro);
		if (pgm.hasVars && pgm.hasFunctions)
			saveGlobals2(pgm);
		run(pgm);
	}
	
	/** Runs the specified macro, passing it a string 
		argument and returning a string value. */
	public String run(String macro, String arg) {
		argument = arg;
		calledMacro = true;
		if (IJ.getInstance()==null)
			setBatchMode(true);
		Interpreter saveInstance = instance;
		run(macro);
		instance = saveInstance;
		return returnValue;
	}
	
	/** Evaluates 'code' and returns the output, or any error, as a String.
	 * @see ij.Macro#eval
	*/
	public String eval(String code) {
		Interpreter saveInstance = instance;
		if (pgm!=null)
			reuseSymbolTable();
		Tokenizer tok = new Tokenizer();
		Program pgm = tok.tokenize(code);
		if (pgm.hasVars && pgm.hasFunctions)
			saveGlobals2(pgm);
		evaluating = true;
		evalOutput = null;
		ignoreErrors = true;
		calledMacro = true;
		run(pgm);
		instance = saveInstance;
		if (errorMessage!=null)
			return errorMessage;
		else
			return evalOutput;
	}
	
	private void reuseSymbolTable() {
		if (pgm==null)
			return;
		Symbol[] table1 = pgm.getSymbolTable();
		Symbol[] table2 = new Symbol[pgm.stLoc+1];
		for (int i=0; i<=pgm.stLoc; i++)
			table2[i] = table1[i];
		Program.systemTable = table2;
	}
	
	/** Interprets the specified tokenized macro file starting at location 0. */
	public void run(Program pgm) {
		this.pgm = pgm;
		pc = -1;
		instance = this;
		if (!calledMacro) {
			batchMode = false;
			imageTable = imageActivations = null;
		}
		pushGlobals();
		if (func==null)
			func = new Functions(this, pgm);
		else
			func.pgm = pgm;
		func.plot = null;
		done = false;
		errorMessage = null;
		doStatements();
		finishUp();
	}

	/** Runs an existing macro starting at the specified program counter location. */
	public void run(int location) {
		topOfStack = topOfGlobals;
		done = false;
		pc = location-1;
		doStatements();
	}

	/** Interprets the specified tokenized macro starting at the specified location. */
	public void runMacro(Program pgm, int macroLoc, String macroName) {
		calledMacro = true;
		this.pgm = pgm;
		this.macroName = macroName;
		pc = macroLoc-1;
		previousInstance = instance;
		instance = this;
		pushGlobals();
		if (func==null)
			func = new Functions(this, pgm);
		func.plot = null;
		if (macroLoc==0)
			doStatements();
		else
			doBlock(); 
		finishUp();
		Recorder.recordInMacros = false;
	}
	
	/** Runs Process/Batch/ macros. */
	public ImagePlus runBatchMacro(String macro, ImagePlus imp) {
		calledMacro = true;
		batchMacro = true;
		setBatchMode(true);
		addBatchModeImage(imp);
		batchMacroImage = null;
		run(macro);
		IJ.showStatus("");
		return batchMacroImage;
	}

	/** Saves global variables. */
	public void saveGlobals(Program pgm) {
		Interpreter saveInstance = instance;
		saveGlobals2(pgm);
		instance = saveInstance; 
	}
	
	void saveGlobals2(Program pgm) {
		this.pgm = pgm;
		pc = -1;
		instance = this;
		func = new Functions(this, pgm);
		while (!done) {
			getToken();
			switch (token) {
				case VAR: doVar(); break;
				case MACRO: skipMacro(); break;
				case FUNCTION: skipFunction(); break;
				default:
			}
		}
		instance = null;
		pgm.saveGlobals(this);
		pc = -1;
		topOfStack = -1;
		done = false;
	}

	final void getToken() {
		if (done) return;
		token = pgm.code[++pc];
		//IJ.log(pc+" "+pgm.decodeToken(token));
		if (token<=127)
			return;
		tokenAddress = token>>TOK_SHIFT;
		token = token&TOK_MASK;
		Symbol sym = pgm.table[tokenAddress];
		tokenString = sym.str;
		tokenValue = sym.value;
		done = token==EOF;
	}

	final int nextToken() {
		return pgm.code[pc+1]&TOK_MASK;
	}

	final int nextNextToken() {
		return pgm.code[pc+2]&TOK_MASK;
	}

	final void putTokenBack() {
		pc--;
		if (pc<0)
			pc = -1;
		if (token==EOF)
			done = false;
	}

	void doStatements() {
		while (!done)
			doStatement();
	}

	final void doStatement() {
		getToken();
		if (debugMode!=Debugger.NOT_DEBUGGING && debugger!=null && !done && token!=';' && token!=FUNCTION) {
			debugger.debug(this, debugMode);
			if (done) return;
		}
		switch (token) {
			case VAR:
				doVar();
				break;
			case PREDEFINED_FUNCTION:
				func.doFunction(pgm.table[tokenAddress].type);
				break;
			case VARIABLE_FUNCTION:
				func.getVariableFunction(pgm.table[tokenAddress].type);
				break;
			case USER_FUNCTION:
				runUserFunction();
				break;
			case RETURN:
				doReturn();
				break;
			case BREAK:
				if (inLoop) throw new MacroException(BREAK);
				break;
			case CONTINUE:
				if (inLoop) throw new MacroException(CONTINUE);
				break;
			case WORD:
				doAssignment();
				break;
			case IF:
				doIf();
				return;
			case ELSE:
				error("Else without if");
				return;
			case FOR:
				doFor();
				return;
			case WHILE:
				doWhile();
				return;
			case DO:
				doDo();
				return;
			case MACRO:
				runFirstMacro();
				return;
			case FUNCTION:
				skipFunction();
				return;
			case ';':
				return;
			case '{':
				putTokenBack();
				doBlock();
				return;
			case NUMBER:
			case NUMERIC_FUNCTION:
			case STRING_FUNCTION:
			case STRING_CONSTANT:
			case '(': 
				putTokenBack();
				inPrint = true;
				String s = getString();
				inPrint = false;
				if (s!=null && s.length()>0 && !s.equals("NaN") && !s.equals("[aborted]"))
					log(s);
				return;
			case ARRAY_FUNCTION: func.getArrayFunction(pgm.table[tokenAddress].type); break;
			case EOF: break;
			default:
				if (evaluating && token==PI) {
					putTokenBack();
					log(""+getExpression());
				} else
					error("Statement cannot begin with '"+pgm.decodeToken(token, tokenAddress)+"'");
		}
		if (!looseSyntax) {
			getToken();
			if (token!=';' && !done)
				error("';' expected");
		}
	}
	
	void log(String s) {
		if (evaluating)
			evalOutput = s;
		else
			IJ.log(s);
	}

	Variable runUserFunction() {
		int newPC = (int)tokenValue;
		int saveStartOfLocals = startOfLocals;
		startOfLocals = topOfStack+1;
		int saveTOS = topOfStack;		
		int nArgs = pushArgs();
		int savePC = pc;
		Variable value = null;
		pc = newPC;
		setupArgs(nArgs);
		boolean saveInFunction = inFunction;
		inFunction = true;
		try {
			doBlock();
		} catch (ReturnException e) {
			value = new Variable(0, e.value, e.str, e.array);
			if (value.getArray()!=null && e.arraySize!=0)
				value.setArraySize(e.arraySize);
		}
		inFunction = saveInFunction;
		pc = savePC;
		trimStack(saveTOS, saveStartOfLocals);
		return value;
	}

	/** Push function arguments onto the stack. */
	int pushArgs() {
		getLeftParen();
		int count = 0;
		Variable[] args = new Variable[MAX_ARGS];
		double value;
		if (nextToken()!=')') {
			do {
				if (count==MAX_ARGS)
					error("Too many arguments");
				int next = nextToken();
				int nextPlus = pgm.code[pc+2]&0xff;
				if (next==STRING_CONSTANT || next==STRING_FUNCTION)
					args[count] = new Variable(0, 0.0, getString());
				else if (next==USER_FUNCTION) {
					int savePC = pc;
					getToken(); // the function
					boolean simpleFunctionCall = isSimpleFunctionCall(false);
					pc = savePC;
					if (simpleFunctionCall) {
						getToken(); // the function
						Variable v2 = runUserFunction();
						if (v2==null)
							error("No return value");
						args[count] = v2;
					} else
						args[count] = new Variable(0, getExpression(), null);	
				} else if (next==WORD && (nextPlus==',' || nextPlus==')')) {
					value = 0.0;
					Variable[] array = null;
					int arraySize = 0;
					String str = null;
					getToken();
					Variable v = lookupVariable();
					if (v!=null) {
						int type = v.getType();
						if (type==Variable.VALUE)
							value = v.getValue();
						else if (type==Variable.ARRAY) {
							array = v.getArray();
							arraySize = v.getArraySize();
						} else
							str = v.getString();
					}
					args[count] = new Variable(0, value, str, array);
					if (array!=null) args[count].setArraySize(arraySize);
				} else if (next==WORD && nextPlus=='[' ) {
					int savePC = pc;
					getToken();
					Variable v = lookupVariable();
					v = getArrayElement(v);
					if (v.getString()!=null)
						args[count] = new Variable(0, 0.0, v.getString(), null);
					else {
						pc = savePC;
						args[count] = new Variable(0, getExpression(), null);
					}
				} else if (nextPlus=='+' && next==WORD) {
					int savePC = pc;
					getToken();
					Variable v = lookupVariable();
					boolean isString = v!=null && v.getType()==Variable.STRING;
					pc = savePC;
					if (isString)
						args[count] = new Variable(0, 0.0, getString());
					else
						args[count] = new Variable(0, getExpression(), null);
				} else
					args[count] = new Variable(0, getExpression(), null);
				count++;
				getToken();
			} while (token==',');
			putTokenBack();
		}
		int nArgs = count;
		while(count>0)
			push(args[--count], this);
		getRightParen();
		return nArgs;
	}

	void setupArgs(int nArgs) {
		getLeftParen();
		int i = topOfStack;
		int count = nArgs;
		if (nextToken()!=')') {
			do {
			   getToken();
			   if (i>=0)
				  stack[i].symTabIndex = tokenAddress;
			   i--;
			   count--;
			   getToken();
			} while (token==',');
			putTokenBack();
		}
		if (count!=0)
		   error(nArgs+" argument"+(nArgs==1?"":"s")+" expected");
		getRightParen();
	}
	
	// cache exception object for better performance
    ReturnException returnException;
    
    // Handle return statement 
	void doReturn() {
		double value = 0.0;
		String str = null;
		Variable[] array = null;
		int arraySize = 0;
		getToken();		
		if (token=='(') {
			int next = pgm.code[pc+1];
			if ((next&TOK_MASK)==STRING_CONSTANT || (next&TOK_MASK)==STRING_FUNCTION || isString(next))
				error("String enclosed in parens");
		}		
		if (token!=';') {
			boolean isString = token==STRING_CONSTANT || token==STRING_FUNCTION;
			boolean isArrayFunction = token==ARRAY_FUNCTION;
			if (token==WORD) {
				Variable v = lookupLocalVariable(tokenAddress);
				if (v!=null && nextToken()==';') {
					array = v.getArray();
					if (array!=null) arraySize=v.getArraySize();
					isString = v.getString()!=null;
				} else if (v!=null && nextToken()=='+')
					isString = v.getType()==Variable.STRING;
			}
			putTokenBack();
			if (isString)
				str = getString();
			else if (isArrayFunction) {
				getToken();
				array = func.getArrayFunction(pgm.table[tokenAddress].type);
			} else if (array==null) {
				if ((pgm.code[pc+2]&0xff)=='[' && nextToken()==WORD) {
					int savePC = pc;
					getToken();
					Variable v = lookupVariable();
					v = getArrayElement(v);
					pc = savePC;
					if (v.getString()!=null)
						str = getString();
					else
						value = getExpression();
				} else
					value = getExpression();
			}
		}
		if (inFunction) {
			if (returnException==null)
				returnException = new ReturnException();
			returnException.value = value;
			returnException.str = str;
			returnException.array = array;
			returnException.arraySize = arraySize;
			//throw new ReturnException(value, str, array);
			throw returnException;
		} else {
			finishUp();
			if (value!=0.0 || array!=null)
				error("Macros can only return strings");
			returnValue = str;
			done = true;
		}
	}
	
	void doFor() {
		boolean saveLooseSyntax = looseSyntax;
		looseSyntax = false;
		loopDepth++;
		inLoop = true;
		getToken();
		if (token!='(')
			error("'(' expected");
		getToken(); // skip 'var'
		if (token!=VAR)
			putTokenBack();
		do {
			if (nextToken()!=';')
			   getAssignmentExpression();
			getToken();
		} while (token==',');
		if (token!=';')
			error("';' expected");
		int condPC = pc;
		int incPC2, startPC=0;
		double cond = 1;
		while (true) {
			if (pgm.code[pc+1]!=';')
			   cond = getLogicalExpression();
			if (startPC==0)
				checkBoolean(cond);
			getToken();
			if (token!=';')
				error("';' expected");
			int incPC = pc;
			// skip to start of code
			if (startPC!=0)
				pc = startPC;
			else {
			  while (token!=')') {
				getToken();
				//IJ.log(pgm.decodeToken(token,tokenAddress));
				if (token=='{' || token==';' || token=='(' || done)
					error("')' expected");
			   }
			}
			startPC = pc;
			if (cond==1) {
				try {
					doStatement();
				} catch(MacroException e) {
					if (e.getType()==BREAK) {
						pc = startPC;
						skipStatement();
						break;
					}
				}
			} else {
				skipStatement();
				break;
			}
			pc = incPC; // do increment
			do {
				 if (nextToken()!=')')
					getAssignmentExpression();
				getToken();
			} while (token==',');
			pc = condPC;
		}
		looseSyntax = saveLooseSyntax;
		loopDepth--;
		if (loopDepth==0)
			inLoop = false;
	}

	void doWhile() {
		looseSyntax = false;
		loopDepth++;
		inLoop = true;
		int savePC = pc;
		boolean isTrue;
		do {
			pc = savePC;
			isTrue = getBoolean();
			if (isTrue) {
				try {
					doStatement();
				} catch(MacroException e) {
					if (e.getType()==BREAK) {
						pc = savePC;
						getBoolean();
						skipStatement();
						break;
					}
				}
			} else
				skipStatement();
		} while (isTrue && !done);
		loopDepth--;
		if (loopDepth==0)
			inLoop = false;
	}

	void doDo() {
		looseSyntax = false;
		int savePC = pc;
		boolean isTrue;
		do {
			doStatement();
			getToken();
			if (token!=WHILE)
				error("'while' expected");
			isTrue = getBoolean();
			if (isTrue)
				pc = savePC;
		} while (isTrue && !done);
	}

	final void doBlock() {
		getToken();
		if (token!='{')
			error("'{' expected");
		while (!done) {
			getToken();
			if (token=='}')
				break;
			putTokenBack();
			doStatement();
		}
		if (token!='}')
			error("'}' expected");
	}

	final void skipStatement() {
		if (done)
			return;
		getToken();
		switch (token) {
			case PREDEFINED_FUNCTION: case USER_FUNCTION: case VAR:
			case WORD: case '(': case PLUS_PLUS: case RETURN: case ARRAY_FUNCTION:
			case NUMERIC_FUNCTION: case STRING_FUNCTION:
				skipSimpleStatement();
				break;
			case IF:
				skipParens();
				skipStatement();
				getToken();
				if (token==ELSE)
					skipStatement();
				else
					putTokenBack();
				break;
			case FOR:
				skipParens();
				skipStatement();
				break;
			case WHILE:
				skipParens();
				skipStatement();
				break;
			case DO:
				skipStatement();
				getToken(); // skip 'while'
				skipParens();
				break;
			case BREAK: case CONTINUE: case ';':
				break;
			case '{':
				putTokenBack();
				skipBlock();
				break;
			default:
				error("Skipped statement cannot begin with '"+pgm.decodeToken(token, tokenAddress)+"'");
		}
	}

	final void skipBlock() {
		int count = 0;
		do {
			getToken();
			if (token=='{')
				count++;
			else if (token=='}')
				count--;
			else if (done) {
				error("'}' expected");
				return;
			}
		} while (count>0);
	}
	
	final void skipParens() {
		int count = 0;
		do {
			getToken();
			if (token=='(')
				count++;
			else if (token==')')
				count--;
			else if (done) {
				error("')' expected");
				return;
			}
		} while (count>0);
	}

	final void skipSimpleStatement() {
		boolean finished = done;
		getToken();
		while (!finished && !done) {
			if (token==';')
				finished = true;
			else if (token==ELSE||(token==PREDEFINED_FUNCTION&&pgm.code[pc-1]!='.'))
				error("';' expected");
			else
				getToken();
		}
	}

	/** Skips a user-defined function. */
	void skipFunction() {
		getToken(); // skip function id
		skipParens();
		skipBlock();
	}

	void runFirstMacro() {
		getToken(); // skip macro label
		doBlock(); 
		done = true;
		finishUp();
	}

	void skipMacro() {
		getToken(); // skip macro label
		skipBlock();
	}

	final void doAssignment() {
		int next = pgm.code[pc+1]&0xff;
		if (next=='[') {
			doArrayElementAssignment();
			return;
		} 
		int type = getExpressionType();
		switch (type) {
			case Variable.STRING: doStringAssignment(); break;
			case Variable.ARRAY: doArrayAssignment(); break;
			case USER_FUNCTION: doUserFunctionAssignment(); break;
			case STRING_FUNCTION: doNumericStringAssignment(); break;
			default:
				putTokenBack();
				double value = getAssignmentExpression();
				if (evaluating) log(""+value);
		}
	}

	int getExpressionType() {
		int rightSideToken = pgm.code[pc+2];
		int tok = rightSideToken&0xff;
		if (tok==STRING_CONSTANT)
			return Variable.STRING;
		if (tok==STRING_FUNCTION) {
			int address = rightSideToken>>TOK_SHIFT;
			int type = pgm.table[address].type;
			if (type==DIALOG) {
				int token2 = pgm.code[pc+4];
				String name = pgm.table[token2>>TOK_SHIFT].str;
				if (name.equals("getNumber") || name.equals("getCheckbox"))
					return STRING_FUNCTION; 
			} else if (type==FILE) {
				int token2 = pgm.code[pc+4];
				String name = pgm.table[token2>>TOK_SHIFT].str;
				if (name.equals("exists")||name.equals("isDirectory")||name.equals("length")
				||name.equals("getLength")||name.equals("rename")||name.equals("delete"))
					return STRING_FUNCTION;
			} else if (type==LIST) {
				int token2 = pgm.code[pc+4];
				String name = pgm.table[token2>>TOK_SHIFT].str;
				if (name.equals("getValue")) return STRING_FUNCTION;
			}
			return Variable.STRING;
		}
		if (tok==ARRAY_FUNCTION)
			return Variable.ARRAY;
		if (tok==USER_FUNCTION)
			return USER_FUNCTION;
		if (tok==VARIABLE_FUNCTION) {
			int address = rightSideToken>>TOK_SHIFT;
			int type = pgm.table[address].type;
			if (type==TABLE) {
				int token2 = pgm.code[pc+4];
				String name = pgm.table[token2>>TOK_SHIFT].str;
				if (name.equals("getString")||name.equals("title")||name.equals("headings"))
					return Variable.STRING;
				else if (name.equals("getColumn"))
					return Variable.ARRAY;
			}
		}
		if (tok!=WORD)
			return Variable.VALUE;
		Variable v = lookupVariable(rightSideToken>>TOK_SHIFT);
		if (v==null)
			return Variable.VALUE;
		int type = v.getType();
		if (type!=Variable.ARRAY)
			return type;
		if (pgm.code[pc+3]=='.')
			return Variable.VALUE;		
		if (pgm.code[pc+3]!='[')
			return Variable.ARRAY;
		int savePC = pc;
		getToken(); //"="
		getToken(); //the variable
		checkingType = true;
		int index = getIndex();
		checkingType = false;
		pc = savePC-1;
		getToken();
		Variable[] array = v.getArray();
		if (index<0 || index>=array.length)
			return Variable.VALUE;
		return array[index].getType();
	}
	
	/** Handles string functions such as Dialog.getNumber() that return a number. */
	final void doNumericStringAssignment() {
		putTokenBack();
		getToken();		
		Variable v = lookupLocalVariable(tokenAddress);
		if (v==null) v = push(tokenAddress, 0.0, null, this);
		getToken();
		if (token!='=') error("'=' expected");
		v.setValue(getExpression());
	}

	final void doArrayElementAssignment() {
		Variable v = lookupLocalVariable(tokenAddress);
		if (v==null)
				error("Undefined identifier");
		if (pgm.code[pc+5]==';'&&(pgm.code[pc+4]==PLUS_PLUS||pgm.code[pc+4]==MINUS_MINUS))
			{putTokenBack(); getFactor(); return;}
		int index = getIndex();
		int expressionType = getExpressionType();
		if (expressionType==Variable.ARRAY) 
			error("Arrays of arrays not supported");
		getToken();
		int op = token;
		if (!(op=='='||op==PLUS_EQUAL||op==MINUS_EQUAL||op==MUL_EQUAL||op==DIV_EQUAL))
			{error("'=', '+=', '-=', '*=' or '/=' expected"); return;}
		if (op!='=' && (expressionType==Variable.STRING||expressionType==Variable.ARRAY))
			{error("'=' expected"); return;}
		Variable[] array = v.getArray();
		if (array==null)
			error("Array expected");
		if (index<0)
			error("Negative index");
		if (index>=array.length) {  // expand array
			if (!func.expandableArrays)
				error("Index ("+index+") out of range");
			Variable[] array2 = new Variable[index+array.length/2+1];
			boolean strings = array.length>0 && array[0].getString()!=null;
			for (int i=0; i<array2.length; i++) {
				if (i<array.length)
					array2[i] = array[i];
				else {
					array2[i] = new Variable(Double.NaN);
					if (strings)
						array2[i].setString("undefined");
				}
			}
			v.setArray(array2);
			v.setArraySize(index+1);
			array = v.getArray();
		}
		int size = v.getArraySize();
		if (index+1>size)
			v.setArraySize(index+1);
		int next = nextToken();
		switch (expressionType) {
			case Variable.STRING:
				array[index].setString(getString());
				break;
			case Variable.ARRAY:
				getToken();
				if (token==ARRAY_FUNCTION)
					array[index].setArray(func.getArrayFunction(pgm.table[tokenAddress].type));
				break;
			default:
				switch (op) {
					case '=': array[index].setValue(getExpression()); break;
					case PLUS_EQUAL: array[index].setValue(array[index].getValue()+getExpression()); break;
					case MINUS_EQUAL: array[index].setValue(array[index].getValue()-getExpression()); break;
					case MUL_EQUAL: array[index].setValue(array[index].getValue()*getExpression()); break;
					case DIV_EQUAL: array[index].setValue(array[index].getValue()/getExpression()); break;
				}
				break;
		}				
	}

	final void doUserFunctionAssignment() {
		//IJ.log("doUserFunctionAssignment0: "+pgm.decodeToken(token, tokenAddress));
		putTokenBack();
		int savePC = pc;
		getToken(); // the variable
		getToken(); // '='
		getToken(); // the function
		boolean simpleAssignment = isSimpleFunctionCall(true);
		pc = savePC;
		if (!simpleAssignment)
			getAssignmentExpression();
		else {
			getToken();		
			Variable v1 = lookupLocalVariable(tokenAddress);
			if (v1==null)
				v1 = push(tokenAddress, 0.0, null, this);
			getToken();
			if (token!='=')
				error("'=' expected");
			getToken(); // the function
			Variable v2 = runUserFunction();
			if (v2==null)
				error("No return value");
			if (done) return;
			int type = v2.getType();
			if (type==Variable.VALUE)
				v1.setValue(v2.getValue());
			else if (type==Variable.ARRAY) {
				v1.setArray(v2.getArray());
				v1.setArraySize(v2.getArraySize());
			} else
				v1.setString(v2.getString());
		}	
	}
	
	boolean isSimpleFunctionCall(boolean assignment) {
		int count = 0;
		do {
			getToken();
			//IJ.log(pgm.decodeToken(token, tokenAddress));
			if (token=='(')
				count++;
			else if (token==')')
				count--;
			else if (done)
				error("')' expected");
		} while (count>0);
		getToken();
		if (assignment)
			return token==';';
		else
			return token==','||token==')';
	}
	
	final void doStringAssignment() {
		Variable v = lookupLocalVariable(tokenAddress);
		if (v==null) {
			if (nextToken()=='=')
				v = push(tokenAddress, 0.0, null, this);
			else
				error("Undefined identifier");
		}
		getToken();
		if (token=='=')
			v.setString(getString());
		else if (token==PLUS_EQUAL)
			v.setString(v.getString()+getString());
		else
			error("'=' or '+=' expected");
	}

	final void doArrayAssignment() {
		Variable v = lookupLocalVariable(tokenAddress);
		if (v==null) {
			if (nextToken()=='=')
				v = push(tokenAddress, 0.0, null, this);
			else
				error("Undefined identifier");
		}
		getToken();
		if (token!='=') {
			error("'=' expected");
			return;
		}
		getToken();
		if (token==ARRAY_FUNCTION)
			v.setArray(func.getArrayFunction(pgm.table[tokenAddress].type));
		else if (token==WORD) {
			Variable v2 = lookupVariable();
			v.setArray(v2.getArray());
			v.setArraySize(v2.getArraySize());
		} else if (token==VARIABLE_FUNCTION) {
			Variable v2 = func.getVariableFunction(pgm.table[tokenAddress].type);
			Variable[] array = v2.getArray();
			if (array==null)
				error("Array expected");			
			v.setArray(array);
		} else
			error("Array expected");
	}

	final void doIf() {
		looseSyntax = false;
		boolean b = getBoolean();
		if (b)
			doStatement();
		else
			skipStatement();
		int next = nextToken();
		if (next==';') {
			getToken();
			next = nextToken();
		}
		if (next==ELSE) {
			getToken();
			if (b)
				skipStatement();
			else
				doStatement();
		}
	}

	final boolean getBoolean() {
		getLeftParen();
		double value = getLogicalExpression();
		checkBoolean(value);
		getRightParen();
		return value==0.0?false:true;
	}

	final double getLogicalExpression() {
		double v1 = getBooleanExpression();
		int next = nextToken();
		if (!(next==LOGICAL_AND || next==LOGICAL_OR))
			return v1;
		checkBoolean(v1);
		getToken();
		int op = token;	
		double v2 = getLogicalExpression();
		checkBoolean(v2);
		if (op==LOGICAL_AND)
			return (int)v1 & (int)v2;
		else if (op==LOGICAL_OR)
			return (int)v1 | (int)v2;
		return v1;
	}

	final double getBooleanExpression() {
		double v1 = 0.0;
		String s1 = null;
		int next = pgm.code[pc+1];
		int tok = next&TOK_MASK;
		if (tok==STRING_CONSTANT || tok==STRING_FUNCTION || isString(next))
			s1 = getString();
		else
			v1 = getExpression();
		next = nextToken();
		if (next>=EQ && next<=LTE) {
			getToken();
			int op = token;
			if (s1!=null)
				return compareStrings(s1, getString(), op);
			double v2 = getExpression();
			switch (op) {
				case EQ:
					v1 = v1==v2?1.0:0.0;
					break;
				case NEQ:
					v1 = v1!=v2?1.0:0.0;
					break;
				case GT:
					v1 = v1>v2?1.0:0.0;
					break;
				case GTE:
					v1 = v1>=v2?1.0:0.0;
					break;
				case LT:
					v1 = v1<v2?1.0:0.0;
					break;
				case LTE:
					v1 = v1<=v2?1.0:0.0;
					break;
			}
		} else if (s1!=null)
			v1 = Tools.parseDouble(s1, 0.0);
		return v1;
	}

	// returns true if the specified token is a string variable
	boolean isString(int token) {
		if ((token&TOK_MASK)!=WORD) return false;
		Variable v = lookupVariable(token>>TOK_SHIFT);
		if (v==null) return false;
		if (pgm.code[pc+2]=='[') {
			Variable[] array = v.getArray();
			if (array!=null && array.length>0)
				return array[0].getType()==Variable.STRING;
		}
		return v.getType()==Variable.STRING;
	}

	double compareStrings(String s1, String s2, int op) {
		int result;
		result = s1.compareToIgnoreCase(s2);
		double v1 = 0.0;
		switch (op) {
			case EQ:
				v1 = result==0?1.0:0.0;
				break;
			case NEQ:
				v1 = result!=0?1.0:0.0;
				break;
			case GT:
				v1 = result>0?1.0:0.0;
				break;
			case GTE:
				v1 = result>=0?1.0:0.0;
				break;
			case LT:
				v1 = result<0?1.0:0.0;
				break;
			case LTE:
				v1 = result<=0?1.0:0.0;
				break;
		}
		return v1;
	}

	final double getAssignmentExpression() {
		int tokPlus2 = pgm.code[pc+2];
		if ((pgm.code[pc+1]&0xff)==WORD && (tokPlus2=='='||tokPlus2==PLUS_EQUAL
		||tokPlus2==MINUS_EQUAL||tokPlus2==MUL_EQUAL||tokPlus2==DIV_EQUAL)) {
			getToken();
			Variable v = lookupLocalVariable(tokenAddress);
			if (v==null)
				v = push(tokenAddress, 0.0, null, this);
			getToken();
			double value = 0.0;
			if (token=='=')
				value = getAssignmentExpression();
			else {
				value = v.getValue();
				switch (token) {
					case PLUS_EQUAL: value += getAssignmentExpression(); break;
					case MINUS_EQUAL: value -= getAssignmentExpression(); break;
					case MUL_EQUAL: value *= getAssignmentExpression(); break;
					case DIV_EQUAL: value /= getAssignmentExpression(); break;
				}
			}
			v.setValue(value);
			return value;
		} else
			return getLogicalExpression();
	}

	final void checkBoolean(double value) {
		if (!(value==0.0 || value==1.0))
			error("Boolean expression expected");
	}

	void doVar() {
		getToken();
		while (token==WORD) {
			if (nextToken()=='=')
				doAssignment();
			else {
				Variable v = lookupVariable(tokenAddress);
				if (v==null)
					push(tokenAddress, 0.0, null, this);
			}
			getToken();
			if (token==',')
				getToken();
			else {
				putTokenBack();
				break;
			}
		}
	}
	
	final void getLeftParen() {
		getToken();
		if (token!='(')
			error("'(' expected");
	}

	final void getRightParen() {
		getToken();
		if (token!=')')
			error("')' expected");
	}

	final void getParens() {
		if (nextToken()=='(') {
			getLeftParen();
			getRightParen();
		}
	}

	final void getComma() {
		getToken();
		if (token!=',') {
			if (looseSyntax)
				putTokenBack();
			else
				error("',' expected");
		}
	}

	void error (String message) {
		errorMessage = message;
		if (ignoreErrors)
			return;
		errorCount++;
		boolean showMessage = !done;
		String[] variables = showMessage?getVariables():null;
		token = EOF;
		tokenString = "";
		IJ.showStatus("");
		IJ.showProgress(0, 0);
		batchMode = false;
		imageTable = imageActivations = null;
		WindowManager.setTempCurrentImage(null);
		wasError = true;
		if (!evaluating)
			instance = null;
		if (showMessage && message!=null) {
			String line = getErrorLine();
			done = true;
			if (line.length()>120)
				line = line.substring(0,119)+"...";			
			Frame f = WindowManager.getFrame("Debug");			
			TextPanel panel = null;
			if (showVariables && f!=null && (f instanceof TextWindow)) { //clear previous content
				TextWindow debugWindow = (TextWindow) f;
				if (debugWindow != null) {
					panel = debugWindow.getTextPanel();
					panel.clear();
				}	
			}
			showError("Macro Error", message+" in line "+lineNumber+" \n \n"+line, variables);
			f = WindowManager.getFrame("Debug");
			if (showVariables && f!=null && (f instanceof TextWindow)) {
				TextWindow debugWindow = (TextWindow)f;
				debugWindow.append("\n---\t\t---\nError:\t\t" + message + " in line "+lineNumber + ":");
				debugWindow.append("\t\t"+line);
			}			
			throw new RuntimeException(Macro.MACRO_CANCELED);
		}
		done = true;
		if (errorCount>10) 
			throw new RuntimeException(Macro.MACRO_CANCELED);
	}
		
	void showError(String title, String msg, String[] variables) {
		boolean noImages = msg.startsWith("There are no images open");
		if (noImages)
			title = "No Image";
		Macro.setOptions(null);
		GenericDialog gd = new GenericDialog(title);
		gd.setInsets(6,5,0);
		gd.addMessage(msg);
		gd.setInsets(15,30,5);
		if (!noImages)
			gd.addCheckbox("Show \"Debug\" Window", showVariables);
		gd.hideCancelButton();
		gd.showDialog();
		if (!noImages)
			showVariables = gd.getNextBoolean();
		else
			showVariables = false;
		if (!gd.wasCanceled() && showVariables)
			updateDebugWindow(variables, null);
	}

	public TextWindow updateDebugWindow(String[] variables, TextWindow debugWindow) {
		if (debugWindow==null) {
			Frame f = WindowManager.getFrame("Debug");
			if (f!=null && (f instanceof TextWindow)) {
				debugWindow = (TextWindow)f;
				debugWindow.toFront();
			}
		}
		if (debugWindow==null)
			debugWindow = new TextWindow("Debug", "Name\t*\tValue", "", 300, 400);
		TextPanel panel = debugWindow.getTextPanel();
		int n = variables.length;
		if (n==0) {
			panel.clear();
			return debugWindow;
		}
		int lines = panel.getLineCount();
		String[] markedVariables = markChanges(variables);
		for (int i=0; i<lines; i++) {
			if (i<n)
				panel.setLine(i, markedVariables[i]);
			else
				panel.setLine(i, "");
		}
		for (int i=lines; i<n; i++)
			debugWindow.append(markedVariables[i]);
		return debugWindow;
	}

	private static String[] prevVars; //previous variables for comparison

	private String[] markChanges(String[] newVars) {//add asterisk if variable has changed
		int len = newVars.length;
		String[] copyOfNew = new String[len];
		String[] hilitedVars = new String[len];
		for (int jj = 0; jj < len; jj++) {
			copyOfNew[jj] = newVars[jj];
			String marker = "\t*\t";//changed
			if (prevVars != null && jj < prevVars.length && jj < len && prevVars[jj].equals(newVars[jj]))
				marker = "\t\t";//unchanged
			hilitedVars[jj] = newVars[jj].replaceFirst("\t", marker);
		}
		prevVars = copyOfNew;
		return hilitedVars;
	}

	String getErrorLine() {
		int savePC = pc;
		lineNumber = pgm.lineNumbers[pc];
		while (pc>=0 && lineNumber==pgm.lineNumbers[pc])
			pc--;   //go to beginning of line
		if (lineNumber<=1)
			pc = -1;
		String line = "";
		getToken();
		while (!done && lineNumber==pgm.lineNumbers[pc]) {
			String str = pgm.decodeToken(token, tokenAddress);
			if (pc==savePC)
				str = "<" + str + ">";
			line += str + " ";
			getToken();
		}
		return line;
	}

	final String getString() {
		String str = getStringTerm();
		while (true) {
			getToken();
			if (token=='+')
				str += getStringTerm();
			else {
				putTokenBack();
				break;
			}
		}
		return str;
	}

	final String getStringTerm() {
		String str;
		Variable v;
		getToken();
		switch (token) {
		case STRING_CONSTANT:
			str = tokenString;
			break;
		case STRING_FUNCTION:
			str = func.getStringFunction(pgm.table[tokenAddress].type);
			break;
		case VARIABLE_FUNCTION:
			v = func.getVariableFunction(pgm.table[tokenAddress].type);
			str = v.getString();
			if (str==null) {
				double value = v.getValue();
				if ((int)value==value)
					str = IJ.d2s(value,0);
				else
					str = ""+value;
			}
			break;
		case USER_FUNCTION:
			v = runUserFunction();
			if (v==null)
				error("No return value");
			str = v.getString();
			if (str==null) {
				double value = v.getValue();
				if ((int)value==value)
					str = IJ.d2s(value,0);
				else
					str = ""+value;
			}
			break;
		case WORD:
			str = lookupStringVariable();
			if (str!=null)
				break;
			// else fall through
		default:
			putTokenBack();
			str = toString(getStringExpression());
		}
		return str;
	}
	
	private String toString(double x) {
		if ((int)x==x)
			return IJ.d2s(x,0);
		else {
			if (evaluating)
				return ""+x;
			String str = IJ.d2s(x, 4, 9);
			while(str.endsWith("0") && str.contains(".") && !str.contains("E"))
				str = str.substring(0, str.length()-1);
			if (str.endsWith("."))
				str = str.substring(0, str.length()-1);
			return str;
		}
	}

	final boolean isStringFunction() {
		Symbol symbol = pgm.table[tokenAddress];
		return symbol.type==D2S;
	}

	final double getExpression() {
		double value = getTerm();
		int next;
		while (true) {
			next = nextToken();
			if (next=='+') {
				getToken();
				value += getTerm();
			} else if (next=='-') {
				getToken();
				value -= getTerm();
			} else
				break;
		}
		return value;
	}

	final double getTerm() {
		double value = getFactor();
		boolean done = false;
		int next;
		while (!done) {
			next = nextToken();
			switch (next) {
				case '*': getToken(); value *= getFactor(); break;
				case '/': getToken(); value /= getFactor(); break;
				case '%': getToken(); value %= getFactor(); break;
				case '&': getToken(); value = (int)value&(int)getFactor(); break;
				case '|': getToken(); value = (int)value|(int)getFactor(); break;
				case '^': getToken(); value = (int)value^(int)getFactor(); break;
				case SHIFT_RIGHT: getToken(); value = (int)value>>(int)getFactor(); break;
				case SHIFT_LEFT: getToken(); value = (int)value<<(int)getFactor(); break;
				default: done = true; break;
			}
		}
		return value;
	}

	final double getFactor() {
		double value = 0.0;
		Variable v = null;
		getToken();
		switch (token) {
			case NUMBER:
				value = tokenValue;
				break;
			case NUMERIC_FUNCTION:
				value = func.getFunctionValue(pgm.table[tokenAddress].type);
				break;
			case STRING_FUNCTION:
				String str = func.getStringFunction(pgm.table[tokenAddress].type);
				value = Tools.parseDouble(str);
				if ("NaN".equals(str))
					value = Double.NaN;
				else if (Double.isNaN(value))
					error("Numeric value expected");
				break;				
			case VARIABLE_FUNCTION:
				v = func.getVariableFunction(pgm.table[tokenAddress].type);
				if (v==null)
					error("No return value");
				if (v.getString()!=null)
						error("Numeric return value expected");
				else
					value = v.getValue();
				break;
			case USER_FUNCTION:
				v = runUserFunction();
				if (v==null)
					error("No return value");
				if (done)
					value = 0;
				else {
					if (v.getString()!=null)
						error("Numeric return value expected");
					else
						value = v.getValue();
				}
				break;
			case TRUE: value = 1.0; break;
			case FALSE: value = 0.0; break;
			case PI: value = Math.PI; break;
			case NaN: value = Double.NaN; break;
			case WORD:
				v = lookupVariable();
				if (v==null)
					return 0.0;
				int next = nextToken();
				if (next=='[') {
					v = getArrayElement(v);
					value = v.getValue();
					next = nextToken();
				} else if (next=='.') {
					value = getArrayLength(v);
					next = nextToken();
				} else {
					if (v.getArray()!=null) {
						getToken();
						error("'[' or '.' expected");
					}
					if (prefixValue!=0 && !checkingType) {
						v.setValue(v.getValue()+prefixValue);
						prefixValue = 0;
					}
					value = v.getValue();
				}
				if (!(next==PLUS_PLUS || next==MINUS_MINUS))
					break;
				getToken();
				if (token==PLUS_PLUS)
					v.setValue(v.getValue()+(checkingType?0:1));
				else
					v.setValue(v.getValue()-(checkingType?0:1));
				break;
			case (int)'(':
				value = getLogicalExpression();
				getRightParen();
				break;
			case PLUS_PLUS:
				prefixValue = 1;
				value = getFactor();
				break;
			case MINUS_MINUS:
				prefixValue = -1;
				value = getFactor();
				break;
			case '!':
				value = getFactor();
				if (value==0.0 || value==1.0) {
					value = value==0.0?1.0:0.0;
				} else
					error("Boolean expected");
				break;
			case '-':
				value = -getFactor();
				break;
			case '~':
				value = ~(int)getFactor();
				break;
			default:
				error("Number or numeric function expected");
		}
		// IJ.log("getFactor: "+value+" "+pgm.decodeToken(preToken,0));
		return value;
	}

	final Variable getArrayElement(Variable v) {
		int index = getIndex();
		Variable[] array = v.getArray();
		if (array==null)
			error("Array expected");
		if (index<0 || index>=array.length) {
			if (array.length==0)
				error("Empty array");
			else
				error("Index ("+index+") out of 0-"+(array.length-1)+" range");
		}
		return array[index];
	}
	
	final double getArrayLength(Variable v) {
		getToken(); // '.'
		getToken();
		if (!(token==WORD && tokenString.equals("length")))
			error("'length' expected");
		if (v.getArray()==null)
			error("Array expected");
		return v.getArraySize();
	}
	
	final double getStringExpression() {
		double value = getTerm();
		while (true) {
			getToken();
			if (token=='+') {
				getToken();
				if (token==STRING_CONSTANT || token==STRING_FUNCTION) {
					putTokenBack();
					putTokenBack();
					break;
				}
				if (token==WORD) {
					Variable v = lookupVariable(tokenAddress);
					if (v!=null && v.getString()!=null) {
						putTokenBack();
						putTokenBack();
						break;
					}
				}
				putTokenBack();
				value += getTerm();
			} else if (token=='-')
				value -= getTerm();
			else {
				putTokenBack();
				break;
			}
		};
		return value;
	}

	/** Searches the local and global sections of the stack for.
		the specified variable. Returns null if it is not found. */
	final Variable lookupLocalVariable(int symTabAddress) {
		//IJ.log("lookupLocalVariable: "+topOfStack+" "+startOfLocals+" "+topOfGlobals);
		Variable v = null;
		for (int i=topOfStack; i>=startOfLocals; i--) {
			if (stack[i].symTabIndex==symTabAddress) {
				v = stack[i];
				break;
			}
		}
		if (v==null) {
			for (int i=topOfGlobals; i>=0; i--) {
				if (stack[i].symTabIndex==symTabAddress) {
					v = stack[i];
					break;
				}
			}
		}
		return v;
	}

	/** Searches the entire stack for the specified variable. Returns null if it is not found. */
	final Variable lookupVariable(int symTabAddress) {
		Variable v = null;
		for (int i=topOfStack; i>=0; i--) {
			if (stack[i].symTabIndex==symTabAddress) {
				v = stack[i];
				break;
			}
		}
		return v;
	}

	Variable push(Variable var, Interpreter interp) {
		if (stack==null)
			stack = new Variable[STACK_SIZE];
		if (topOfStack>=(STACK_SIZE-2))
			interp.error("Stack overflow");
		else
			topOfStack++;
		stack[topOfStack] = var;
		return var;
	}

	void pushGlobals() {
		if (pgm.globals==null)
			return;
		if (stack==null)
			stack = new Variable[STACK_SIZE];
		for (int i=0; i<pgm.globals.length; i++) {
			topOfStack++;
			stack[topOfStack] = pgm.globals[i];
		}
		topOfGlobals = topOfStack;
	}

	/** Creates a Variable and pushes it onto the stack. */
	Variable push(int symTabLoc, double value, String str, Interpreter interp) {
		Variable var = new Variable(symTabLoc, value, str);
		if (stack==null)
			stack = new Variable[STACK_SIZE];
		if (topOfStack>=(STACK_SIZE-2))
			interp.error("Stack overflow");
		else
			topOfStack++;
		stack[topOfStack] = var;
		return var;
	}

	void trimStack(int previousTOS, int previousStartOfLocals) {
		for (int i=previousTOS+1; i<=topOfStack; i++)
			stack[i] = null;
		topOfStack = previousTOS;
	    startOfLocals = previousStartOfLocals;
	}
	
	/** Searches the entire stack for the variable associated with the 
		current token. Aborts the macro if it is not found. */
	final Variable lookupVariable() {
		Variable v = null;
		if (stack==null) {
			undefined();
			return v;
		}
		boolean found = false;
		for (int i=topOfStack; i>=0; i--) {
			v = stack[i];
			if (v.symTabIndex==tokenAddress) {
				found = true;
				break;
			}
		}
		if (!found)
			undefined();
		return v;
	}

	final String lookupStringVariable() {
		if (stack==null) {
			undefined();
			return "";
		}
		boolean found = false;
		String str = null;
		for (int i=topOfStack; i>=0; i--) {
			if (stack[i].symTabIndex==tokenAddress) {
				Variable v = stack[i];
				found = true;
				int next = nextToken();
				if (next=='[') {
					int savePC = pc;
					int index = getIndex();
					Variable[] array = v.getArray();
					if (array==null)
						error("Array expected");
					if (index<0 || index>=array.length)
						error("Index ("+index+") out of 0-"+(array.length-1)+" range");
					str = array[index].getString();					
					if (str==null) {
						int next2 = nextToken();
						if (next2==')' || next2==';')
							str = toString(array[index].getValue());
						else {
							pc = savePC-1;
							getToken();
						}
					}
				} else if (next=='.')
						str = null;
				else {
					if (v.getArray()!=null)
						{getToken(); error("'[' or '.' expected");}
					str = v.getString();
				}
				break;
			}
		}
		if (!found)
			undefined();
		return str;
	}

	int getIndex() {
		getToken();
		if (token!='[')
			error("'['expected");
		int index = (int)getExpression();
		getToken();
		if (token!=']')
			error("']' expected");
		return index;
	}
	
	void undefined() {
		if (nextToken()=='(')
			error("Undefined identifier");
		else {
			if (pgm.getSize()==1) {
				String cmd = pgm.decodeToken(pgm.code[0]);
				cmd = cmd.replaceAll("_", " ");
				Hashtable commands = Menus.getCommands();
				if (commands!=null && commands.get(cmd)!=null)
					IJ.run(cmd);
				else
					error("Undefined variable");
			} else
				error("Undefined variable");
		}
	}
	
	void dump() {
		getParens();
		if (!done) {
			pgm.dumpSymbolTable();
			pgm.dumpProgram();
			dumpStack();
		}
	}

	void dumpStack() {
		IJ.log("");
		IJ.log("Stack");
		if (stack!=null) {
			for (int i=topOfStack; i>=0; i--) {
				Variable v = stack[i];
				Symbol symbol = v!=null?pgm.table[v.symTabIndex]:null;
				IJ.log(i+" "+(symbol!=null?symbol.str:"null")+" "+v);
			}
		}
	}
	
	void finishUp() {
		if (batchMacro)
			batchMacroImage = WindowManager.getCurrentImage();
		func.updateDisplay();
		instance = null;
		if (!calledMacro || batchMacro) {
			if (batchMode)
				showingProgress = true;
			batchMode = false;
			imageTable = imageActivations = null;
			WindowManager.setTempCurrentImage(null);
		}
		if (func.plot!=null) {
			func.plot.show();
			func.plot = null;
		}
		if (showingProgress)
			IJ.showProgress(0, 0);
		if (keysSet) {
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.setKeyUp(KeyEvent.VK_SHIFT);		
			IJ.setKeyUp(KeyEvent.VK_SPACE);
		}
		if (rgbWeights!=null)
			ColorProcessor.setWeightingFactors(rgbWeights[0], rgbWeights[1], rgbWeights[2]);
		if (func.writer!=null)
			func.writer.close();
		func.roiManager = null;
		if (func.resultsPending) {
			ResultsTable rt = ResultsTable.getResultsTable();
			if (rt!=null && rt.size()>0)
				rt.show("Results");
		}
		if (IJ.isMacOSX() && selectCount>0 && debugger==null) {
			Frame frame = WindowManager.getFrontWindow();
			if (frame!=null && (frame instanceof ImageWindow))
				ImageWindow.setImageJMenuBar((ImageWindow)frame);
		}
	}
	
	/** Aborts currently running macro. */
	public static void abort() {
		//IJ.log("abort: "+(instance!=null?""+instance.hashCode():"null"));
		if (instance!=null)
			instance.abortMacro();
	}
	
	/** Aborts the macro that was running when this one started. */
	static void abortPrevious() {
		if (previousInstance!=null) {
			previousInstance.abortMacro();
			IJ.beep();
			previousInstance = null;
		}
	}

	/** Absolete, replaced by abortMacro(). */
	public static void abort(Interpreter interp) {
		if (interp!=null)
			interp.abortMacro();
	}
	
	/** Aborts this macro. */
	public void abortMacro() {
		if (!calledMacro || batchMacro) {
			batchMode = false;
			imageTable = imageActivations = null;
		}
		if (func!=null && !(macroName!=null&&macroName.indexOf(" Tool")!=-1))
			func.abortDialog();
		IJ.showStatus("Macro aborted");
		shutdown();
		//IJ.log("abortMacro1: "+done+" "+(instance!=null?""+instance.hashCode():"null"));
		long t0 = System.currentTimeMillis();
		while ((System.currentTimeMillis()-t0)<2000 && instance!=null)
			IJ.wait(5);
		if (instance!=null) {
			abortAllMacroThreads();
			setInstance(null);
		}
	}
	
	public void abort(String message) {
		errorMessage = message;
		if (ignoreErrors) {
			done = true;
			finishUp();
		} else {
			error(message);
		}
	}
	
	private synchronized void shutdown() {
		ignoreErrors = true;
		for (int i=0; i<10; i++)
			done = true;
	}
		
	private void abortAllMacroThreads() {
		try {
			ThreadGroup group = Thread.currentThread().getThreadGroup(); 
			int activeCount = group.activeCount(); 
			Thread[] threads = new Thread[activeCount]; 
			group.enumerate(threads); 
			for (int i = 0; i < activeCount; i++) { 
				String name = threads[i].getName(); 
				if (name!=null && name.endsWith("Macro$"))
					threads[i].stop(); 
			}
		} catch (Throwable e) {
		}
	} 

	public static Interpreter getInstance() {
		return instance;
	}
	
	static void setInstance(Interpreter i) {
		instance = i;
	}

	static void setBatchMode(boolean b) {
		batchMode = b;
		if (b==false)
			imageTable = imageActivations = null;
	}

	public static boolean isBatchMode() {
		return batchMode && !tempShowMode;
	}
	
	public static void addBatchModeImage(ImagePlus imp) {
		if (!batchMode || imp==null) return;
		if (imageTable==null)
			imageTable = new Vector();
		imageTable.add(imp);
		activateImage(imp);
	}

	public static void removeBatchModeImage(ImagePlus imp) {
		if (imageTable!=null && imp!=null) {
			int index = imageTable.indexOf(imp);
			if (index!=-1) {
				imageTable.remove(index);
				imageActivations.remove(imp);
				WindowManager.setTempCurrentImage(getLastBatchModeImage());
			}
		}
	}
	
	public static void activateImage(ImagePlus imp) {
		if (imageTable!=null && imp!=null) {
			if (imageActivations==null)
				imageActivations = new Vector();
			imageActivations.remove(imp);
			imageActivations.add(imp);
		}
	}

	public static int[] getBatchModeImageIDs() {
		if (!batchMode || imageTable==null)
			return new int[0];
		int n = imageTable.size();
		int[] imageIDs = new int[n];
		for (int i=0; i<n; i++) {
			ImagePlus imp = (ImagePlus)imageTable.get(i);
			imageIDs[i] = imp.getID();
		}
		return imageIDs;
	}

	public static int getBatchModeImageCount() {
		if (!batchMode || imageTable==null)
			return 0;
		else
			return imageTable.size();
	}
	
	public static ImagePlus getBatchModeImage(int id) {
		if (!batchMode || imageTable==null)
			return null;
		for (Enumeration en=Interpreter.imageTable.elements(); en.hasMoreElements();) {
			ImagePlus imp = (ImagePlus)en.nextElement();
			if (id==imp.getID())
				return imp;
		}
		return null;
	}
	
	public static ImagePlus getLastBatchModeImage() { 
		if (!batchMode || imageTable==null)
			return null; 
		ImagePlus imp2 = null;
		try {
			int size = imageTable.size(); 
			if (size==0)
				return null;
			if (imageActivations!=null && imageActivations.size()>0)
				imp2 =  (ImagePlus)imageActivations.get(imageActivations.size()-1);
			if (imp2==null)
				imp2 = (ImagePlus)imageTable.get(size-1);
		} catch(Exception e) { }
		return imp2;
	} 
 
 	/** The specified string, if not null, is added to strings passed to the run() method. */
 	public static void setAdditionalFunctions(String functions) {
 		additionalFunctions = functions;
	} 

 	public static String getAdditionalFunctions() {
 		return additionalFunctions;
	} 
	
	/** Returns the batch mode RoiManager instance. */
	public static RoiManager getBatchModeRoiManager() {
		Interpreter interp = getInstance();
		if (interp!=null && isBatchMode() && RoiManager.getRawInstance()==null) {
			if (interp.func.roiManager==null)
				interp.func.roiManager = new RoiManager(true);
			return interp.func.roiManager;
		} else
			return null;
	}
	
	/** Returns true if there is an internal batch mode RoiManager. */
	public static boolean isBatchModeRoiManager() {
		Interpreter interp = getInstance();
		return interp!=null && isBatchMode() && interp.func.roiManager!=null;
	}
	
	public void setDebugger(Debugger debugger) {
		this.debugger = debugger;
		if (debugger!=null)
			debugMode = Debugger.STEP;
		else
			debugMode = Debugger.NOT_DEBUGGING;
	}
	
	// Returns the Debugger (editor), if any, associated with this macro. */
	public Debugger getDebugger() {
		return debugger;
	}

	public void setDebugMode(int mode) {
		debugMode = mode;
	}
	
	public int getLineNumber() {//n__
        return pgm.lineNumbers[pc];
    }

	/** Returns the names of all variables and functions with human-readable annotations */
	public String[] getVariables() {
		int nImages = WindowManager.getImageCount();
		if (nImages>0) showDebugFunctions = true;
		int nFunctions = showDebugFunctions?3:0;
		String[] variables = new String[topOfStack+1+nFunctions];
		if (showDebugFunctions) {
			String title = null;
			if (nImages>0) {
				ImagePlus imp = WindowManager.getCurrentImage();
				if (imp!=null) title = imp.getTitle();
			}
			if (debugMode==Debugger.STEP) System.gc();
			variables[0] = "Memory\t" + IJ.freeMemory();
			variables[1] = "nImages()\t" + nImages;
			variables[2] = "getTitle()\t" + (title!=null?"\""+title+"\"":"");
		}
		String name;
		int index = nFunctions;
		for (int i=0; i<=topOfStack; i++) {
			name = pgm.table[stack[i].symTabIndex].str;
			if (i<=topOfGlobals)
				name += " (g)";
			variables[index++] = name + "\t" + stack[i];
		}
		return variables;
	}
	
	/** Returns the names of all variables, without any annotation */
	public String[] getVariableNames() {
		String[] variables = new String[topOfStack+1];
		for (int i=0; i<=topOfStack; i++)
			variables[i] = pgm.table[stack[i].symTabIndex].str;
		return variables;
	}

	// Returns 'true' if this macro has finished or if it was aborted. */
	public boolean done() {
		return done;
	}

	// Returns 'true' if this macro generated an error and was aborted. */
	public boolean wasError() {
		return wasError;
	}

	public void setVariable(String name, double value) {
		int index;
		for (int i=0; i<=topOfStack; i++) {
			index = stack[i].symTabIndex;
			if (pgm.table[index].str.equals(name)) {
				stack[i].setValue(value);
				break;
			}
		}
	}
	
	public void setVariable(String name, String str) {
		int index;
		for (int i=0; i<=topOfStack; i++) {
			index = stack[i].symTabIndex;
			if (pgm.table[index].str.equals(name)) {
				stack[i].setString(str);
				break;
			}
		}
	}

	public double getVariable(String name) {
		int index;
		for (int i=0; i<=topOfStack; i++) {
			index = stack[i].symTabIndex;
			if (pgm.table[index].str.equals(name))
				return stack[i].getValue();
		}
		return Double.NaN;
	}

	public double getVariable2(String name) {
		int index;
		for (int i=topOfStack; i>=0; i--) {
			index = stack[i].symTabIndex;
			if (pgm.table[index].str.equals(name))
				return stack[i].getValue();
		}
		return Double.NaN;
	}

	public String getStringVariable(String name) {
		int index;
		for (int i=topOfStack; i>=0; i--) {
			index = stack[i].symTabIndex;
			if (pgm.table[index].str.equals(name))
				return stack[i].getString();
		}
		return null;
	}
	
	public String getVariableAsString(String name) {
		String s = getStringVariable(name);
		if (s==null) {
			double value = getVariable2(name);
			if (!Double.isNaN(value)) s=""+value;
		}
		return s;
	}
	
	 /**
	 * Shows array elements after clicking an array variable in Debug
	 * window
	 * N. Vischer 
	 *
	 * @param row Debug window row of variable to be shown
	 */
	public void showArrayInspector(int row) {
		if (stack==null)
			return;
		int nFunctions = showDebugFunctions?3:0;
		int stkPos = row - nFunctions;
		if (stack.length>stkPos && stkPos>=0) {
			Variable var = stack[stkPos];
			if (var==null)
				return;
			if (var.getType()!=Variable.ARRAY && arrayWindow!=null)
				arrayWindow.setVisible(false);
			if (var.getType()==Variable.ARRAY) {
				String headings = "Index\t*\tValue";
				if (arrayWindow==null)
					arrayWindow = new TextWindow("Array", "", "", 170, 300);
				arrayWindow.setVisible(true);
 				int symIndex = var.symTabIndex;
				String arrName = pgm.table[symIndex].str;
				inspectStkIndex = stkPos;
				inspectSymIndex = symIndex;
				TextPanel txtPanel = arrayWindow.getTextPanel();
				String oldText = txtPanel.getText();//n__ possible NullPointer at ij.text.TextPanel.getText(TextPanel.java:875) vData == null
				String[] oldLines = oldText.split("\n");
				txtPanel.clear();
				txtPanel.setColumnHeadings(headings);
				Variable[] elements = var.getArray();
				String title = arrName + "[" + elements.length + "]";
				arrayWindow.setTitle(title);
				arrayWindow.rename(title);
				String newText = "";
				String valueStr = "";
				for (int jj=0; jj<elements.length; jj++) {
					Variable element = elements[jj];
					if (element.getType()==Variable.STRING) {
						valueStr = elements[jj].getString();
						valueStr = valueStr.replaceAll("\n", "\\\\n");
						valueStr = "\"" + valueStr + "\""; //show it's a string
					} else if (element.getType()==Variable.VALUE) {
						double v = elements[jj].getValue();
						if ((int)v==v)
							valueStr = IJ.d2s(v, 0);
						else
							valueStr = ResultsTable.d2s(v, 4);
					}
						String flag = " ";
						if (oldLines.length > jj + 1){
							String [] parts = oldLines[jj+1].split("\t");
							String oldValue = parts[2];
							if (!valueStr.equals(oldValue))
									flag ="*";
						}
					String ss = ("" + jj + "\t" + flag +"\t" + valueStr) + "\n";
					newText += ss;
				}
				txtPanel.append(newText);
				txtPanel.scrollToTop();
				if (debugger!=null && (debugger instanceof Window))
					((Window)debugger).toFront();
				//n__  scroll position should not change during single-stepping
			}
		}
	}

	/**
	 * Updates Array inspector if variable exists, otherwise closes
	 * ArrayInspector
	 */
	public void updateArrayInspector() {
		boolean varExists = false;
		if (arrayWindow!=null && arrayWindow.isVisible()) {
 			for (int stkIndex=0; stkIndex<=topOfStack; stkIndex++) {
				Variable var = stack[stkIndex];
				int symIndex = var.symTabIndex;
				if (inspectStkIndex==stkIndex && inspectSymIndex==symIndex && var.getType()==Variable.ARRAY) {
					varExists = true;
					break;
				}
			}
			if (varExists)
				showArrayInspector(inspectStkIndex+(showDebugFunctions?3:0));
			else{
				arrayWindow.setVisible(false);
				arrayWindow.getTextPanel().clear();
			}
		}
	}
	
	static void setTempShowMode(boolean mode) {
		tempShowMode = mode;
	}
	
	private static Interpreter lastInterp;
	
	public static boolean nonBatchMacroRunning() {
		Interpreter interp = getInstance();
		if (interp==null)
			return false;
		int count =  interp.selectCount;
		if (interp==lastInterp)
			interp.selectCount++;
		lastInterp = interp;
		return !interp.waitingForUser && interp.debugger==null && count>0 && !isBatchMode();
	}
	
	public void setApplyMacroTable(ResultsTable rt) {
		applyMacroTable = rt;
	}
	
	public void setIgnoreErrors(boolean ignoreErrors) {
		this.ignoreErrors = ignoreErrors;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
			
} // class Interpreter





