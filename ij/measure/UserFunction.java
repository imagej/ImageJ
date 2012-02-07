package ij.measure;


/**
 * A plugin should implement this interface for minimizing a single-valued function
 * or fitting a curve with a custom fit function
 */
public interface UserFunction {
    /**
     * A user-supplied function
     * @param params    When minimizing, array of variables.
     *                  For curve fit array of fit parameters.
     *                  The array contents should not be modified.
     *                  Note that the function can get an array with more
     *                  elements then needed to specify the parameters.
     *                  Ignore the rest (and don't modify them).
     * @param x         For a fit function, the independent variable of the function.
     *                  Ignore it when using the minimizer.
     * @return          The result of the function.
     */
    public double userFunction(double[] params, double x);
}

