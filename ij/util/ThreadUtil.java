package ij.util;

public class ThreadUtil {
	
	/** Start all given threads and wait on each of them until all are done.
	 * From Stephan Preibisch's Multithreading.java class. See:
	 * http://repo.or.cz/w/trakem2.git?a=blob;f=mpi/fruitfly/general/MultiThreading.java;hb=HEAD
	 * @param threads 
	 */
	public static void startAndJoin(Thread[] threads) {
		for (int ithread = 0; ithread < threads.length; ++ithread) {
			threads[ithread].setPriority(Thread.NORM_PRIORITY);
			threads[ithread].start();
		}

		try {
			for (int ithread = 0; ithread < threads.length; ++ithread) {
				threads[ithread].join();
			}
		} catch (InterruptedException ie) {
			throw new RuntimeException(ie);
		}
	}

	public static Thread[] createThreadArray(int nb) {
		if (nb == 0) {
			nb = getNbCpus();
		}
		Thread[] threads = new Thread[nb];

		return threads;
	}

	public static Thread[] createThreadArray() {
		return createThreadArray(0);
	}

	public static int getNbCpus() {
		return Runtime.getRuntime().availableProcessors();
	}

}
