package ij.measure;
import ij.*;
import ij.gui.*;
import ij.macro.*;
import ij.gui.Roi;
import ij.gui.PolygonRoi;
import ij.gui.Line;
import ij.util.Tools;
import ij.plugin.frame.RoiManager;
import java.util.Random;
import java.util.Arrays;
import java.util.Vector;
import java.util.Hashtable;

/** Minimizer based on Nelder-Mead simplex method (also known as polytope method),
 *  including the 'outside contraction' as described in:
 *      J.C. Lagarias, J.A. Reeds, M.H. Wright, P. Wright:
 *      Convergence properties of the Nelder-Mead simplex algorithm in low dimensions.
 *      SIAM J. Optim. 9, 112-147 (1998).
 * Differences w.r.t. this publication:
 * - If outside contraction is rejected, instead of shrinking the whole simplex, an inside
 *   contraction is tried first. Own experiments show that this results in slightly better
 *   performance for some test functions (Perm, Mc Kinnon's function with tau=2, theta=6,
 *   Osborne1 curve-fitting problem). In most cases, there is no difference, however.
 * - This implementation does not include any 'ordering rules' in case of equal function values.
 * - When checking for convergence, a special iteration step may be performed, improving
 *   the best vertex of the simplex.
 *
 * Re-initialization within a minimization run:
 *   In some cases, the simplex algorithm may fail to find the minimum or the convergence
 *   check might stop it prematurely. Therefore, the search is initialized again, keeping the
 *   best vertex of the simplex and setting the other vertices to random values, but keeping
 *   variations of the parameters w.r.t. the best vertex at a similar value. If re-initializing
 *   the simplex does not lead to a significant improvement, the value is accepted as true
 *   (local) minimum.
 *
 * Multiple minimization runs:
 *   In spite of re-initializing (see above), there are rare cases where minimization is stopped
 *   too early. Also, minimization may result in a local minimum. Therefore, unless determined
 *   otherwise by setting 'setRestarts', two minimization runs with different initialization
 *   of the simplex are started in parallel threads. If the results don't agree within the
 *   error limit, two more minimization runs are started. This is repeated until the two best
 *   results agree within the error limits or the number of restarts (determined by 'setRestarts';
 *   default 2, i.e., up to 3 runs with two threads each) is exceeded.
 *   This does not guarantee that the minimum is a global minimum, however: A local minimum
 *   will be accepted if the minimizer finds a local minimum twice (or two different local
 *   minima with the same function value within the error bounds), but no better minimum has
 *   been found at that time.
 *
 * The user-supplied target function should return NaN for out-of-bounds parameters instead
 * of a high (penalty) value (minimization is faster and more reliable with NaNs).
 * The region where the function is defined (e.g. not returning NaN) must be convex.
 * Sharp corners of the region where the function value is defined (especially in higher dimensions)
 * may cause a problem with finding suitable test points when (re-)initializing the simplex.
 * If all attempts to find initial points result in NaN, the status returned is
 * INITIALIZATION_FAILURE.
 *
 * Versions:
 * Michael Schmid 2012-01-30: first version, based on previous CurveFitter
 * 2012-11-20: mor tries to find initial params not leading to NaN
 * 2013-09-24: 50% higher maximum iteration count, and never uses more than 0.4*maxIter
 *             iterations per minimization to avoid trying too few sets of initial params
 *
 */
public class Minimizer {
    /** Status returned: successful completion */
    public final static int SUCCESS = 0;
    /** Status returned: Could not initialize the simplex because either the initialParams
     *  resulted in the target function returning NaN or all attempts to find starting
     *  parameters for the other simplex points resulted in the target function returning NaN.
     *  No minimization was possible. */    
    public final static int INITIALIZATION_FAILURE = 1;
    /** Status returned: aborted by call to abort method.  */
    public final static int ABORTED = 2;
    /** Status returned: Could not reinitialize the simplex because all attempts to find restarting
     *  parameters resulted in the target function returning NaN.  Reinitialization is
     *  needed to obtain a reliable result; so the result may be inaccurate or wrong. */    
    public final static int REINITIALIZATION_FAILURE = 3;
    /** Status returned: no convergence detected after maximum iteration count */
    public final static int MAX_ITERATIONS_EXCEEDED = 4;
    /** Status returned: not two equal solutions after maximum number of restarts */
    public final static int MAX_RESTARTS_EXCEEDED = 5;
    /** Strings describing the status codes */
    public final static String[] STATUS_STRING = { "Success",
            "Initialization failure; no result",
            "Aborted",
            "Re-initialization failure (inaccurate result?)",
            "Max. no. of iterations reached (inaccurate result?)",
            "Max. no. of restarts reached (inaccurate result?)"};


    private final static double C_REFLECTION  = 1.0;  // reflection coefficient
    private final static double C_CONTRACTION = 0.5;  // contraction coefficient
    private final static double C_EXPANSION   = 2.0;  // expansion coefficient
    private final static double C_SHRINK      = 0.5;  // shrink coefficient
    private final static int    ITER_FACTOR   = 750;  // maximum number of iterations per numParams^2; twice that value for 2 threads
    private final static int WORST=0, NEXT_WORST=1, BEST=2;//indices in array to pass the numbers of the respective vertices

    private int numParams;                  // number of independent variables (parameters)
    private int numVertices;                // numParams+1, number of vertices of the simplex
    private int numExtraArrayElements;      // number of extra array elements for private use in user function
    private UserFunction userFunction;      // target function to minimize
    private double maxRelError = 1e-10;     // max relative error
    private double maxAbsError = 1e-100;    // max absolute error
    private double[] paramResolutions;      // differences of parameters less than these values are considered 0
    private int maxIter;                    // stops after this number of iterations
    private int totalNumIter;               // number of iterations performed in all tries so far
    private int numCompletedMinimizations;  // number of minimizations completed
	private int maxRestarts = 2;            // number of times to try finding local minima; each uses 2 threads if restarts>0
	private int randomSeed;                 // for starting the random number generator
	private boolean useSingleThread =       // single thread if one processor or set by useSingleThread(true)
	        Runtime.getRuntime().availableProcessors() <= 1;
	private int status;                     // SUCCESS or error code
	private boolean wasInitialized;         // initialization was successful at least once
    private double[] result;                // result data+function value
    private Vector<double[]> resultsVector; // holds several results if multiple tries; the best one is kept.
    /*private Hashtable<Thread, double[][]> simpTable =
            new Hashtable<Thread, double[][]>(); //for each thread, holds a reference to its simplex */

    //-----------------------------------------------------------------------------


    // We can't set the function in the constructor because the CurveFitter
    // allows specifying the fit function after other variables
    /** 
     *  Set the the target function, i.e. function whose value should be minimized. 
     *  @param userFunction The class having a function to minimize (implementing
     *                      the UserFunction interface).
     *                      This function must allow simultaneous calls in multiple threads unless
     *                      setMaximumThreads(1); has been called.
     *                      Note that the function will be called with at least numParams+1 array
     *                      elements; the last one is needed to store the function value. Further
     *                      array elements for private use in the user function may be added by
     *                      calling setExtraArrayElements.
     *  @param numParams    Number of independent variables (also called parameters)
     *                      of the function.
     */
    public void setFunction(UserFunction userFunction, int numParams) {
        if (maxIter<=0) {
            maxIter = ITER_FACTOR*numParams*numParams;
            if (maxRestarts > 0)
                maxIter *= 2;
        }
        this.userFunction = userFunction;
        this.numParams = numParams;
        this.numVertices = numParams+1;
    }

    /** Perform minimization with the gradient-enhanced simplex method once or a few
     *  times, depending on the value of 'restarts'. Running it several times helps
     *  to reduce the probability of finding local minima or accepting one of the rare
     *  results where the minimizer has got stuck before finding the true minimum.
     *  We are using two threads and terminate after two equal results. Thus, apart
     *  from the overhead of starting a new thread (typically < 1 ms), for unproblematic
     *  minimization problems on a dual-core machine this is almost as fast as running
     *  it once.
     *
     *  Use 'setFunction' first to define the function and number of parameters.
     *  Afterwards, use the 'getParams' method to access the result.
     *
     *  @param initialParams   Array with starting values of the parameters (variables).
     *                         When null, the starting values are assumed to be 0.
     *                         The target function must be defined (not returning NaN) for
     *                         the values specified as initialParams.
     *  @param initialParamVariations   Parameters (variables) are initially varied by up to +/-
     *                         this value. If not given (null), initial variations are taken as
     *                         10% of initial parameter value or 0.01 for parameters that are zero.
     *                         When this array is given, all elements must be positive (nonzero).
     *  If one or several initial parameters are zero, is advisable to set the initialParamVariations
     *  array to useful values indicating the typical order of magnitude of the parameters.
     *  For target functions with only one minimum, convergence is fastest with large values of
     *  initialParamVariations, so that the expected value is within initialParam+/-initialParamVariations.
     *  If local minima can occur, it is best to use a value close to the expected global minimum,
     *  and rather small initialParamVariations, much lower than the distance to the nearest local
     *  minimum.
     *
     *  @return                status code; SUCCESS if two attempts have found minima with the
     *                         same value (within the error bounds); so a minimum has been found
     *                         with very high probability.
     */
    public int minimize(final double[] initialParams, final double[] initialParamVariations) {
        status = SUCCESS;
        resultsVector = new Vector<double[]>();
        int maxLoopCount = maxRestarts+1;
        if (useSingleThread) maxLoopCount*=2;       // if we have only one thread, loop twice as many times
        for (int i=0; i<maxLoopCount; i++) {        // try several times, until we have twice the same result
            Thread secondThread = null;
            if (maxRestarts>0 && !useSingleThread) {  // set up 2nd thread to minimize
                final int seed = randomSeed+1000000+i;
                final Thread thread = new Thread(
                    new Runnable() {
                        final public void run() {
                            minimizeOnce(initialParams, initialParamVariations, seed);
                        }
                    }, "Minimizer-1"
                );
                thread.setPriority( Thread.currentThread().getPriority() );
                thread.start();
                secondThread = thread;
            }
            minimizeOnce(initialParams, initialParamVariations, randomSeed+i); //minimize in main thread
            if (secondThread != null) try {
                secondThread.join();                // wait until send thread is done
            } catch (InterruptedException e) {}
            if (resultsVector.size() == 0 && result==null)
                return status;
            if (result==null)
                result = (double[])resultsVector.get(0);
            for (double[] r : resultsVector)        // find best result so far
                if (value(r) < value(result))
                    result = r;
            if (status != SUCCESS && status != REINITIALIZATION_FAILURE && status != MAX_ITERATIONS_EXCEEDED)
                return status;                      // no more tries if permanent error or aborted
            if (totalNumIter >= maxIter)
                return MAX_ITERATIONS_EXCEEDED;     // no more tries if too many iterations
            for (int ir=0; ir<resultsVector.size(); ir++)
                if (!belowErrorLimit(value((double[])resultsVector.get(ir)), value(result), 1.0)) {
                    resultsVector.remove(ir);       // discard results that are significantly worse
                    ir --;
                }
            if (resultsVector.size() >= 2) return SUCCESS;  // if we have two (almost) equal results, it's enough
        } //for i <= maxRestarts
        return maxRestarts>0 ?
                MAX_RESTARTS_EXCEEDED :             // number of restarts exceeded without two equal results
                status;                             // if only one run was required, we can't have 2 equal results
    }

    /** Perform minimization with the simplex method once, including re-initialization until
     *  we have a stable solution.
     *  Use the 'getParams' method to access the result.
     *
     *  @param initialParams   Array with starting values of the parameters (variables).
     *                         When null, the starting values are assumed to be 0.
     *                         The target function must be defined (not returning NaN) for
     *                         the values specified as initialParams.
     *  @param initialParamVariations   Parameters (variables) are initially varied by up to +/-
     *                         this value. If not given (null), iniital variations are taken as
     *                         10% of inial parameter value or 0.01 for parameters that are zero.
     *                         When this array is given, all elements must be positive (nonzero).
     *  If one or several initial parameters are zero, is advisable to set the initialParamVariations
     *  array to useful values indicating the typical order of magnitude of the parameters.
     *  For target functions with only one minimum, convergence is fastest with large values of
     *  initialParamVariations, so that the expected value is within initialParam+/-initialParamVariations.
     *  If local minima can occur, it is best to use a value close to the expected global minimum,
     *  and rather small initialParamVariations, much lower than the distance to the nearest local
     *  minimum.
     *
     *  @return                status code; SUCCESS if it is considered likely that a minimum of the
     *                         target function has been found.
     */
    public int minimizeOnce(double[] initialParams, double[] initialParamVariations) {
        status = SUCCESS;
        minimizeOnce(initialParams, initialParamVariations, randomSeed);
        return status;
    }

    /** Get the result, i.e., the set of parameter values (i.e., variable values)
     *  from the best corner of the simplex. Note that the array returned may have more
     *  elements than numParams; ignore the rest.
     *  May return an array with only NaN values in case the minimize call has returned
     *  an INITIALIZATION_FAILURE status or that abort() has been called at the very
     *  beginning of the minimization.
     *  Do not call this method before minimization. */
    public double[] getParams() {
        if (result == null) {
            result = new double[numParams+1+numExtraArrayElements];
            Arrays.fill(result, Double.NaN);
        }
        return result;
    }

    /** Get the value of the minimum, i.e. the value associated with the resulting parameters
     *  as obtained by getParams(). May return NaN in case the minimize call has returned
     *  an INITIALIZATION_FAILURE status or that abort() has been called at the very
     *  beginning of the minimization.
     *  Do not call this method before minimization. */
    public double getFunctionValue() {
        if (result == null) {
            result = new double[numParams+1];
            Arrays.fill(result, Double.NaN);
        }
        return value(result);
    }

    /** Get number of iterations performed (includes all restarts). One iteration needs
     *  between one and numParams+3 calls of the target function (typically two calls
     *  per iteration) */
    public int getIterations() {
        return totalNumIter;
    }
        
    /** Set maximum number of iterations allowed (including all restarts and all threads).
     *  The number of function calls will be higher, up to about twice the number of
     *  iterations.
     *  Note that the number of iterations needed typically scales with the square of
     *  the dimensions (i.e., numParams^2).
     *  Default value is 1000 * numParams^2 (half that value if maxRestarts=0), which is
     *  enough for >99% of all cases (if the maximum number of restarts is set to 2);
     *  typical number of iterations are below 10 and 20% of this value.
     */
    public void setMaxIterations(int x) {
        maxIter = x;
    }

    /** Get maximum number of iterations allowed. Unless given by 'setMaxIterations',
     *  this value is defined only after running 'setFunction' */
    public int getMaxIterations() {
        return maxIter;
    }

    /** Set maximum number of minimization restarts to do.
     *  With n=0, the minimizer is run once in a single thread.
     *  With n>0, two threads are used, and if the two results do not agree within
     *  the error bounds, additional optimizations are done up to n times, each
     *  with two threads. In any case, if the two best results are within the error
     *  bounds, the best result is accepted.
     *  Thus, on dual-core machines running no other jobs, values of n=1 or n=2 (default)
     *  do not cause a notable increase of computing time for 'easy' optimization problems,
     *  while greatly reducing the risk of running into spurious local minima or non-
     *  optimal results due to the minimizer getting stuck. In problematic cases, the
     *  improved 
     *  The 'n' value does not refer to the restarts within one minimization run
     *  (there, at least one restart is performed, and restart is repeated until the result
     *  does not change within the error bounds).
     *  This value does not affect the 'minimizeOnce' function call.
     *  When setting the maximum number of restarts to a value much higher than 3, remember
     *  to adjust the maximum number of iterations (see setMaxIterations).
     */
    public void setMaxRestarts(int n) {
        maxRestarts = n;
    }

    /** Get maximum number of minimization restarts to do */
    public int getMaxRestarts() {
        return maxRestarts;
    }
    /** Get number of minimizations completed (i.e. not aborted or stopped because the
     *  number of minimization was exceeded). After a minimize(..) call, typically 2
     *  for unproblematic cases. Higher number indicate a functin that is difficult to
     *  minimize or the existence of more than one minimum.
     */
    public int getCompletedMinimizations() {
        return numCompletedMinimizations;
    }

    /** Set a seed to initialize the random number generator, which is used for initialization
     *  of the simplex. */
    public void setRandomSeed(int n) {
        randomSeed = n;
    }

    /** Sets the accuracy of convergence. Minimizing is done as long as the
     *  relative error of the function value is more than this number (Default: 1e-10).
     */
    public void setMaxError(double maxRelError) {
        this.maxRelError = maxRelError;
    }

    /** Sets the accuracy of convergence. Minimizing is done as long as the
     *  relative error of the function value is more than maxRelError (Default: 1e-10)
     *  and the maximum absolute error is more than maxAbsError
     *  (i.e. it is enough to fulfil one of these two criteria)
     */
    public void setMaxError(double maxRelError, double maxAbsError) {
        this.maxRelError = maxRelError;
        this.maxAbsError = maxAbsError;
    }

    /** Set the resolution of the parameters, for problems where the target function is not smooth
     *  but suffers from numerical noise. If all parameters of all vertices are closer to the
     *  best value than the respective resolution value, minimization is finished, irrespective
     *  of the difference of the target function values at the vertices */
    public void setParamResolutions(double[] paramResolutions) {
        this.paramResolutions = paramResolutions;
    }

    /** Call setMaximuThreads(1) to avoid multi-threaded execution (in case the user-provided
     *  target function does not allow moultithreading). Currently a maximum of 2 thread is used
     *  irrespective of any higher value. */
    public void setMaximumThreads (int numThreads) {
        useSingleThread = numThreads <= 1;
    }

    /** Aborts minimization. Calls to getParams() will return the best solution found so far.
     *  This method may be called from the user-supplied target function, e.g. when it checks
     *  for IJ.escapePressed(), allowing the user to abort a lengthy minimization. */
    public void abort() {
        status = ABORTED;
    }

    /** Add a given number of extra elements to array of parameters (independent vaiables)
     *  for private use in the user function.  Note that the first numParams+1 elements
     *  should not be touched.*/
    public void setExtraArrayElements(int numExtraArrayElements) {
        this.numExtraArrayElements = numExtraArrayElements;
    }
    /** Get the full simplex of the current thread. This may be useful if the target function
     *  wants to modify the simplex */
     /* public double[][] getSimplex() {
        return simpTable.get(Thread.currentThread());
     } */

    /** One minimization run (including reinitializations of the simplex until the result is stable) */
    private void minimizeOnce(double[] initialParams, double[] initialParamVariations, int seed) {
        Random random = new Random(seed);
        double[][] simp = makeSimplex(initialParams, initialParamVariations, random);
        if (simp == null) {
            status = wasInitialized ? REINITIALIZATION_FAILURE : INITIALIZATION_FAILURE;
            return;
        }
        wasInitialized = true;
        //if (IJ.debugMode) showSimplex(simp, seed+" Initialized:");
        int bestVertexNumber = minimize(simp);          // first minimization
        double bestValueSoFar = value(simp[bestVertexNumber]);
        //reinitialize until converged or error/aborted (don't care about reinitialization failure in other thread)
        boolean reinitialisationFailure = false;
        while (status == SUCCESS || status == REINITIALIZATION_FAILURE) {
            double[] paramVariations =
                    makeNewParamVariations(simp, bestVertexNumber, initialParams, initialParamVariations);
            if (!reInitializeSimplex(simp, bestVertexNumber, paramVariations, random)) {
                reinitialisationFailure = true;
                break;
            }
            //if (IJ.debugMode) showSimplex(simp, seed+" Reinitialized:");
            bestVertexNumber = minimize(simp);          // minimize with reinitialized simplex
            if (belowErrorLimit(value(simp[bestVertexNumber]), bestValueSoFar, 2.0)) break;
            bestValueSoFar = value(simp[bestVertexNumber]);
        }
        if (reinitialisationFailure)
            status = REINITIALIZATION_FAILURE;
        else if (status == SUCCESS || status == REINITIALIZATION_FAILURE) //i.e. not aborted, not max iterations exceeded
            numCompletedMinimizations++;                // it was a complete minimization
        //if (IJ.debugMode) showSimplex(simp, seed+" Final:");
        if (resultsVector != null) synchronized(resultsVector) {
            resultsVector.add(simp[bestVertexNumber]);
        } else
            result = simp[bestVertexNumber];
    }

    /** Minimizes the target function by variation of the simplex.
     *  Note that one call to this function never does more than 0.4*maxIter iterations.
     *  @return index of the best value in simp
     */
    private int minimize(double[][] simp) {
        int[] worstNextBestArray = new int[3];          // used to pass these indices from 'order' function
        double[] center     = new double[numParams+1+numExtraArrayElements];    // center of all vertices except worst
        double[] reflected  = new double[numParams+1+numExtraArrayElements];  // the 1st new vertex to try
        double[] secondTry  = new double[numParams+1+numExtraArrayElements];  // expanded or contracted vertex

        order(simp, worstNextBestArray);
        int worst = worstNextBestArray[WORST];
        int nextWorst = worstNextBestArray[NEXT_WORST];
        int best = worstNextBestArray[BEST];
        //showSimplex(simp, "before minimization, value="+value(simp[best]));

        //String operation="ini";
        int thisNumIter=0;
        while (true) {
            totalNumIter++;                                 // global count over all threads
            thisNumIter++;                                  // local count for this minimize call
            // THE MINIMIZAION ALGORITHM IS HERE
            iteration: {
                getCenter(simp, worst, center);             // centroid of vertices except worst
                // Reflect worst vertex through centriod of not-worst
                getVertexAndEvaluate(center, simp[worst], -C_REFLECTION, reflected);
                if (value(reflected) <= value(simp[best])) {      // if it's better than the best...
                    // try expanding it
                    getVertexAndEvaluate(center, simp[worst], -C_EXPANSION, secondTry);
                    if (value(secondTry) <= value(reflected)) {
                        copyVertex(secondTry, simp[worst]);  // if expanded is better than reflected, keep it
                        //operation="expa";
                        break iteration;
                    }
                }
                if (value(reflected) < value(simp[nextWorst])) {
                    copyVertex(reflected, simp[worst]);     // keep reflected if better than 2nd worst
                    //operation="refl";
                    break iteration;
                } else if (value(reflected) < value(simp[worst])) {
                    // try outer contraction
                    getVertexAndEvaluate(center, simp[worst], -C_CONTRACTION, secondTry);
                    if (value(secondTry) <= value(reflected)) {
                        copyVertex(secondTry, simp[worst]); // keep outer contraction
                        //operation="outC";
                        break iteration;
                    }
                } else if (value(reflected) > value(simp[worst]) || Double.isNaN(value(reflected))) {
                    // else inner contraction
                    getVertexAndEvaluate(center, simp[worst], C_CONTRACTION, secondTry);
                    if (value(secondTry) < value(simp[worst])) {
                        copyVertex(secondTry, simp[worst]);     // keep contracted if better than 2nd worst
                        //operation="innC";
                        break iteration;
                    }
                }
                // if everything else has failed, contract simplex in on best
                shrinkSimplexAndEvaluate(simp, best);
                //operation="shri";
                break iteration;
            } // iteration:
            boolean checkParamResolution =    // if new 'worst' is not close to 'best', don't check any further
                    paramResolutions!=null && belowResolutionLimit(simp[worst], simp[best]);
            order(simp, worstNextBestArray);
            worst = worstNextBestArray[WORST];
            nextWorst = worstNextBestArray[NEXT_WORST];
            best = worstNextBestArray[BEST];

            if (checkParamResolution)
                if (belowResolutionLimit(simp, best))       // check whether all parameters are within the resolution limit
                    break;
            if (belowErrorLimit(value(simp[best]), value(simp[worst]), 4.0)) {   // make sure we are at the minimum:
                getCenter(simp, -1, secondTry);             // check center of the simplex
                evaluate(secondTry);
                if (value(secondTry) < value(simp[best]))
                    copyVertex(secondTry, simp[best]);      // better than best: keep
            }
            if (belowErrorLimit(value(simp[best]), value(simp[worst]), 4.0)) // no large spread of values
                break;                                      // looks like we are at the minimum
            if (totalNumIter > maxIter || thisNumIter>4*(maxIter/10))
                status = MAX_ITERATIONS_EXCEEDED;
            if (status != SUCCESS)
                break;
        }
        //showSimplex(simp, "after "+totalNumIter+" iterations: value="+value(simp[best]));
        return best;
    }

    /** Move along the line between center and worst, where +1 corresponds to worstVertex
     *  The new point is written to'newVertex' and target function is evaluated there
     */
    private void getVertexAndEvaluate(double[] center, double[] worstVertex, double howFar, double[] newVertex) {
        for (int i = 0; i < numParams; i++)
            newVertex[i] = (1.-howFar)*center[i] + howFar*worstVertex[i];
        evaluate(newVertex);
    }

    /** Get center of all vertices except one to exclude
     *  (may be -1 to exclude none)
     *  Does not care about function values (i.e., last array elements) */
    private void getCenter(double[][]simp, int excludeVertex, double[] center) {
        Arrays.fill(center, 0.);
        int nV = 0;
        for (int v=0; v<numVertices; v++)
            if (v != excludeVertex) {
                for (int i=0; i<numParams; i++)
                    center[i] += simp[v][i];
                nV++;
            }
        double norm = 1.0/nV;
        for (int i=0; i<numParams; i++)
            center[i] *= norm;
    }

    private void shrinkSimplexAndEvaluate(double[][] simp, int best) {
        for (int v=0; v<numVertices; v++)
            if (v != best) {
                for (int i=0; i<numParams; i++)
                    simp[v][i] = C_SHRINK*simp[v][i] + (1-C_SHRINK)*simp[best][i];
                evaluate(simp[v]);
            }
    }

    /** Whether the highest and lowest values fulfill the error limit criterion.
     *  Error limits are reduced by a factor of 'sensitivity' */
    private boolean belowErrorLimit(double highest, double lowest, double sensitivity) {
        double absError = sensitivity*Math.abs(highest - lowest);
        double relError = absError/(Math.max(Math.abs(highest),Math.abs(lowest))+1e-100);
        return relError < maxRelError || absError < maxAbsError;
    }

    /** Initialise the simplex and evaluate it. Returns null on failure. */
    private double[][] makeSimplex(double[] initialParams, double[] initialParamVariations, Random random) {
        double[][] simp = new double[numVertices][numParams+1+numExtraArrayElements];
        /* simpTable.put(Thread.currentThread(), simp); */
        
        if (initialParams!=null) {
            for (int i=0; i<numParams; i++)
                if (Double.isNaN(initialParams[i]))
                    if (IJ.debugMode) IJ.log("Warning: Initial Parameter["+i+"] is NaN");
            System.arraycopy(initialParams, 0, simp[0], 0, Math.min(initialParams.length, numParams));
        }
        evaluate(simp[0]);
        if (Double.isNaN(value(simp[0]))) {
            if (IJ.debugMode) showVertex(simp[0], "Warning: Initial Parameters yield NaN:");
            findValidInitalParams(simp[0], initialParamVariations, random);
        }
        if (Double.isNaN(value(simp[0]))) {
            if (IJ.debugMode) IJ.log("Error: Could not find initial parameters not yielding NaN:");
            return null;
        }
        if (initializeSimplex(simp, initialParamVariations, random))
            return simp;
        else {
            if (IJ.debugMode) showSimplex(simp, "Error: Could not make simplex vertices not yielding NaN");
            return null;
        }
    }

    /** Whether the distance between the best and any other simplex vertex
     *  is less than the resolution of the parameters */
    private boolean belowResolutionLimit(double[][] simp, int best) {
        for (int v=0; v<numVertices; v++)
            if (v!= best && !belowResolutionLimit(simp[v], simp[best]))
                return false;
        return true;
    }

    /** Whether the distance between two vertices is less than the resolution of the parameters */
    private boolean belowResolutionLimit(double[] vertex1, double[] vertex2) {
        for (int i=0; i<numParams; i++)
            if (Math.abs(vertex1[i]-vertex2[i]) >= paramResolutions[i])
                return false;
        return true;
    }

    /** Find initial parameters not yielding a result of NaN in case those given yield NaN
     *  Called with params containing the initial parameters that have been tried previously */
    private void findValidInitalParams(double[] params, double[] initialParamVariations, Random random) {
        final int maxAttempts = 50*numParams*numParams;  //max number of attempts to find params that do not lead to NaN
        double rangeFactor = 1;             // will gradually become larger to handle different orders of magnitude
        final double rangeMultiplyLog = Math.log(1e20)/(maxAttempts-1); //will try up to 1e-20 to 1e20*initialParamVariations
        double[] firstParams = new double[numParams]; // remember starting params (which may be modified)
        double[] variations = new double[numParams];    // new values of parameter variations
        for (int i=0; i<numParams; i++) {
            firstParams[i] = Double.isNaN(params[i]) ? 0 : params[i];
            variations[i] = initialParamVariations!=null ? initialParamVariations[i] : 0.1*firstParams[i];
            if (Double.isNaN(variations[i]) || Math.abs(variations[i])<1e-10 || Math.abs(variations[i])>1e10)
                variations[i] = 0.1;
        }
        for (int attempt=0; attempt<maxAttempts; attempt++) {
            for (int i=0; i<numParams; i++) {
                double multiplier = attempt<maxAttempts/10 ? 1 :  //after a while try different orders of magnitude
                        Math.exp(rangeMultiplyLog*attempt*2*(random.nextDouble()-0.5));
                params[i] = multiplier*(firstParams[i]+2*(random.nextDouble()-0.5)*variations[i]);
            }
            evaluate(params);
            //showVertex(params,"findValidInitalParams attempt "+attempt);
            if (!Double.isNaN(value(params))) return;    //found a valid parameter set
        }
    }


    /** Reinitialize an existing simplex: Create new vertices around the best one, keeping the rough size of
     *  the simplex. This helps to avoid premature termination or cases where the simplex has become degenerate.
     *  All points of the new simplex are evaluated already.
     *  Returns false on error (if it could not create a simplex not resulting in NaN). */
    private boolean reInitializeSimplex(double[][] simp, int bestVertexNumber, double[] paramVariations, Random random) {
        if (bestVertexNumber != 0) {
            double[] swap = simp[0];
            simp[0] = simp[bestVertexNumber];
            simp[bestVertexNumber] = swap;
        }
        return initializeSimplex(simp, paramVariations, random);
    }

    /** Initialize simplex vertices except the number 0, which will be taken as starting point.
     *  All points of the new simplex are evaluated already.
     *  Returns false on error (if it could not create a simplex not resulting in NaN).
     */
    private boolean initializeSimplex(double[][] simp, double[] paramVariations, Random random) {
        double[] variations = new double[numParams];    // improved values of parameter variations
        double smallestRange = Double.MAX_VALUE;
        double largestRange = 0;
        for (int i=0; i<numParams; i++) {
            double range = paramVariations!=null && i<paramVariations.length ?
                    paramVariations[i] : 0.1 * Math.abs(simp[0][i]); // if nothing else given, based on initial parameter
            if (range < 1e-100 || Double.isNaN(range))
                range = 0.01;                   //range must be nonzero
            if (Math.abs(range/simp[0][i])<1e-10)
                range = Math.abs(simp[0][i]*1e-10); //range must be more than very last digits
            variations[i] = range;
            if (range < smallestRange) smallestRange = range;
            if (range > largestRange) largestRange = range;
        }
        //IJ.log("smallestR="+smallestRange+" largestR="+largestRange);
        final int maxAttempts = 100*numParams;  //max number of attempts to find params that do not lead to NaN
        for (int v=1; v<numVertices; v++) {
            int numTries = 0;
            do {                                //try finding a vertex that does not yield NaN
                if (numTries++ > maxAttempts)
                    return false;
                // Create a vector orthogonal to all others in a coordinate system where every value is
                // normalized to its respective variations value.
                // Don't orthogonalize after getting only NaN function values in many attempts; it might be
                // easier to find viable parameters without normalization.
                for (int i=0; i<numParams; i++)
                    simp[v][i] = variations[i]*(random.nextFloat() - 0.5);
                if (numTries < maxAttempts/2 &&    // (don't orthogonalize if finding valid params was very difficult
                        largestRange < smallestRange*1e16) {   //... or if orthogonalization won't work due to extremely different parameter ranges
                    for (int v1=1; v1<v; v1++) {    // to avoid a degenerate simplex, make orthogonal to all others
                        double lengthSqr = 0;
                        double innerProduct = 0;
                        for (int i=0; i<numParams; i++) {
                            double x = (simp[v1][i] - simp[0][i])/variations[i];
                            lengthSqr += x*x;
                            innerProduct += x*simp[v][i]/variations[i];
                        }
                        for (int i=0; i<numParams; i++)
                            simp[v][i] -= (simp[v1][i] - simp[0][i])/variations[i] * innerProduct/lengthSqr;
                    }
                } //else IJ.log("no orthogonalization v#"+v);
                double sumSqr = 0;        // normalize the 'try vector' to desired parameter variation range
                for (int i=0; i<numParams; i++) {
                    double ratio = simp[v][i] / variations[i];
                    sumSqr += ratio*ratio;
                }
                double nonZeroRandom = -1 + 1.8*random.nextFloat();
                if (nonZeroRandom > -0.1)
                    nonZeroRandom += 0.2;   //random number between -1..-0.1 or +0.1..1
                double normalizationFactor = nonZeroRandom/(Math.sqrt(sumSqr));
                for (int i=0; i<numParams; i++)
                    simp[v][i] = simp[0][i] + simp[v][i]*normalizationFactor;
                //IJ.log("[0] var="+paramVariations[0]+"simp0="+simp[0][0]+" rand="+IJ.d2s(nonZeroRandom,4)+" simpV="+simp[v][0]);
                evaluate(simp[v]);
            } while (Double.isNaN(value(simp[v])) && (status == SUCCESS || status == REINITIALIZATION_FAILURE));
        }
        //showSimplex(simp, 0);
        return true;
    }

    /** Calculate ranges for parameter variations for re-initializing:
     *  Note that the simplex should have adapted in size to the useful range of each
     *  parameter, but in special cases it might have become degenerate [i.e. the
     *  (hyper-)volume spanned by the vertices might be zero; in such a case it may
     *  happen that one parameter has the same value for all vertices of the simplex].
     *
     *  For each parameter, as a variation value we use the typical difference w.r.t. the best
     *  vertex of the simplex.
     *  To avoid a degenerate simplex, we have to know what the the relative orders of magnitude of
     *  the parameter variations should be.  If initialParamVariations are given, we use them as
     *  indication of the typoical relative values; otherwise we use the parameter values themselves
     *  and assume that the variation of each parameter should be roughly the same fraction of the value.
     *  If the variation of a parameter in the simplex w.r.t. this estimated variation is much
     *  smaller (<10^3) than typical for the parameters, we increase the variation for this parameter.
     */
    private double[] makeNewParamVariations(double[][] simp, int bestVertexNumber,
            double[] initialParams, double[] initialParamVariations) {
        double[] paramVariations = new double[numParams];   // will be the return value
        double[] relatedTo = new double[numParams];         // parameter variation should be related to (in size) these values
        double logTypicalRelativeVariation = 0;
        for (int i=0; i<numParams; i++) {                   // for all parameters
            double variation = 0;                           // roughly size of simplex in direction of that parameter
            for (int v=0; v<numVertices; v++)
                if (v != bestVertexNumber) {
                    double delta = Math.abs(simp[v][i] - simp[bestVertexNumber][i]);
                    paramVariations[i] += delta*delta;
                }
            paramVariations[i] = 10*Math.sqrt(paramVariations[i]); // make the new simplex larger than the old one
            relatedTo[i] = initialParamVariations!=null && initialParamVariations.length>= numParams ?
                    initialParamVariations[i] :             // relate to initialParamVariations (if given),
                    Math.max(Math.abs(initialParams[i]), Math.abs(simp[bestVertexNumber][i]));
            double logRelativeVariation = paramVariations[i]>relatedTo[i] ? 0 :
                    Math.log(paramVariations[i]/relatedTo[i]);
            logTypicalRelativeVariation += logRelativeVariation;
        }
        logTypicalRelativeVariation /= numParams;
        double typicalRelativeVariation = Math.exp(logTypicalRelativeVariation);
        final double WORST_RATIO = 1e-3;        //parameter variation should not be lower than typical value by more than this
        for (int i=0; i<numParams; i++)
            if (paramVariations[i]<relatedTo[i] && paramVariations[i]/relatedTo[i] < typicalRelativeVariation*WORST_RATIO)
                paramVariations[i] = relatedTo[i]*typicalRelativeVariation*WORST_RATIO;
        return paramVariations;
    }

    /** Evaluate the target function for the parameters given by 'vertex'
     *  The function value is stored into the last (extra) array element.
     */
    private void evaluate(double[] vertex) {
        vertex[numParams] = userFunction.userFunction(vertex, 0);
    }

    /** Function value of a vertex after call to 'evaluate' 
     *  (stored it as last array element) */
    private double value(double[] vertex) {
        return vertex[numParams];
    }

    /** Keep the newVertex: replace a given Vertex with it */
    void copyVertex(double[] newVertex, double[] vertex) {
        System.arraycopy(newVertex, 0, vertex, 0, newVertex.length);
    }
    
    /** Find the simplex vertices with the worst, nextWorst and best values
     *  of the target function */
    private void order(double[][] simp, int[] worstNextBestArray) {
        int worst = 0, nextWorst = 0, best = 0;
        for (int i = 0; i < numVertices; i++) {
            if (value(simp[i]) < value(simp[best])) best = i;
            if (value(simp[i]) > value(simp[worst])) worst = i;
        }
        nextWorst = best;
        for (int i = 0; i < numVertices; i++)
            if (i != worst && value(simp[i]) > value(simp[nextWorst]))
                nextWorst = i;
        worstNextBestArray[WORST] = worst;
        worstNextBestArray[NEXT_WORST] = nextWorst;
        worstNextBestArray[BEST] = best;
    }

    // Display simplex [Iteration: s0(p1, p2....), s1(),....] in Log window
    private synchronized void showSimplex(double[][] simp, String heading) {
        IJ.log("Minimizer: "+heading);
        for (int i = 0; i < numVertices; i++)
            showVertex(simp[i], null);
    }
    private synchronized void showVertex(double[] vertex, String heading) {
        if (heading != null)
            IJ.log(heading);
        String s = "";
        for (int j=0; j < numParams; j++)
            s += "  " + IJ.d2s(vertex[j], 8,12);
        s += " -> " +  IJ.d2s(value(vertex), 8,12);
        IJ.log(s);
    }
}