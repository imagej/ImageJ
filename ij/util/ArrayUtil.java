package ij.util;
import java.util.Arrays;

public class ArrayUtil {
	private int size;
	float[] values;
	boolean sorted;

	public void setSize(int si) {
		size = si;
	}

	/**
	 * constructeur
	 *
	 * @param size number of elements
	 */
	public ArrayUtil(int size) {
		this.size = size;
		values = new float[size];
		sorted = false;
	}
	
	/**
	 * constructeur
	 *
	 * @param data float array
	 */
	public ArrayUtil(float[] data) {
		this.size = data.length;
		sorted = false;
		values = data;
	}

	/**
	 * put a value to a index
	 *
	 * @param pos position in the array
	 * @param value value to put
	 * @return false if position does not exist
	 */
	public boolean putValue(int pos, float value) {
		if (pos<size) {
			values[pos] = value;
			sorted = false;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Average value
	 * @return average value
	 */
	public double getMean() {
		double total = 0;
		for (int i=0; i<size; i++)
			total += values[i];
		return total/size;
	}

	/**
	 * The median (sorted array)
	 * @return mediane
	 */
	public double medianSort() {
		if (!sorted)
			sort();
		if (size % 2==1)
			return values[size/2];
		else
			return (0.5f * (values[size/2 - 1] + values[size/2]));
	}

	public void sort() {
		if (size<values.length) {
			float[] tosort = new float[size];
			System.arraycopy(values, 0, tosort, 0, size);
			Arrays.sort(tosort);
			System.arraycopy(tosort, 0, values, 0, size);
		} else
			Arrays.sort(values);
		sorted = true;
	}

	/**
	 *
	 * @param val
	 * @return
	 */
	public boolean isMaximum(double val) {
		int i = 0;
		boolean maxok = true;
		while ((i<size) && (values[i]<=val)) {
			i++;
		}
		if (i < size) {
			maxok = false;
		}
		return maxok;
	}

	/**
	 * The minimum value
	 *
	 * @return min value
	 */
	public double getMinimum() {
		double min = values[0];
		for (int i = 1; i < size; i++) {
			if (values[i] < min) {
				min = values[i];
			}
		}
		return min;
	}

	/**
	 * The maximum value
	 *
	 * @return max value
	 */
	public double getMaximum() {
		double max = values[0];
		for (int i = 1; i < size; i++) {
			if (values[i] > max) {
				max = values[i];
			}
		}
		return max;
	}

	/**
	 * Variance value 
	 *
	 * @return variance
	 */
	public double getVariance() {
	   if (size == 1) {
			return 0;
		}

		double total = 0;
		double total2 = 0;

		for (int i = 0; i < size; i++) {
			total += values[i];
			total2 += values[i] * values[i];
		}

		double var = (double) ((total2 - (total * total / size)) / (size - 1));
		return var;
	}
	
	
	/**
	 * information to be displayed
	 *
	 * @return text
	 */
	public String toString() {
		String str = "{" + values[0];
		for (int i = 1; i < size; i++) {
			str = str + ", " + values[i];
		}
		return str + "}";
	}
}
