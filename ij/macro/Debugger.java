package ij.macro;

	public interface Debugger {

		public static final int NOT_DEBUGGING=0, STEP=1, TRACE=2, FAST_TRACE=3,
			RUN_TO_COMPLETION=4, RUN_TO_CARET=5;
		
		public int debug(Interpreter interp, int mode);
			
}
