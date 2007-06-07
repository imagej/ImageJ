package ij.measure;

public interface Measurements {
	public static final int AREA=1,MEAN=2,STD_DEV=4,MODE=8,MIN_MAX=16,
		CENTROID=32,CENTER_OF_MASS=64,PERIMETER=128, LIMIT = 256, RECT=512,
		LABELS=1024,ELLIPSE=2048,INVERT_Y=4096;
		
	/** Maximum number of calibration standard (20) */
	public static final int MAX_STANDARDS = 20;

}
