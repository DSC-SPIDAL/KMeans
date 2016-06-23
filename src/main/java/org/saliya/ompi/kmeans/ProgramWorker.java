package org.saliya.ompi.kmeans;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.primitives.Doubles;
import mpi.MPI;
import mpi.MPIException;
import org.saliya.ompi.kmeans.threads.ThreadCommunicator;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
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
    private String outputFile;
    private final String pointsFile;
    private final boolean isBigEndian;

    public ProgramWorker(Integer threadIdx, ThreadCommunicator threadComm, int numPoints, int dimension, int numCenters, int maxIterations, double errorThreshold, int numThreads, double[] points, double[] centers, String outputFile, String pointsFile, boolean isBigEndian) {
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
        this.outputFile = outputFile;
        this.pointsFile = pointsFile;
        this.isBigEndian = isBigEndian;
    }

    public void run() throws MPIException, IOException {

        final double[] centerSumsAndCountsForThread = new double[numCenters*(dimension+1)];
        final int[] clusterAssignments = new int[ParallelOps.pointsForThread[threadIdx]];

        int itrCount = 0;
        boolean converged = false;
        print("  Computing K-Means .. ");
        Stopwatch loopTimer = threadIdx == 0 ? Stopwatch.createStarted(): null;
        Stopwatch timer = Stopwatch.createUnstarted();
        long[] times = new long[]{0,0,0};
        while (!converged && itrCount < maxIterations) {
            ++itrCount;
            resetCenterSumsAndCounts(centerSumsAndCountsForThread);

            timer.start();
            findNearesetCenters(dimension, numCenters, pointsForProc, centers, centerSumsAndCountsForThread,
                    clusterAssignments, threadIdx);
            timer.stop();
            times[1] += timer.elapsed(TimeUnit.MILLISECONDS);
            timer.reset();

            if (numThreads > 1) {
                // Sum over threads
                // Place results to arrays of thread 0
                threadComm.sumDoubleArrayOverThreads(threadIdx, centerSumsAndCountsForThread);
            }

            timer.start();
            if (ParallelOps.worldProcsCount > 1 && threadIdx == 0) {
                ParallelOps.allReduceSum(centerSumsAndCountsForThread, 0, numCenters*(dimension+1));
            }
            timer.stop();
            times[2] += timer.elapsed(TimeUnit.MILLISECONDS);
            timer.reset();

            if (numThreads > 1){
                threadComm.broadcastDoubleArrayOverThreads(threadIdx, centerSumsAndCountsForThread, 0);
            }

            converged = true;
            if (threadIdx == 0) {
                for (int i = 0; i < numCenters; ++i) {
                    final int c = i;
                    IntStream.range(0, dimension).forEach(j -> centerSumsAndCountsForThread[(c * (dimension + 1)) + j] /= centerSumsAndCountsForThread[(c * (dimension + 1)) + dimension]);
                    double dist = getEuclideanDistance(centerSumsAndCountsForThread, centers, dimension,
                            (c * (dimension + 1)), c * dimension);
                    if (dist > errorThreshold) {
                        // Can't break as center sums need to be divided to
                        // form new centers
                        converged = false;
                    }
                    IntStream.range(0, dimension).forEach(
                            j -> centers[(c * dimension) + j] = centerSumsAndCountsForThread[(c * (dimension + 1)) + j]);
                }
            }
            if (numThreads > 1) {
                converged = threadComm.bcastBooleanOverThreads(threadIdx, converged, 0);
            }
        }

        if (threadIdx == 0) {
            loopTimer.stop();
            times[0] = loopTimer.elapsed(TimeUnit.MILLISECONDS);
            loopTimer.reset();
        }

        if (ParallelOps.worldProcsCount > 1 && threadIdx == 0) {
            ParallelOps.worldProcsComm.reduce(times, 3, MPI.LONG, MPI.SUM, 0);
        }

        if (threadIdx == 0){
            if (!converged) {
                print("    Stopping K-Means as max iteration count " +
                        maxIterations +
                        " has reached");
            }
            print("    Done in " + itrCount + " iterations and " +
                    times[0] * 1.0 / ParallelOps.worldProcsCount + " ms on average (across all MPI)");
            print("    Compute time (thread 0 avg across MPI) " + times[1] * 1.0 / ParallelOps.worldProcsCount + " ms");
            print("    Comm time (thread 0 avg across MPI) " + times[2] * 1.0 / ParallelOps.worldProcsCount + " ms");
        }

        if (!Strings.isNullOrEmpty(outputFile)) {
            int[] clusterAssignmentsForProc = threadComm.collect(threadIdx, clusterAssignments);
            if (threadIdx == 0) {
                IntBuffer intBuffer = MPI.newIntBuffer(numPoints);
                if (ParallelOps.worldProcsCount > 1) {
                    // Gather cluster assignments
                    print("  Gathering cluster assignments ...");
                    int[] lengths = ParallelOps.getLengthsArray(numPoints);
                    int[] displas = new int[ParallelOps.worldProcsCount];
                    displas[0] = 0;
                    System.arraycopy(lengths, 0, displas, 1, ParallelOps.worldProcsCount - 1);
                    Arrays.parallelPrefix(displas, (p, q) -> p + q);

                    intBuffer.position(ParallelOps.pointStartIdxForProc);
                    intBuffer.put(clusterAssignmentsForProc);
                    ParallelOps.worldProcsComm.allGatherv(intBuffer, lengths, displas, MPI.INT);
                    print("    Done");
                }

                if (ParallelOps.worldProcRank == 0) {
                    print("  Writing output file ...");
                    try (PrintWriter writer = new PrintWriter(
                            Files.newBufferedWriter(Paths.get(outputFile), Charset.defaultCharset(), StandardOpenOption.CREATE, StandardOpenOption.WRITE), true)) {
                        PointReader reader = PointReader.readRowRange(pointsFile, 0, numPoints, dimension, isBigEndian);
                        double[] point = new double[dimension];
                        for (int i = 0; i < numPoints; ++i) {
                            reader.getPoint(i, point, dimension, 0);
                            writer.println(i + "\t" + Doubles.join("\t", point) + "\t" +
                                    ((ParallelOps.worldProcsCount > 1) ? intBuffer.get(i) : clusterAssignmentsForProc[i]));
                        }
                    }
                    print("    Done");
                }
            }
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
