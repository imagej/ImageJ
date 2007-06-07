package ij.macro;

/** This runtime exceptions is thrown when return is invoked in a user-defined function. */
class ReturnException extends RuntimeException {
	double value;
	String str;
	
	ReturnException(double value, String str) {
		this.value = value;
		this.str = str;
	}

}
