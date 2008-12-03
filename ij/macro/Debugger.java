package ij.macro;

	/** Classes that implement this interface are called when the
		macro interpreter starts to interpret another statement. */
	public interface Debugger {

	public int debug(Interpreter interp);

}


