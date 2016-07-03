package org.saliya.ompi.kmeans.threads;

import org.saliya.ompi.kmeans.ParallelOps;

import java.nio.DoubleBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadCommunicator {
    private int numThreads;
    private AtomicInteger sumCount = new AtomicInteger(0);
    private AtomicInteger bcastDoubleCount = new AtomicInteger(0);
    private AtomicInteger bcastBoolCount = new AtomicInteger(0);
    private AtomicInteger collectCount = new AtomicInteger(0);
    private double[] doubleBufferArray;
    private DoubleBuffer doubleBuffer;
    private int[] intBuffer;
    private boolean booleanBuffer;

    public ThreadCommunicator(int numThreads, int numCenters, int dimensions) {
        this.numThreads = numThreads;
//        doubleBuffer = new double[numThreads*numCenters*(dimensions+1)];
        doubleBuffer = DoubleBuffer.allocate(numThreads*numCenters*(dimensions+1));
        intBuffer = new int[ParallelOps.pointsForProc];
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

        // column values are placed nearby
        int idx;
        for (int i = 0; i < vals.length; ++i){
            idx = (i* numThreads)+threadIdx;
            doubleBufferArray[idx] = vals[i];
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
                    sum += doubleBufferArray[pos+t];
                }
                vals[i] = sum;
            }
        }
    }

    public void sumDoubleArrayOverThreads(int threadIdx, DoubleBuffer vals, int length) {
        sumCount.compareAndSet(numThreads, 0);

        /* column values are placed nearby */
        int idx;
        for (int i = 0; i < length; ++i){
            idx = (i* numThreads)+threadIdx;
            doubleBuffer.put(idx, vals.get(i));
        }
        sumCount.getAndIncrement();
        // thread 0 waits for others to update
        if (threadIdx == 0) {
            while (sumCount.get() != numThreads) {
                ;
            }
            for (int i = 0; i < length; ++i) {
                double sum = 0.0;
                int pos = i*numThreads;
                for (int t = 0; t < numThreads; ++t) {
                    sum += doubleBuffer.get(pos+t);
                }
                vals.put(i, sum);
            }
        }
    }

    public void broadcastDoubleArrayOverThreads(int threadIdx, double[] vals, int root) {
        bcastDoubleCount.compareAndSet(numThreads, 0);
        if (threadIdx == root){
            System.arraycopy(vals, 0, doubleBufferArray, 0, vals.length);
        }
        bcastDoubleCount.getAndIncrement();

        if (threadIdx != root){
            while (bcastDoubleCount.get() != numThreads) {
                ;
            }
            System.arraycopy(doubleBufferArray, 0, vals, 0, vals.length);
        }
    }

    public void broadcastDoubleArrayOverThreads(int threadIdx, DoubleBuffer vals, int length, int root) {
        bcastDoubleCount.compareAndSet(numThreads, 0);
        if (threadIdx == root){
            vals.position(0);
            doubleBuffer.position(0);
            doubleBuffer.put(vals);
        }
        bcastDoubleCount.getAndIncrement();

        if (threadIdx != root){
            while (bcastDoubleCount.get() != numThreads) {
                ;
            }
            vals.position(0);
            doubleBuffer.position(0);
            for (int i = 0; i < length; ++i){
                vals.put(i, doubleBuffer.get(i));
            }
        }
    }


    public boolean bcastBooleanOverThreads(int threadIdx, boolean val, int root) {
        bcastBoolCount.compareAndSet(numThreads, 0);
        if (threadIdx == root){
            booleanBuffer = val;
        }
        bcastBoolCount.getAndIncrement();

        if (threadIdx != root){
            while (bcastBoolCount.get() != numThreads) {
                ;
            }
        }
        return booleanBuffer;
    }
    public int[] collect(int threadIdx, int[] val) {
        collectCount.compareAndSet(numThreads, 0);
        int pos = ParallelOps.pointStartIdxForThread[threadIdx];
        System.arraycopy(val, 0, intBuffer, pos, val.length);

        collectCount.getAndIncrement();
        while (collectCount.get() != numThreads) {
            ;
        }
        return intBuffer;
    }

}
