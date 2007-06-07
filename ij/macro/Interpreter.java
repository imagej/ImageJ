package ij.macro;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.util.*;

/** This is the recursive descent parser/interpreter for the ImageJ macro language. */
public class Interpreter implements MacroConstants {

	static final int MAX_ARGS=20;

	Tokenizer tok;
	int pc;
	int token;
	int tokenAddress;
	double tokenValue;
	String tokenString;
	boolean looseSyntax = true;
	double darg1,darg2,darg3,darg4;
	int lineNumber;
	boolean ignoreEOL = true;
	boolean statusUpdated;
	boolean showingProgress;

	static Interpreter instance;
	boolean done;
	Program pgm;
	Functions func;
	boolean inFunction;

	/** Interprets the specified string. */
	public void run(String str) {
		Tokenizer tok = new Tokenizer();
		Program pgm = tok.tokenize(str);
		run(pgm);
	}

	/** Interprets the specified tokenized macro file starting at location 0. */
	public void run(Program pgm) {
		this.pgm = pgm;
		pc = -1;
		instance = this;
		func = new Functions(this, pgm);
		IJ.showStatus("interpreting");
		if ((pgm.code[pc+1]&0xff)==MACRO && (pgm.code[pc+2]&0xff)==STRING_CONSTANT) {
		   // run macro instead of skipping over it
		   getToken();
		   getToken();
		}
		doStatements();
		func.updateDisplay();
		instance = null;
		if (!statusUpdated) IJ.showStatus("");
		if (showingProgress) IJ.showProgress(1.0);
	}

	/** Interprets the specified tokenized macro starting at the specified location. */
	public void runMacro(Program pgm, int macroLoc) {
		this.pgm = pgm;
		pc = macroLoc-1;
		instance = this;
		IJ.showStatus("interpreting");
		func = new Functions(this, pgm);
		pgm.startOfLocals = 0;
		doBlock();
		func.updateDisplay();
		instance = null;
		if (!statusUpdated) IJ.showStatus("");
		if (showingProgress) IJ.showProgress(1.0);
	}
	
	/** Pushes global variables onto the stack. */
	public void pushGlobals(Program pgm) {
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
		pgm.topOfGlobals = pgm.topOfStack;
	}

	final void getToken() {
		if (done)
			return;
		token = pgm.code[++pc];
		//IJ.log(pc+" "+pgm.decodeToken(token));
		if (token<=127)
			return;
		while (token==EOL && ignoreEOL)
			token = pgm.code[++pc];
		tokenAddress = token>>16;
		token = token&0xffff;
		Symbol sym = pgm.table[tokenAddress];
		tokenString = sym.str;
		tokenValue = sym.value;
		done = token==EOF;
	}

	final int nextToken() {
		return pgm.code[pc+1]&0xffff;
	}

	final void putTokenBack() {
		pc--;
		if (pc<0)
			pc = -1;
	}

	void doStatements() {
		while (!done)
			doStatement();
	}

	final void doStatement() {
		getToken();
		switch (token) {
			case VAR:
				doVar();
				break;
			case PREDEFINED_FUNCTION:
				func.doFunction(pgm.table[tokenAddress].type);
				break;
			case USER_FUNCTION:
				runUserFunction();
				break;
			case RETURN:
				doReturn();
				break;
			case WORD:
				doAssignment();
				break;
			case '(': case PLUS_PLUS:
				putTokenBack();
				getAssignmentExpression();
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
				skipMacro();
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
				putTokenBack();
				IJ.log(getString());
				return;
			case EOF: break;
			default:
				error("Statement cannot begin with '"+pgm.decodeToken(token, tokenAddress)+"'");
		}
		if (!looseSyntax) {
			getToken();
			if (token!=';')
				error("';' expected");
		}
	}

	Variable runUserFunction() {
		int newPC = (int)tokenValue;
		int saveStartOfLocals = pgm.startOfLocals;
		pgm.startOfLocals = pgm.topOfStack+1;
		int saveTOS = pgm.topOfStack;		
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
			value = new Variable(0, e.value, e.str);
		}
		inFunction = saveInFunction;
		pc = savePC;
		pgm.trimStack(saveTOS, saveStartOfLocals);
		return value;
	}

	/** Push function arguments onto the stack. */
	int pushArgs() {
		getLeftParen();
		int count = 0;
		double[] values = new double[MAX_ARGS];
		if (nextToken()!=')') {
			do {
				if (count==MAX_ARGS)
					error("Too many arguments");
				values[count] = getExpression();
				count++;
				getToken();
			} while (token==',');
			putTokenBack();
		}
		int nArgs = count;
		while(count>0)
			pgm.push(0, values[--count], null, this);
		getRightParen();
		return nArgs;
	}

	void setupArgs(int nArgs) {
		getLeftParen();
		int i = pgm.topOfStack;
		int count = nArgs;
		if (nextToken()!=')') {
			do {
			   getToken();
			   if (i>=0)
				  pgm.stack[i].symTabIndex = tokenAddress;
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
	
	void doReturn() {
		if (inFunction) {
			double value = 0.0;
			String str = null;
			getToken();
			if (token!=';') {
				boolean isString = token==STRING_CONSTANT || token==STRING_FUNCTION;
				if (token==WORD) {
					Variable v = pgm.lookupVariable(tokenAddress);
					if (v!=null) {
						if (nextToken()==';')
							throw new ReturnException(v.getValue(), v.getString());
						else
							isString = v.getString()!=null;
					}
				}
				putTokenBack();
				if (isString)
					str = getString();
				else
					value = getExpression();
			}
			throw new ReturnException(value, str);
		} else
			error("Return outside of function");
	}
	
	void doFor() {
		boolean saveLooseSyntax = looseSyntax;
		looseSyntax = false;
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
		//IJ.log("token: "+pgm.decodeToken(token,tokenAddress));
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
			if (cond==1)
				doStatement();
			else {
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
	}

	void doWhile() {
		looseSyntax = false;
		int savePC = pc;
		boolean isTrue;
		do {
			pc = savePC;
			isTrue = getBoolean();
			if (isTrue)
				doStatement();
			else
				skipStatement();
		} while (isTrue && !done);
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
			if(token=='}')
				break;
			putTokenBack();
			doStatement();
		}
		if (token!='}')
			error("'}' expected");
	}

	final void skipStatement() {
		getToken();
		//IJ.write("skipStatement: " +pgm.decodeToken(token, tokenAddress));
		switch (token) {
			case PREDEFINED_FUNCTION: case USER_FUNCTION: case VAR:
			case WORD: case '(': case PLUS_PLUS: case RETURN:
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
			case ';':
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

	void skipMacro() {
		getToken(); // skip macro label
		skipBlock();
	}

	final void doAssignment() {
		int rightSideToken = pgm.code[pc+2]&0xff;
		if (rightSideToken==STRING_CONSTANT || rightSideToken==STRING_FUNCTION)
			doStringAssignment();
		else {
			putTokenBack();
			getAssignmentExpression();
		}
	}

	final void doStringAssignment() {
		Variable v = pgm.lookupVariable(tokenAddress);
		if (v==null) {
			if (nextToken()=='=')
				v = pgm.push(tokenAddress, 0.0, null, this);
			else
				error("Undefined identifier");
		}
		getToken();
		if (token!='=') {
			error("'=' expected");
			return;
		}
		v.setString(getString());
	}

	final void doIf() {
		looseSyntax = false;
		boolean b = getBoolean();
		if (b)
			doStatement();
		else
			skipStatement();
		getToken();
		if (token==ELSE) {
			if (b)
				skipStatement();
			else
				doStatement();
		} else
			putTokenBack();
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
		double v2 = getBooleanExpression();
		checkBoolean(v2);
		if (op==LOGICAL_AND)
			return (int)v1 & (int)v2;
		else if (op==LOGICAL_OR)
			return (int)v1 | (int)v2;
		return v1;
	}

	final double getBooleanExpression() {
		double v1 = getExpression();
		int next = nextToken();
		if (next>=EQ && next<=LTE) {
			getToken();
			int op = token;
			double v2 = getExpression();
			//IJ.log("getBooleanExpression: "+v1+" "+op+" "+v2);
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
		}
		return v1;
	}

	final double getAssignmentExpression() {
		int tokPlus2 = pgm.code[pc+2];
		if ((pgm.code[pc+1]&0xff)==WORD && (tokPlus2=='='||tokPlus2==PLUS_EQUAL
		||tokPlus2==MINUS_EQUAL||tokPlus2==MUL_EQUAL||tokPlus2==DIV_EQUAL)) {
			getToken();
			Variable v = pgm.lookupVariable(tokenAddress);
			if (v==null)
				v = pgm.push(tokenAddress, 0.0, null, this);
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
				Variable v = pgm.lookupVariable(tokenAddress);
				if (v==null)
					pgm.push(tokenAddress, 0.0, null, this);
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
		getLeftParen();
		getRightParen();
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
		boolean showMessage = !done;
		token = EOF;
		tokenString = "";
		IJ.showStatus("");
		if (showMessage) {
			String line = getErrorLine();
			IJ.showMessage("Macro Error", message+" in line "+lineNumber+".\n \n"+line);
			throw new RuntimeException("Macro canceled");
		}
		done = true;
	}

	String getErrorLine() {
		int savePC = pc;
		lineNumber = 1;
		ignoreEOL = false;
		pc = -1;
		int lineStart = -1;
		while(pc<savePC) {
			getToken();
			if (token==EOL) {
				lineNumber++;
				lineStart = pc;
			}
		}
		String line = "";
		pc = lineStart;
		getToken();
		String str;
		double v;
		while (token!=EOL && !done) {
			str = pgm.decodeToken(token, tokenAddress);
			if (pc==savePC)
				str = "<"+str+">";
			line += str+" ";
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
		};
		return str;
	}

	final String getStringTerm() {
		String str;
		getToken();
		switch (token) {
		case STRING_CONSTANT:
			str = tokenString;
			break;
		case STRING_FUNCTION:
			str = func.getStringFunction(pgm.table[tokenAddress].type);
			break;
		case USER_FUNCTION:
			Variable v = runUserFunction();
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
			double value = getStringExpression();
			if ((int)value==value)
				str = IJ.d2s(value,0);
			else {
				str = ""+value;
				if (value!=Double.POSITIVE_INFINITY && value!=Double.NEGATIVE_INFINITY
						&& value!=Double.NaN && (str.length()-str.indexOf('.'))>6 && str.indexOf('E')==-1)
					str = IJ.d2s(value, 4);
			}
		}
		return str;
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
			case USER_FUNCTION:
				v = runUserFunction();
				if (v==null)
					error("No return value");
				if (v.getString()!=null)
					error("Numeric return value expected");
				else
					value = v.getValue();
				break;
			case TRUE:
				value = 1.0;
				break;
			case FALSE:
				value = 0.0;
				break;
			case PI:
				value = Math.PI;
				break;
			case WORD:
				v = lookupNumericVariable();
				if (v==null)
					return 0.0;
				value = v.getValue();
				int next = nextToken();
				if (!(next==PLUS_PLUS || next==MINUS_MINUS))
					break;
				getToken();
				if (token==PLUS_PLUS)
					v.setValue(v.getValue()+1);
				else
					v.setValue(v.getValue()-1);
				break;
			case (int)'(':
				value = getLogicalExpression();
				getRightParen();
				break;
			case PLUS_PLUS:
				value = getFactor();
				value++;
				//if (v!=null)
				//	v.setValue(value);
				break;
			case MINUS_MINUS:
				value = getFactor();
				value--;
				//if (v!=null)
				//	v.setValue(value);
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

	final Variable lookupNumericVariable() {
		Variable v = null;
		if (pgm.stack==null) {
			undefined();
			return v;
		}
		boolean found = false;
		for (int i=pgm.topOfStack; i>=0; i--) {
			if (pgm.stack[i].symTabIndex==tokenAddress) {
				found = true;
				v = pgm.stack[i];
				break;
			}
		}
		if (!found)
			undefined();
		return v;
	}

	final String lookupStringVariable() {
		if (pgm.stack==null) {
			undefined();
			return "";
		}
		boolean found = false;
		String str = null;
		for (int i=pgm.topOfStack; i>=0; i--) {
			if (pgm.stack[i].symTabIndex==tokenAddress) {
				found = true;
				str = pgm.stack[i].getString();
				break;
			}
		}
		if (!found)
			undefined();
		return str;
	}

	void undefined() {
		if (nextToken()=='(')
			error("Undefined identifier");
		else
			error("Undefined variable");
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
		if (pgm.stack!=null)
			for (int i=pgm.topOfStack; i>=0; i--)
				IJ.log(i+" "+pgm.stack[i]+" "+pgm.table[pgm.stack[i].symTabIndex].str);
	}
	
	/** Aborts currently running macro. */
	public static void abort() {
		if (instance!=null) {
			instance.done = true;
			IJ.beep();
			IJ.showStatus("Macro aborted");
		}
	}

} // class Interpreter





