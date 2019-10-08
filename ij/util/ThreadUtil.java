package ij.util;
import java.util.concurrent.*;

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

	/*--------------------------------------------------------------------------*/
	/* The following is for parallelization using a ThreadPool, which avoids the
	 * overhead of creating threads, and is therefore faster if each thread has
	 * only a short task to perform */

	/** The threadPoolExecutor holds at least as many threads for parallel execution as the number of
	 *  processors; additional threads are added as required. These additional threads will be
	 *  terminated if idle for 120 seconds. */
	public static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
			Runtime.getRuntime().availableProcessors(),	//minimum number of threads
			Integer.MAX_VALUE,							//maximum number of threads
			120,										//unused threads are terminated after this time
			TimeUnit.SECONDS,
			new SynchronousQueue<Runnable>()			//requests will be processed immediately (not a real queue)
			);

	/** Starts all callables for parallel execution (using a ThreadPoolExecutor)
	 *  and waits until each of them has finished.
	 *  If the current thread is interrupted, each of the callables gets
	 *  cancelled and interrupted. Also in that case, waits until all callables have
	 *  finished. The 'interrupted' status of the current thread is
	 *  preserved, as required for preview in an ImageJ ExtendedPlugInFilter.
	 *  Note that ImageJ requires that all callables can run concurrently,
	 *  and none of them must stay in the queue while others run.
	 *  (This is required by the RankFilters, where the threads are not independent)
	 *  @param callables Array of tasks. If no return value is needed,
	 *  best use <code>Callable<Void></code> (then the <code>Void call()</code> method
	 *  should return null). If the array size is 1, the <code>call()</code> method
	 *  is executed in the current thread.
	 *  @return Array of the <code>java.util.concurrent.Future</code>s,
	 *  corresponding to the callables. If the call methods of the callables
	 *  return results, the get() methods of these Futures may be used to get the results.
	 */
	public static Future[] startAndJoin(Callable[] callables) {
		if (callables.length == 1) {	//special case: call in current thread and create a Future
			Object callResult = null;
			try {
				callResult = callables[0].call();
			} catch (Exception e) {
				ij.IJ.handleException(e);
			}
			final Object result = callResult;
			Future[] futures = new Future[] {
				new Future() {
					public boolean cancel(boolean mayInterruptIfRunning) {return false;}
					public Object get() {return result;}
					public Object get(long timeout, TimeUnit unit) {return result;}
					public boolean isCancelled() {return false;}
					public boolean isDone() {return true;}
				}	
			};
			return futures;
		} else {
			Future[] futures = start(callables);
			joinAll(futures);
			return futures;
		}
	}

	/** Starts all callables for parallel execution (using a ThreadPoolExecutor)
	 *  without waiting for the results.
	 *  @param callables Array of tasks; these might be <code>Callable<Void></code>
	 *  if no return value is needed (then the <code>call</code> methods should
	 *  return null).
	 *  @return Array of the <code>java.util.concurrent.Future</code>s,
	 *  corresponding to the callables. The futures may be used to wait for
	 *  completion of the callables or cancel them.
	 *  If the call methods of the callables return results, these Futures
	 *  may be used to get the results.
	 */
	public static Future[] start(Callable[] callables) {
		Future[] futures = new Future[callables.length];
		for (int i=0; i<callables.length; i++)
			futures[i] = threadPoolExecutor.submit(callables[i]);
		return futures;
	}

	/** Waits for completion of all <code>Callable</code>s corresponding to the
	 *  <code>Future</code>s given.
	 *  If the current thread is interrupted, each of the <code>Callable</code>s
	 *  gets cancelled and interrupted. Also in that case, this method waits
	 *  until all callables have finished.
	 *  The 'interrupted' status of the current thread is preserved,
	 *  as required for preview in an ImageJ ExtendedPlugInFilter.
	 */
	public static void joinAll(Future[] futures) {
		boolean interrupted = false;
		for (int i=0; i<futures.length; i++) {
			Future f = futures[i];
			try {
				f.get();
			} catch (InterruptedException e) {
				interrupted = true;
				for (int j=i; j<futures.length; j++)
					futures[j].cancel(true);
				i--;  //we still have to wait for completion of this one
			} catch (CancellationException e) { //cancellation is allowed, e.g. during preview
			} catch (Exception eOther) {
				ij.IJ.log("Error in thread called by "+Thread.currentThread().getName()+":\n"+eOther);
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
			threadPoolExecutor.purge();
		}
	}
}
