package ij.measure;


/** This class interpolates a set of points using natural cubic splines
 *	(assuming zero second derivatives at end points).
 *  Given a set of knots x (all different and arranged in increasing order)
 *  and function values y at these positions, the class build the spline
 *  that can be evaluated at any point xp within the range of x.
 *  It is based on the publication 
 *  Haysn Hornbeck "Fast Cubic Spline Interpolation"
 *  https://arxiv.org/abs/2001.09253 
 *  Implemented by Eugene Katrukha (katpyxa@gmail.com)
 *  to fit the layout of SplineFitter class of ImageJ
*/
public class SplineFitter {
	private double[] y2;
	private static int EXTEND_BY = 7;
	private int extendBy;
	private float[] xpoints, ypoints;
	private int npoints;
	private int[] ixpoints, iypoints;

	public SplineFitter(int[] x, int[] y, int n) {
		initSpline(x, y, n);
	}
	
	/** For closed curves: the first and last y value should be identical;
	 *	internally, a periodic continuation with a few will be used at both
	 *	ends */
	public SplineFitter(float[] x, float[] y, int n, boolean closed) {
		initSpline(x, y, n, closed);
	}

	public SplineFitter(float[] x, float[] y, int n) {
		initSpline(x, y, n, false);
	}
	
	/** Given arrays of data points x[0..n-1] and y[0..n-1], computes the
	values of the second derivative at each of the data points
	y2[0..n-1] for use in the evalSpline() function. */
	private void initSpline(int[] x, int[] y, int n) {		
		int j;
		double new_x,new_y,old_x,old_y,new_dj, old_dj;
		double aj,bj,dj,cj;
		double inv_denom;
		y2 = new double[n];	 // cached
		double[] c_p = new double[n];
		
		//ends conditions:natural spline,
		//i.e. second derivative at ends is equal to zero
		c_p[0]=0;
		y2[0]=0;
		c_p[n-1]=0;
		y2[n-1]=0;
		
		//recycle these values in later routines
		new_x = x[1];
		new_y = y[1];
		cj = x[1]-x[0];
		new_dj = (y[1]-y[0])/cj;
		
		//forward substitution portion
		j=1;
		while(j<(n-1)) {
			old_x = new_x;
			old_y = new_y;
			aj=cj;
			old_dj = new_dj;
			//generate new quantities
			new_x = x[j+1];
			new_y = y[j+1];
			cj = new_x-old_x;
			new_dj = (new_y-old_y)/cj;
			bj = 2.0*(cj+aj);
			inv_denom = 1.0/(bj-aj*c_p[j-1]);
			dj = 6.0*(new_dj-old_dj);
			y2[j]= ( dj- aj*y2[j-1])*inv_denom;
			c_p[j] = cj*inv_denom;
			j+=1;
		}
		
		// backward substitution portion
		while (j>0) {
			j-=1;
			y2[j]=y2[j]-c_p[j]*y2[j+1];
		}
		ixpoints = x;
		iypoints = y;
		npoints = n;
	}
	
	private void initSpline(float[] x, float[] y, int n, boolean closed) {
		if (closed) {					//add periodic continuation at both ends
			extendBy = EXTEND_BY;
			if (extendBy>=n)
				extendBy = n - 1;
			int n2 = n + 2*extendBy;
			float[] xx = new float[n2];
			float[] yy = new float[n2];
			for (int i=0; i<extendBy; i++) {
				xx[i] = x[n-(extendBy-i+1)] - x[n-1];
				yy[i] = y[n-(extendBy-i+1)];
			}
			for (int i=extendBy; i<extendBy+n; i++) {
				xx[i] = x[i-extendBy];
				yy[i] = y[i-extendBy];
			}
			for (int i=extendBy+n; i<n2; i++) {
				xx[i] = x[i+1-(extendBy+n)] - x[0] + x[n-1];
				yy[i] = y[i+1-(extendBy+n)];
			}
			n = n2;
			x = xx;
			y = yy;
		}
		int j;
		double new_x,new_y,old_x,old_y,new_dj, old_dj;
		double aj,bj,dj,cj;
		double inv_denom;
		y2 = new double[n];	 // cached
		double[] c_p = new double[n];
		
		//ends conditions:natural spline,
		//i.e. second derivative at ends is equal to zero
		c_p[0]=0;
		y2[0]=0;
		c_p[n-1]=0;
		y2[n-1]=0;
		
		//recycle these values in later routines
		new_x = x[1];
		new_y = y[1];
		cj = x[1]-x[0];
		new_dj = (y[1]-y[0])/cj;
		
		//forward substitution portion
		j=1;
		while(j<(n-1)) {
			old_x = new_x;
			old_y = new_y;
			aj=cj;
			old_dj = new_dj;
			//generate new quantities
			new_x = x[j+1];
			new_y = y[j+1];
			cj = new_x-old_x;
			new_dj = (new_y-old_y)/cj;
			bj = 2.0*(cj+aj);
			inv_denom = 1.0/(bj-aj*c_p[j-1]);
			dj = 6.0*(new_dj-old_dj);
			y2[j]= ( dj- aj*y2[j-1])*inv_denom;
			c_p[j] = cj*inv_denom;
			j+=1;
		}
		
		// backward substitution portion
		while (j>0) {
			j-=1;
			y2[j]=y2[j]-c_p[j]*y2[j+1];
		}
		xpoints = x;
		ypoints = y;
		npoints = n;
	}
	
	/** Evalutes spline function at given point */
	public double evalSpline(double xp) {
		if (xpoints!=null)
			return evalSpline(xpoints, ypoints, npoints, xp);
		else
			return evalSpline(ixpoints, iypoints, npoints, xp);
	}
	
	/** provides interpolated function value at position xp**/
	public double evalSpline(int x[], int y[], int n, double xp) {
		int ls,rs,m;
		double ba,ba2,xa,bx, lower, C, D;
		
		//binary search of the interval
		ls = 0;
		rs = n-1;
		while (rs>1+ls) {
			m = (int) Math.floor(0.5*(ls+rs));
			if(x[m]<xp)
				ls=m;
			else rs = m;
		}
		ba = x[rs]-x[ls];
		xa = xp -x[ls];
		bx = x[rs]-xp;
		ba2 = ba*ba;
		lower = xa*y[rs]+bx*y[ls];
		C = (xa*xa-ba2)*xa*y2[rs];
		D = (bx*bx-ba2)*bx*y2[ls];
		return (lower +(C+D)/6.0)/ba;
	}
	
	public double evalSpline(float x[], float y[], int n, double xp) {
		int ls,rs,m;
		double ba,ba2,xa,bx, lower, C, D;
		
		//binary search of the interval
		ls = 0;
		rs = n-1;
		while (rs>1+ls) {
			m = (int) Math.floor(0.5*(ls+rs));
			if(x[m]<xp)
				ls=m;
			else rs = m;
		}
		ba = x[rs]-x[ls];
		xa = xp -x[ls];
		bx = x[rs]-xp;
		ba2 = ba*ba;
		lower = xa*y[rs]+bx*y[ls];
		C = (xa*xa-ba2)*xa*y2[rs];
		D = (bx*bx-ba2)*bx*y2[ls];
		return (lower +(C+D)/6.0)/ba;
	}
		
}
