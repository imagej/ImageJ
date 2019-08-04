package ij.macro;

/** This runtime exceptions is thrown when return is invoked in a user-defined function. */
class ReturnException extends RuntimeException {
	double value;
	String str;
	Variable[] array;
	int arraySize;
	
	ReturnException() {
	}
}
