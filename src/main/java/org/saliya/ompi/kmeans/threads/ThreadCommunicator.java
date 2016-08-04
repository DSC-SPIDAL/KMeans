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
    private AtomicInteger minTimingCount = new AtomicInteger(0);
    private AtomicInteger maxTimingCount = new AtomicInteger(0);
//    private double[] doubleBufferArray;
    private DoubleBuffer doubleBuffer;
    private int[] intBuffer;
    private boolean booleanBuffer;
    private double[] minTimingBuffer;
    private double[] maxTimingBuffer;

    public ThreadCommunicator(int numThreads, int numCenters, int dimensions) {
        this.numThreads = numThreads;
//        doubleBufferArray = new double[numThreads*numCenters*(dimensions+1)];
        doubleBuffer = DoubleBuffer.allocate(numThreads*numCenters*(dimensions+1));
        intBuffer = new int[ParallelOps.pointsForProc];
        minTimingBuffer = new double[numThreads];
        maxTimingBuffer = new double[numThreads];
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
    public void sumDoubleArrayOverThreads(int threadIdx, DoubleBuffer vals, int length) {
        sumCount.compareAndSet(numThreads, 0);

        // column values are placed nearby
        int idx;
        for (int i = 0; i < length; ++i){
            idx = (i* numThreads)+threadIdx;
            doubleBuffer.put(idx,vals.get(i));
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



    public void broadcastDoubleArrayOverThreads(int threadIdx, DoubleBuffer vals, int length, int root) {
        bcastDoubleCount.compareAndSet(numThreads, 0);
        if (threadIdx == root){
//            System.arraycopy(vals, 0, doubleBufferArray, 0, vals.length);
            for (int i = 0; i < length; ++i){
                doubleBuffer.put(i, vals.get(i));
            }
        }
        bcastDoubleCount.getAndIncrement();

        if (threadIdx != root){
            while (bcastDoubleCount.get() != numThreads) {
                ;
            }
            for (int i = 0; i < length; ++i){
                vals.put(i, doubleBuffer.get(i));
            }
//            System.arraycopy(doubleBufferArray, 0, vals, 0, vals.length);
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

    public double findMinOverThreads(int threadIdx, double val) {
        minTimingCount.compareAndSet(numThreads, 0);
        minTimingBuffer[threadIdx] = val;
        minTimingCount.getAndIncrement();

        if (threadIdx == 0){
            while (minTimingCount.get() != numThreads) {
                ;
            }
        }
        double tMin = minTimingBuffer[0];
        double tmp;
        for (int i = 1; i < numThreads; ++i){
            tmp = minTimingBuffer[i];
            if (tmp < tMin){
                tMin = tmp;
            }
        }
        return tMin;
    }

    public double findMaxOverThreads(int threadIdx, double val) {
        maxTimingCount.compareAndSet(numThreads, 0);
        maxTimingBuffer[threadIdx] = val;
        maxTimingCount.getAndIncrement();

        if (threadIdx == 0){
            while (maxTimingCount.get() != numThreads) {
                ;
            }
        }
        double tMax = maxTimingBuffer[0];
        double tmp;
        for (int i = 1; i < numThreads; ++i){
            tmp = maxTimingBuffer[i];
            if (tmp > tMax){
                tMax = tmp;
            }
        }
        return tMax;
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
