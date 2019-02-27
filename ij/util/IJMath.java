package ij.util;

public class IJMath {

	static double A = 0.05857895078654250866288;
	static double B = -0.00626245895772819579;
	static double C = -0.00299946450696036814;
	static double D = 0.289389696496082416;
	static double E = 0.0539962589851632982;
	static double F = 0.00508516909930653109;
	static double G = 0.000215969713046142876;
	static double H = -0.000225663858340491571;
	static double I = -3.06833213472529049e-7;

	/* This approximation of the error function erf has maximum absolute and relative errors below 1e-12
	 * Except for low and high x, it is based on the type erf(x) = sgn(x) * sqrt(1-exp(-x^2*f(x)),
	 * with the function f(x) having a smooth transition from 4/pi = 1.273... at x=0 to 1 at high x */
	public static double erf(double x) {
		double x2 = sqr(x);
		if (x2 < 1e-8)
			return (2/Math.sqrt(Math.PI))*x*(1+x2*(-1./3.+x2*(1./10.)));  // Taylor series for low x
		double erf = x2 > 36 ? 1 :  //the polynomials go crazy for some large x2; erf(6) is 1 - 2e-17, less than ulp from 1
				Math.sqrt(1 - Math.exp(-sqr(x*((2/Math.sqrt(Math.PI) - A) + A *
				(1 + x2*x2*(B + x2*(C + x2*(H + x2*I)))) /
				(1 + x2*(D + x2*(E + x2*(F + x2*G))))
				))));
		return x>0 ? erf : -erf; //could be Math.copySign in Java 1.6 & up
	}

	static double sqr(double x) {
		return x*x;
	}
	
}
