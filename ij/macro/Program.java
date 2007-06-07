package ij.macro;
import ij.*;

/** An object of this type is a tokenized macro file and the associated symbol table and stack. */
public class Program implements MacroConstants {

	static final int STACK_SIZE=1000;

	private int maxSymbols = 1000;
	private int maxProgramSize = 5000;
	private int pc = -1;
	Variable[] stack;
	int topOfStack = -1;
	int topOfGlobals = -1;
	int startOfLocals = 0;

	int stLoc = -1;
	int symTabLoc;
	Symbol[] table = new Symbol[maxSymbols];
	int[] code = new int[maxProgramSize];

	public Program() {
		addKeywords();
		addFunctions();
		addNumericFunctions();
		addStringFunctions();
	}
	
	public int[] getCode() {
		return code;
	}
	
	public Symbol[] getSymbolTable() {
		return table;
	}
	
	void addKeywords() {
		for (int i=0; i<keywords.length; i++)
			table[++stLoc] = new Symbol(keywordIDs[i], keywords[i]);
	}

	void addFunctions() {
		for (int i=0; i<functions.length; i++)
			table[++stLoc] = new Symbol(functionIDs[i], functions[i]);
	}

	void addNumericFunctions() {
		for (int i=0; i<numericFunctions.length; i++)
			table[++stLoc] = new Symbol(numericFunctionIDs[i], numericFunctions[i]);
	}
	
	void addStringFunctions() {
		for (int i=0; i<stringFunctions.length; i++)
			table[++stLoc] = new Symbol(stringFunctionIDs[i], stringFunctions[i]);
	}

	void addSymbol(Symbol sym) {
		if (stLoc<(maxSymbols-1))
			stLoc++;
		table[stLoc] = sym;
	}
	
	void addToken(int tok) {
		if (pc<(maxProgramSize-1))
			pc++;
		code[pc] = tok;
	}

	/** Looks up a word in the symbol table. Returns null if the word is not found. */
	Symbol lookupWord(String str) {
		Symbol symbol = null;
		String symStr;
		for (int i=0; i<maxSymbols; i++) {
			symbol = table[i];
			if (symbol==null)
				break;
			symStr = symbol.str;
			if (symStr!=null && str.equals(symStr)) {
				symTabLoc = i;
				break;
			}
		}
		return symbol;
	}

	final Variable lookupVariable(int symTabAddress) {
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
	    //IJ.log("trimStack: "+topOfStack);
	}
	
	public void dumpSymbolTable() {
		IJ.log("");
		IJ.log("Symbol Table");
		for (int i=0; i<=maxSymbols; i++) {
			Symbol symbol = table[i];
			if (symbol==null)
				break;
			IJ.log(i+" "+symbol);
		}
	}

	public void dumpProgram() {
		IJ.log("");
		IJ.log("Tokenized Program");
		String str;
		int token, address;
		for (int i=0; i<=pc; i++) 
			IJ.log(i+"	"+(code[i]&0xffff)+"  "+decodeToken(code[i]));
	}

	public String decodeToken(int token) {
		return decodeToken(token&0xffff, token>>16);
	}

	String decodeToken(int token, int address) {
		String str;
		switch (token) {
			case WORD:
			case PREDEFINED_FUNCTION:
			case NUMERIC_FUNCTION:
			case STRING_FUNCTION:
			case USER_FUNCTION:
				str = table[address].str;
				break;
			case STRING_CONSTANT:
				str = "\""+table[address].str+"\"";
				break;
			case NUMBER:
				double v = table[address].value;
				if ((int)v==v)
					str = IJ.d2s(v,0);
				else
					str = ""+v;
				break;
			case EOL:
				str = "EOL";
				break;
			case EOF:
				str = "EOF";
				break;
			default:
				if (token<32) {
					switch (token) {
					case PLUS_PLUS:
						str="++";
						break;
					case MINUS_MINUS:
						str="--";
						break;
					case EQ:
						str="==";
						break;
					case NEQ:
						str="!=";
						break;
					case GT:
						str=">";
						break;
					case GTE:
						str=">=";
						break;
					case LT:
						str="<";
						break;
					case LTE:
						str="<=";
						break;
					default:
						str="";
						break;
					}
				} else if (token>=200) {
					str = table[address].str;
				} else {
					char s[] = new char[1];
					s[0] = (char)token;
					str = new String(s);
				}
				break;
		}
		return str;
	}

} // Program