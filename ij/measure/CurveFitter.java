package ij.measure;

/** Curve fitting class based on the Simplex method described
	in the article "Fitting Curves to Data" in the May 1984
	issue of Byte magazine, pages 340-362. */

public class CurveFitter {

	public static final int STRAIGHT_LINE=0,POLY2=1,POLY3=2,POLY4=3,
		EXPONENTIAL=4,POWER=5,LOG=6,RODBARD=7;

	public static final String[] fitList = {"Straight Line","2nd Degree Polynomial",
		"3rd Degree Polynomial", "4th Degree Polynomial","Exponential","Power",
		"log","Rodbard"};

	public static final String[] fList = {"y=a+bx","y=a+bx+cx^2",
		"y=a+bx+cx^2+dx^3", "y=a+bx+cx^2+dx^3+ex^4","y=aexp(bx)","y=ax^b",
		"y=aln(bx)","y=c*((a-x)/(x-d))^(1/b)"};

	private static final int maxn = 6;
	private static final double alpha = 1.0;	//reflection coefficient
	private static final double beta = 0.5;		//contraction coefficient
	private static final double gamma = 2.0;	//expansion coefficient
	private static final double root2 = 1.414214;
	private static final double maxError = 1e-7;
	
	private int fit;
	private double[] xdata, ydata;
	private int np; // number of data points
	private int m;  // number of coefficients
	private int n;  // m+1
	private int[] h = new int[maxn];
	private int[] l = new int[maxn];
	private double[][] simp = new double[maxn][maxn]; //the simplex
	private double[] next = new double[maxn]; //new vertex to be tested
	private double[] error = new double[maxn];
	private double[] maxerr = new double[maxn];
	private double[] smean;
	private int niter = 0;
	private int maxiter;
	
	/** Construct a new CurveFitter. */
	public CurveFitter (double[] xdata, double[] ydata) {
		this.xdata = xdata;
		this.ydata = ydata;
		np = xdata.length;
	}
	
	public int nCoefficients() {
		switch (fit) {
			case STRAIGHT_LINE: return 2;
			case POLY2: return 3;
			case POLY3: return 4;
			case POLY4: return 5;
			case EXPONENTIAL: return 2;
			case POWER: return 2;
			case LOG: return 2;
			case RODBARD: return 4;
		}
		return 0;
	}
	
	public void doFit(int fitType) {
		if (fitType<STRAIGHT_LINE || fitType>RODBARD)
			throw new IllegalArgumentException("Invalid fit type");
		fit = fitType;
		m = nCoefficients();
		initialize();
		niter = 0;
		boolean done;
		double[] center = new double[maxn];
		do {
			done = true;
			niter++;
			for (int i=0; i<n; i++)
				center[i] = 0.0;
			for (int i=0; i<n; i++)
				if (i!=h[n-1])
					for (int j=0; j<m; j++)
						center[j] += simp[i][j];
			for (int i=0; i<n; i++) {
				center[i] /= m;
				next[i] = (1.0+alpha)*center[i] - alpha*simp[h[n-1]][i];
			}
			sum_of_residuals(next);
			if (next[n-1]<=simp[l[n-1]][n-1]) {
				new_vertex();
				for (int i=0; i<m; i++)
					next[i] = gamma*simp[h[n-1]][i]+(1.0-gamma)*center[i];
				sum_of_residuals(next);
				if (next[n-1]<=simp[l[n-1]][n-1])
					new_vertex();
			} else {
				if (next[n-1]<=simp[h[n-1]][n-1])
					new_vertex();
				else {
					for (int i=0; i<m; i++)
						next[i] = beta*simp[h[n-1]][i]+(1.0-beta)*center[i];
					sum_of_residuals(next);
					if (next[n-1]<=simp[h[n-1]][n-1])
						new_vertex();
					else {
						for (int i=0; i<n; i++) {
							for (int j=0; j<m; j++)
								simp[i][j] = (simp[i][j]+simp[l[n-1]][j])*beta;
							sum_of_residuals(simp[i]);
						}
					}
				}
			}
			order();
			double sum = 0.0;
			for (int j=0; j<n; j++) {
				sum += simp[j][n-1];
				if (simp[h[j]][j]-simp[l[j]][j]==0)
					error[j] = 0;
				else if (simp[h[j]][j]!=0)
					error[j] = (simp[h[j]][j]-simp[l[j]][j])/simp[h[j]][j];
				else
					error[j] = (simp[h[j]][j]-simp[l[j]][j])/simp[l[j]][j];
				if (done && Math.abs(error[j])>maxerr[j])
					done = false;
			}
			//IJ.write("sum: "+sum);
			//done = (sum<maxError);
			//showSimplex(niter);
		} while (!done && niter<maxiter);
		//IJ.write("Iterations="+niter+" ("+maxiter+")");
	}
	
	public int getIterations() {
		return niter;
	}

	public int getMaxIterations() {
		return maxiter;
	}

	public double[] getCoefficients() {
		smean = new double[n];
		for (int i=0; i<n; i++)
			for (int j=0; j<n; j++)
				smean[j] += simp[i][j];
		for (int i=0; i<n; i++)
			smean[i] /= n;
		return smean;
	}
	
	public double[] getResiduals() {
		if (smean==null)
			smean = getCoefficients();
		double[] residuals = new double[np];
		for (int i=0; i<np; i++)
			residuals[i] = ydata[i]-f(fit,smean,xdata[i]);
		return residuals;
	}

	void initialize() {
		double firstx = xdata[0];
		double firsty = ydata[0];
		double lastx = xdata[np-1];
		double lasty = ydata[np-1];
		double xmean = (firstx+lastx)/2.0;
		double ymean = (firsty+lasty)/2.0;
		double slope;
		if ((lastx-firstx)!=0.0)
			slope = (lasty-firsty)/(lastx-firstx);
		else
			slope = 1.0;
		double yintercept = firsty-slope*firstx;
		switch (fit) {
			case STRAIGHT_LINE: 
				simp[0][0] = yintercept;
				simp[0][1] = slope;
				break;
			case POLY2: 
				simp[0][0] = yintercept;
				simp[0][1] = slope;
				simp[0][2] = 0.0;
				break;
			case POLY3: 
				simp[0][0] = yintercept;
				simp[0][1] = slope;
				simp[0][2] = 0.0;
				simp[0][3] = 0.0;
				break;
			case POLY4: 
				simp[0][0] = yintercept;
				simp[0][1] = slope;
				simp[0][2] = 0.0;
				simp[0][3] = 0.0;
				simp[0][4] = 0.0;
				break;
			case EXPONENTIAL: 
				simp[0][0] = 0.1;
				simp[0][1] = 0.01;
				break;
			case POWER: 
				simp[0][0] = 0.0;
				simp[0][1] = 1.0;
				break;
			case LOG: 
				simp[0][0] = 0.5;
				simp[0][1] = 0.05;
				break;
			case RODBARD: 
				simp[0][0] = firsty;
				simp[0][1] = 1.0;
				simp[0][2] = xmean;
				simp[0][3] = lasty;
				break;
		}
		maxiter = 200*m*m;
		n = m+1;
		double[] step = new double[maxn];
		for (int i=0; i<m; i++) {
			step[i] = simp[0][i]/2.0;
			if (step[i]==0.0)
				step[i] = 0.01;
		}
		for (int i=0; i<n; i++)
			maxerr[i] = maxError;
		sum_of_residuals(simp[0]);
		double[] p = new double[maxn];
		double[] q = new double[maxn];
		for (int i=0; i<m; i++) {
			p[i] = step[i]*(Math.sqrt(n)+m-1.0)/(m*root2);
			q[i] = step[i]*(Math.sqrt(n)-1.0)/(m*root2);
		}
		for (int i=1; i<n; i++) {
			for (int j=0; j<m; j++)
				simp[i][j] = simp[i-1][j]+q[j];
			simp[i][i-1] = simp[i][i-1]+p[i-1];
			sum_of_residuals(simp[i]);
		}
		for (int i=0; i<n; i++) {
			l[i] = 1;
			h[i] = 1;
		}
		order();
	}

	void showSimplex(int iter) {
		//ij.IJ.write(""+iter);
		//for (int i=0; i<n; i++) {
		//	String s = "";
		//	for (int j=0; j<n; j++)
		//		s += "  "+ij.IJ.d2s(simp[i][j],6);
		//	ij.IJ.write(s);
		//}
		String s = "";
		for (int i=0; i<n; i++)
			s += "    "+ij.IJ.d2s(error[i],8);
		ij.IJ.write(iter+" "+s);
	}

	public static double f(int fit, double[] p, double x) {
		switch (fit) {
			case STRAIGHT_LINE:
				return p[0]+p[1]*x;
			case POLY2: 
				return p[0]+p[1]*x+p[2]* x*x;
			case POLY3: 
				return p[0]+p[1]*x+p[2]*x*x+p[3]*x*x*x;
			case POLY4: 
				return p[0]+p[1]*x+p[2]*x*x+p[3]*x*x*x+p[4]*x*x*x*x;
			case EXPONENTIAL: 
				return p[0]*Math.exp(p[1]*x);
			case POWER: 
				if (x==0.0)
					return 0.0;
				else
					return p[0]*Math.exp(p[1]*Math.log(x)); //y=ax^b
			case LOG: 
				if (x==0.0)
					x = 0.5;
				return p[0]*Math.log(p[1]*x);
			case RODBARD:
				double ex; 
				if (x==0.0)
					ex = 0.0;
				else
					ex = Math.exp(Math.log(x/p[2])*p[1]);
				double y = p[0]-p[3];
				y = y/(1.0+ex);
				return y+p[3];
			default:
				return 0.0;
		}
	}

	double sqr(double d) {return d*d;}

	void sum_of_residuals (double[] x) {
		x[n-1] = 0.0;
		for (int i=0; i<np; i++) {
			x[n-1] = x[n-1]+sqr(f(fit,x,xdata[i])-ydata[i]);
			//ij.IJ.write(i+" "+x[n-1]+" "+f(fit,x,xdata[i])+" "+ydata[i]);
		}
	}

	void new_vertex() {
		for (int i=0; i<n; i++)
			simp[h[n-1]][i] = next[i];
	}
	
	void order() {
		for (int j=0; j<n; j++) {
			for (int i=0; i<n; i++) {
				if (simp[i][j]<simp[l[j]][j])
					l[j] = i;
				if (simp[i][j]>simp[h[j]][j])
					h[j] = i;
			}
		}
	}

}

/*
	type
		ColumnVector = array[1..maxnp] of extended;

		vector = array[1..maxn] of extended;
		datarow = array[1..nvpp] of extended;
		index = 0..255;


	var
		m, n: integer;
		done: boolean;
		maxx, maxy: extended;
		i, j: index;
		h, l: array[1..maxn] of index;
		np, npmax, niter, maxiter: integer;
		next, center, smean, error, maxerr, p, q, step: vector;
		simp: array[1..maxn] of vector;
		data: array[1..maxnp] of datarow;
		filename, newname: string;
		yoffset: integer;


	procedure DoSimplexFit (nStandards, nCoefficients: integer; xdata, ydata: ColumnVector; var Coefficients: CoefficientArray;
var residuals: ColumnVector);


implementation


	function f (p: vector; d: datarow): extended;
		var
			x, y, ex: extended;
	begin
		x := d[1];
		case info^.fit of
			StraightLine: 
				f := p[1] + p[2] * x;
			Poly2: 
				f := p[1] + p[2] * x + p[3] * x * x;
			Poly3: 
				f := p[1] + p[2] * x + p[3] * x * x + p[4] * x * x * x;
			Poly4: 
				f := p[1] + p[2] * x + p[3] * x * x + p[4] * x * x * x + p[5] * x * x * x * x;
			ExpoFit: 
				f := p[1] * exp(p[2] * x);
			PowerFit: 
				if x = 0.0 then
					f := 0.0
				else
					f := p[1] * exp(p[2] * ln(x)); {y=ax^b}
			LogFit: 
				begin
					if x = 0.0 then
						x := 0.5;
					f := p[1] * ln(p[2] * x)
				end;
			RodbardFit: 
				begin
					if x = 0.0 then
						ex := 0.0
					else
						ex := exp(ln(x / p[3]) * p[2]);
					y := p[1] - p[4];
					y := y / (1 + ex);
					f := y + p[4];
				end; {Rodbard fit}
		end; {case}
	end;


	procedure order;
		var
			i, j: index;
	begin
		for j := 1 to n do
			begin
				for i := 1 to n do
					begin
						if simp[i, j] < simp[l[j], j] then
							l[j] := i;
						if simp[i, j] > simp[h[j], j] then
							h[j] := i
					end
			end
	end;


	procedure sum_of_residuals (var x: vector);

		var
			i: index;
	begin
		x[n] := 0.0;
		for i := 1 to np do
			x[n] := x[n] + sqr(f(x, data[i]) - data[i, 2])
	end;


	procedure Initialize;
		var
			i, j: index;
			firstx, firsty, lastx, lasty, xmean, ymean, slope, yintercept: extended;
	begin
		firstx := data[1, 1];
		firsty := data[1, 2];
		lastx := data[np, 1];
		lasty := data[np, 2];
		xmean := (firstx + lastx) / 2.0;
		ymean := (firsty + lasty) / 2.0;
		if (lastx - firstx) <> 0.0 then
			slope := (lasty - firsty) / (lastx - firstx)
		else
			slope := 1.0;
		yintercept := firsty - slope * firstx;
		case info^.fit of
			StraightLine: 
				begin
					simp[1, 1] := yintercept;
					simp[1, 2] := slope;
				end;
			Poly2: 
				begin
					simp[1, 1] := yintercept;
					simp[1, 2] := slope;
					simp[1, 3] := 0.0;
				end;
			Poly3: 
				begin
					simp[1, 1] := yintercept;
					simp[1, 2] := slope;
					simp[1, 3] := 0.0;
					simp[1, 4] := 0.0;
				end;
			Poly4: 
				begin
					simp[1, 1] := yintercept;
					simp[1, 2] := slope;
					simp[1, 3] := 0.0;
					simp[1, 4] := 0.0;
					simp[1, 5] := 0.0;
				end;
			ExpoFit: 
				begin
					simp[1, 1] := 0.1;
					simp[1, 2] := 0.01;
				end;
			PowerFit: 
				begin
					simp[1, 1] := 0.0;
					simp[1, 2] := 1.0;
				end;
			LogFit: 
				begin
					simp[1, 1] := 0.5;
					simp[1, 2] := 0.05;
				end;
			RodbardFit: 
				begin
					simp[1, 1] := firsty;
					simp[1, 2] := 1.0;
					simp[1, 3] := xmean;
					simp[1, 4] := lasty;
				end;
		end;
		maxiter := 100 * m * m;
		n := m + 1;
		for i := 1 to m do
			begin
				step[i] := simp[1, i] / 2.0;
				if step[i] = 0.0 then
					step[i] := 0.01;
			end;
		for i := 1 to n do
			maxerr[i] := MaxError;
		sum_of_residuals(simp[1]);
		for i := 1 to m do
			begin
				p[i] := step[i] * (sqrt(n) + m - 1) / (m * root2);
				q[i] := step[i] * (sqrt(n) - 1) / (m * root2)
			end;
		for i := 2 to n do
			begin
				for j := 1 to m do
					simp[i, j] := simp[i - 1, j] + q[j];
				simp[i, i - 1] := simp[i, i - 1] + p[i - 1];
				sum_of_residuals(simp[i])
			end;
		for i := 1 to n do
			begin
				l[i] := 1;
				h[i] := 1
			end;
		order;
		maxx := 255;
	end;


	procedure new_vertex;
		var
			i: index;
	begin
		for i := 1 to n do
			simp[h[n], i] := next[i]
	end;


	procedure DoSimplexFit (nStandards, nCoefficients: integer; xdata, ydata: ColumnVector; var Coefficients: CoefficientArray;
var residuals: ColumnVector);
		var
			i, j: integer;
			d: datarow;
	begin
		np := nStandards;
		m := nCoefficients;
		for i := 1 to np do
			begin
				data[i, 1] := xdata[i];
				data[i, 2] := ydata[i];
			end;
		Initialize;
		niter := 0;
		repeat
			done := true;
			niter := succ(niter);
			for i := 1 to n do
				center[i] := 0.0;
			for i := 1 to n do
				if i <> h[n] then
					for j := 1 to m do
						center[j] := center[j] + simp[i, j];
			for i := 1 to n do
				begin
					center[i] := center[i] / m;
					next[i] := (1.0 + alpha) * center[i] - alpha * simp[h[n], i]
				end;
			sum_of_residuals(next);
			if next[n] <= simp[l[n], n] then
				begin
					new_vertex;
					for i := 1 to m do
						next[i] := gamma * simp[h[n], i] + (1.0 - gamma) * center[i];
					sum_of_residuals(next);
					if next[n] <= simp[l[n], n] then
						new_vertex
				end
			else
				begin
					if next[n] <= simp[h[n], n] then
						new_vertex
					else
						begin
							for i := 1 to m do
								next[i] := beta * simp[h[n], i] + (1.0 - beta) * center[i];
							sum_of_residuals(next);
							if (next[n] <= simp[h[n], n]) then
								new_vertex
							else
								begin
									for i := 1 to n do
										begin
											for j := 1 to m do
												simp[i, j] := (simp[i, j] + simp[l[n], j]) * beta;
											sum_of_residuals(simp[i])
										end
								end
						end
				end;
			order;
			for j := 1 to n do
				begin
					if (simp[h[j], j] - simp[l[j], j]) = 0 then
						error[j] := 0
					else if simp[h[j], j] <> 0 then
						error[j] := (simp[h[j], j] - simp[l[j], j]) / simp[h[j], j]
					else
						error[j] := (simp[h[j], j] - simp[l[j], j]) / simp[l[j], j];
					if done then
						if abs(error[j]) > maxerr[j] then
							done := false
				end;
		until (done or (niter = maxiter));
		ShowMessage(concat('interations=', long2str(niter), crStr, 'max interations=', long2str(maxiter)));
		for i := 1 to n do
			begin
				smean[i] := 0;
				for j := 1 to n do
					smean[i] := smean[i] + simp[j, i];
				smean[i] := smean[i] / n;
			end;
		for i := 1 to m do
			Coefficients[i] := smean[i];
		for i := 1 to nstandards do
			begin
				d[1] := xdata[i];
				Residuals[i] := ydata[i] - f(smean, d);
			end;
	end;


end.
*/

