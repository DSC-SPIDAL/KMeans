package org.saliya.ompi.kmeans.threads;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadCommunicator {
    private int numThreads;
    private final int numCenters;
    private final int dimensions;
    private double sum = 0;
    private AtomicInteger sumCount = new AtomicInteger(0);
    private double[] doubleBuffer;

    public ThreadCommunicator(int numThreads, int numCenters, int dimensions) {
        this.numThreads = numThreads;
        this.numCenters = numCenters;
        this.dimensions = dimensions;
        doubleBuffer = new double[numThreads*numCenters*(dimensions+1)];
    }

    /* The  structure of the vals should be
    * | d0 d1 d2 .. dD N | d0 d1 d2 .. dD N |
    * <--------C0-------> <-------Ci------->
    * <-----------------T------------------>
    *
    * Ci is i th center. 0 <= i < numCenters
    * T is the calling thread
    * D is dimension of the point
    * N is the count of points assigned to the corresponding center
    */
    public void sumDoubleArrayOverThreads(int threadIdx, double[] vals) {
        sumCount.compareAndSet(numThreads, 0);

        /* column values are placed nearby */
        int idx;
        for (int i = 0; i < vals.length; ++i){
            idx = (i* numThreads)+threadIdx;
            doubleBuffer[idx] = vals[i];
        }
        sumCount.getAndIncrement();
        // thread 0 waits for others to update
        if (threadIdx == 0) {
            while (sumCount.get() != numThreads) {
                ;
            }
            for (int i = 0; i < vals.length; ++i) {
                double sum = 0.0;
                int pos = i*numThreads;
                for (int t = 0; t < numThreads; ++t) {
                    sum += doubleBuffer[pos+t];
                }
                vals[i] = sum;
            }
        }
    }

    public void broadcastDoubleArrayOverThreads(int threadIdx, double[] vals, int root) {
        sumCount.compareAndSet(numThreads, 0);
        if (threadIdx == root){
            System.arraycopy(vals, 0, doubleBuffer, 0, vals.length);
        }
        sumCount.getAndIncrement();

        if (threadIdx != root){
            while (sumCount.get() != numThreads) {
                ;
            }
            System.arraycopy(doubleBuffer, 0, vals, 0, vals.length);
        }
    }
}
