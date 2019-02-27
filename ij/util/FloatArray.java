package ij.util;

/** This class implements an expandable float array similar
 * to an ArrayList or Vector but more efficient.
 *  Calls to this class are not synchronized.
 * @author Michael Schmid
*/
 public class FloatArray {
	private int size;
	private float[] data;

	/** Creates a new expandable array with an initial capacity of 100. */
	public FloatArray() {
		this(100);
	}

	/** Creates a new expandable array with specified initial capacity.
	 * @throws IllegalArgumentException if the specified initial capacity is less than zero */
	public FloatArray(int initialCapacity) {
		if (initialCapacity < 0) throw new IllegalArgumentException("Illegal FloatArray Capacity: "+initialCapacity);
		data = new float[initialCapacity];
	}

	/** Returns the number of elements in the array. */
	public int size() {
		return size;
	}

	/** Returns a float array containing all elements of this expandable array. */
	public float[] toArray() {
		float[] out = new float[size];
		System.arraycopy(data, 0, out, 0, size);
		return out;
	}

	/** Returns the element at the specified position in this expandable array.
	 *  @throws IndexOutOfBoundsException - if index is out of range (<code>index < 0 || index >= size()</code>). */
	public float get(int index) {
		if (index<0 || index>= size) throw new IndexOutOfBoundsException("FloatArray Index out of Bounds: "+index);
		return data[index];
	}

	/** Replaces the element at the specified position with the value. 
	 *  @return the value previously at the specified position.
	 *  @throws  - if index is out of range (<code>index < 0 || index >= size()</code>). */
	public float set(int index, float value) {
		if (index<0 || index>= size) throw new IndexOutOfBoundsException("FloatArray Index out of Bounds: "+index);
		float previousValue = data[index];
		data[index] = value;
		return previousValue;
	}

	/** Appends the specified value to the end of this expandable array. Returns the number of elements after adding. */
	public int add(float value) {
		if (size >= data.length) {
			float[] newData = new float[size*2+50];
			System.arraycopy(data, 0, newData, 0, size);
			data = newData;
		}
		data[size++] = value;
		return size;
	}

	/** Trims the capacity of this FloatArray instance to be its current size,
	 *  minimizing the storage of the FloatArray instance. */
	public void trimToSize() {
		data = toArray();
	}
	
}
