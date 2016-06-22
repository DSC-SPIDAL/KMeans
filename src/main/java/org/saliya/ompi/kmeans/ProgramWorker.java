package org.saliya.ompi.kmeans;

import com.google.common.base.Stopwatch;
import mpi.MPI;
import mpi.MPIException;
import org.saliya.ompi.kmeans.threads.ThreadCommunicator;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static edu.rice.hj.Module0.launchHabaneroApp;
import static edu.rice.hj.Module1.forallChunked;

public class ProgramWorker {

    private final Integer threadIdx;
    private ThreadCommunicator threadComm;
    private final int numPoints;
    private final int dimension;
    private final int numCenters;
    private final int maxIterations;
    private final double errorThreshold;
    private final int numThreads;
    private final double[] pointsForProc;
    private final double[] centers;

    public ProgramWorker(Integer threadIdx, ThreadCommunicator threadComm, int numPoints, int dimension, int numCenters, int maxIterations, double errorThreshold, int numThreads, double[] points, double[] centers) {
        this.threadIdx = threadIdx;
        this.threadComm = threadComm;

        this.numPoints = numPoints;
        this.dimension = dimension;
        this.numCenters = numCenters;
        this.maxIterations = maxIterations;
        this.errorThreshold = errorThreshold;
        this.numThreads = numThreads;
        this.pointsForProc = points;
        this.centers = centers;
    }

    public void run() throws MPIException {

        final double[] centerSumsAndCountsForThread = new double[numCenters*(dimension+1)];
        final int[] clusterAssignments = new int[ParallelOps.pointsForThread[threadIdx]];

        int itrCount = 0;
        boolean converged = false;
        print("  Computing K-Means .. ");
        Stopwatch loopTimer = threadIdx == 0 ? Stopwatch.createStarted(): null;
        long[] times = new long[]{0};
        while (!converged && itrCount < maxIterations) {
            ++itrCount;
            resetCenterSumsAndCounts(centerSumsAndCountsForThread);

            findNearesetCenters(dimension, numCenters, pointsForProc, centers, centerSumsAndCountsForThread,
                    clusterAssignments, threadIdx);

            if (numThreads > 1) {
                // Sum over threads
                // Place results to arrays of thread 0
                threadComm.sumDoubleArrayOverThreads(threadIdx, centerSumsAndCountsForThread);
            }

            if (ParallelOps.worldProcsCount > 1 && threadIdx == 0) {
                ParallelOps.allReduceSum(centerSumsAndCountsForThread, 0, numCenters*(dimension+1));
            }

            if (numThreads > 1){
                threadComm.broadcastDoubleArrayOverThreads(threadIdx, centerSumsAndCountsForThread, 0);
            }

            converged = true;
            for (int i = 0; i < numCenters; ++i) {
                final int c = i;
                IntStream.range(0, dimension).forEach(j -> centerSumsAndCountsForThread[(c * (dimension + 1)) +
                        j] /= centerSumsAndCountsForThread[(c * (dimension + 1)) + dimension]);
                double dist = getEuclideanDistance(centerSumsAndCountsForThread, centers, dimension, (c * (dimension + 1)), c*dimension);
                if (dist > errorThreshold) {
                    // Can't break as center sums need to be divided to
                    // form new centers
                    converged = false;
                }
                if (threadIdx == 0) {
                    IntStream.range(0, dimension).forEach(
                            j -> centers[(c * dimension) + j] = centerSumsAndCountsForThread[(c * (dimension + 1)) + j]);
                }
            }
        }
        if (threadIdx == 0) {
            loopTimer.stop();
            times[0] = loopTimer.elapsed(TimeUnit.MILLISECONDS);
            loopTimer.reset();
        }

        if (ParallelOps.worldProcsCount > 1 && threadIdx == 0) {
            ParallelOps.worldProcsComm.reduce(times, 1, MPI.LONG, MPI.SUM, 0);

            if (!converged) {
                print("    Stopping K-Means as max iteration count " +
                        maxIterations +
                        " has reached");
            }
            print("    Done in " + itrCount + " iterations and " +
                    times[0] * 1.0 / ParallelOps.worldProcsCount + " ms on average (across all MPI)");
        }
    }

    private void print(String msg) {
        if (ParallelOps.worldProcRank == 0 && threadIdx == 0) {
            System.out.println(msg);
        }
    }

    private static void findNearesetCenters(int dimension, int numCenters, double[] pointsForProc, double[] centers, double[] centerSumsAndCountsForThread, int[] clusterAssignments, Integer threadIdx) {
        int pointsForThread = ParallelOps.pointsForThread[threadIdx];
        int pointStartIdxForThread = ParallelOps.pointStartIdxForThread[threadIdx];

        for (int i = 0; i < pointsForThread; ++i) {
            int pointOffset = (pointStartIdxForThread + i) * dimension;
            int centerWithMinDist = findCenterWithMinDistance(pointsForProc, centers, dimension,
                    numCenters, pointOffset);

            int centerOffset = centerWithMinDist*(dimension+1);
            ++centerSumsAndCountsForThread[centerOffset+dimension];
            accumulate(pointsForProc, centerSumsAndCountsForThread, pointOffset, centerOffset, dimension);
            clusterAssignments[i] = centerWithMinDist;
        }
    }

    private static int findCenterWithMinDistance(double[] points, double[] centers, int dimension, int numCenters, int pointOffset) {
        double dMin = Double.MAX_VALUE;
        int dMinIdx = -1;
        for (int j = 0; j < numCenters; ++j) {
            double dist = getEuclideanDistance(points, centers, dimension, pointOffset, j*dimension);
            if (dist < dMin) {
                dMin = dist;
                dMinIdx = j;
            }
        }
        return dMinIdx;
    }

    private static void accumulate(double[] points, double[] centerSumsAndCounts, int pointOffset, int centerOffset, int dimension) {
        for (int i = 0; i < dimension; ++i) {
            centerSumsAndCounts[centerOffset+i] += points[pointOffset+i];
        }
    }

    private static double getEuclideanDistance(double[] point1, double[] point2, int dimension, int point1Offset, int point2Offset) {
        double d = 0.0;
        for (int i = 0; i < dimension; ++i) {
            d += Math.pow(point1[i+point1Offset] - point2[i+point2Offset], 2);
        }
        return Math.sqrt(d);
    }

    private static void resetCenterSumsAndCounts(double[] centerSumsAndCountsForThread) {
        IntStream.range(0, centerSumsAndCountsForThread.length).forEach(i -> centerSumsAndCountsForThread[i] = 0.0);
    }
}
