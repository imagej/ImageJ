package ij.macro;

/** This runtime exception is thrown by break and continue statements. */
class MacroException extends RuntimeException {
	private int type;
	
	MacroException(int type) {
		this.type = type;
	}
	
	int getType() {
		return type;
	}
	
}
