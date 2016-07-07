package org.saliya.ompi.kmeans;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.primitives.Doubles;
import mpi.MPI;
import mpi.MPIException;
import org.saliya.ompi.kmeans.threads.ThreadCommunicator;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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

        final int lengthCenterSumsAndCounts = numCenters*(dimension+1);
//        final double[] centerSumsAndCountsForThread = new double[lengthCenterSumsAndCounts];
        final DoubleBuffer centerSumsAndCountsForThread = DoubleBuffer.allocate(lengthCenterSumsAndCounts);
        final int[] clusterAssignments = new int[ParallelOps.pointsForThread[threadIdx]];

        int itrCount = 0;
        boolean converged = false;
        print("  Computing K-Means .. ");
        double loopTimer = threadIdx == 0 ? MPI.wtime(): 0.0;
        double[] times = new double[]{0.0,0.0,0.0,0.0};

        ArrayList<Double> timings = new ArrayList<>();
        double computeTime;

        while (!converged && itrCount < maxIterations) {
            // adding a barrier to begin with to show the variation with compute timings
            ParallelOps.worldProcsComm.barrier();
            ++itrCount;
            resetCenterSumsAndCounts(centerSumsAndCountsForThread, lengthCenterSumsAndCounts);

            timings.add(new Date().getTime()*1.0);
            computeTime = MPI.wtime();
            findNearesetCenters(dimension, numCenters, pointsForProc, centers, centerSumsAndCountsForThread,
                    clusterAssignments, threadIdx);
            double t = (MPI.wtime() - computeTime)*1000;
            times[1] += t;
            timings.add(t);

            if (numThreads > 1) {
                // Sum over threads
                // Place results to arrays of thread 0
                threadComm.sumDoubleArrayOverThreads(threadIdx, centerSumsAndCountsForThread, lengthCenterSumsAndCounts);
            }

            if (ParallelOps.worldProcsCount > 1 && threadIdx == 0) {
                timings.add(new Date().getTime()*1.0);
                double x = MPI.wtime();
                t = MPI.wtime();
                // TODO - testing with a barrier to see if comm times reduce
                ParallelOps.worldProcsComm.barrier();
                double d = (MPI.wtime() - t)*1000;
                times[3] += d;
                timings.add(d);

                t = MPI.wtime();
                // Note. reverting to default MPI call with double buffer
//                ParallelOps.allReduceSum(centerSumsAndCountsForThread, 0, numCenters*(dimension+1));
                ParallelOps.worldProcsComm.allReduce(centerSumsAndCountsForThread, lengthCenterSumsAndCounts, MPI.DOUBLE, MPI.SUM);
                d = (MPI.wtime() - t)*1000;
                times[2] += d;
                timings.add(d);
                timings.add((MPI.wtime() - x)*1000);
                timings.add(new Date().getTime()*1.0);
            }

            if (numThreads > 1){
                // Note. method call with double buffer
                threadComm.broadcastDoubleArrayOverThreads(threadIdx, centerSumsAndCountsForThread, lengthCenterSumsAndCounts, 0);
            }

            converged = true;
            if (threadIdx == 0) {
                for (int i = 0; i < numCenters; ++i) {
                    final int c = i;
                    // Note. method call with double buffer
//                    IntStream.range(0, dimension).forEach(j -> centerSumsAndCountsForThread[(c * (dimension + 1)) + j] /= centerSumsAndCountsForThread[(c * (dimension + 1)) + dimension]);

                    double tmp;
                    int idx = (c * (dimension + 1));
                    for (int j = 0; j < dimension; ++j){
                        tmp = centerSumsAndCountsForThread.get(idx+j);
                        centerSumsAndCountsForThread.put(
                                idx+j, tmp / centerSumsAndCountsForThread.get(idx + dimension));
                    }

                    double dist = getEuclideanDistance(centerSumsAndCountsForThread, centers, dimension,
                            (c * (dimension + 1)), c * dimension);
                    if (dist > errorThreshold) {
                        // Can't break as center sums need to be divided to
                        // form new centers
                        converged = false;
                    }
                    /*IntStream.range(0, dimension).forEach(
                            j -> centers[(c * dimension) + j] = centerSumsAndCountsForThread[(c * (dimension + 1)) + j]);*/
                    IntStream.range(0, dimension).forEach(
                            j -> centers[(c * dimension) + j] = centerSumsAndCountsForThread.get((c * (dimension + 1)) + j));

                }
            }
            if (numThreads > 1) {
                converged = threadComm.bcastBooleanOverThreads(threadIdx, converged, 0);
            }
        }

        if (threadIdx == 0) {
            times[0] = (MPI.wtime() - loopTimer)*1000;
        }

        if (ParallelOps.worldProcsCount > 1 && threadIdx == 0) {
            ParallelOps.worldProcsComm.reduce(times, 4, MPI.DOUBLE, MPI.SUM, 0);

            int size = timings.size();
            DoubleBuffer sendBuff = MPI.newDoubleBuffer(size);
            DoubleBuffer recvBuff = MPI.newDoubleBuffer(size *ParallelOps.worldProcsCount);
            for (int i = 0; i < size; i++) {
                sendBuff.put(i,timings.get(i));
            }
            ParallelOps.worldProcsComm.allGather(sendBuff, size, MPI.DOUBLE, recvBuff, size, MPI.DOUBLE);

            String name = numThreads + "x" + ParallelOps.worldProcsPerNode  + "x" + ParallelOps.nodeCount + "_timings.txt";
            try(BufferedWriter bw = Files.newBufferedWriter(Paths.get(name))){
                PrintWriter pw = new PrintWriter(bw, true);
                int fields = 7;
                for (int i = 0; i < itrCount; ++i){
                    for (int v = 0; v < fields; ++v){
                        pw.print(i +",");
                        for (int p = 0; p < ParallelOps.worldProcsCount; ++p){
                            int offset = p*itrCount*fields+i*fields+v;
                            pw.print(recvBuff.get(offset) +",");
                        }
                        pw.println();
                    }
                }
            }

            name = numThreads + "x" + ParallelOps.worldProcsPerNode  + "x" + ParallelOps.nodeCount + "_centers.txt";
            try(BufferedWriter bw = Files.newBufferedWriter(Paths.get(name))) {
                PrintWriter pw = new PrintWriter(bw, true);
                for (int i = 0; i < numCenters; ++i){
                    pw.print(i + " ");
                    for (int d = 0; d < dimension+1; ++d){
                        pw.print(centerSumsAndCountsForThread.get(i*(dimension+1)+d) + " ");
                    }
                    pw.println();
                }
            }
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
            print("    Barrier time (thread 0 avg across MPI) " + times[3] * 1.0 / ParallelOps.worldProcsCount + " ms");
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

    private static void findNearesetCenters(int dimension, int numCenters, double[] pointsForProc, double[] centers, DoubleBuffer centerSumsAndCountsForThread, int[] clusterAssignments, Integer threadIdx) {
        int pointsForThread = ParallelOps.pointsForThread[threadIdx];
        int pointStartIdxForThread = ParallelOps.pointStartIdxForThread[threadIdx];

        double tmp;
        for (int i = 0; i < pointsForThread; ++i) {
            int pointOffset = (pointStartIdxForThread + i) * dimension;
            int centerWithMinDist = findCenterWithMinDistance(pointsForProc, centers, dimension,
                    numCenters, pointOffset);

            int centerOffset = centerWithMinDist*(dimension+1);
            tmp = centerSumsAndCountsForThread.get(centerOffset+dimension);
            centerSumsAndCountsForThread.put(centerOffset+dimension, tmp+1);
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

    private static void accumulate(double[] points, DoubleBuffer centerSumsAndCounts, int pointOffset, int centerOffset, int dimension) {
        double tmp;
        for (int i = 0; i < dimension; ++i) {
            tmp = centerSumsAndCounts.get(centerOffset+i);
            centerSumsAndCounts.put(centerOffset+i,  tmp + points[pointOffset+i]);
        }
    }



    private static double getEuclideanDistance(double[] point1, double[] point2, int dimension, int point1Offset, int point2Offset) {
        double d = 0.0;
        for (int i = 0; i < dimension; ++i) {
            d += Math.pow(point1[i+point1Offset] - point2[i+point2Offset], 2);
        }
        return Math.sqrt(d);
    }

    private static double getEuclideanDistance(DoubleBuffer point1, double[] point2, int dimension, int point1Offset, int point2Offset) {
        double d = 0.0;
        for (int i = 0; i < dimension; ++i) {
            d += Math.pow(point1.get(i+point1Offset) - point2[i+point2Offset], 2);
        }
        return Math.sqrt(d);
    }



    private static void resetCenterSumsAndCounts(double[] centerSumsAndCountsForThread) {
        IntStream.range(0, centerSumsAndCountsForThread.length).forEach(i -> centerSumsAndCountsForThread[i] = 0.0);
    }

    private static void resetCenterSumsAndCounts(DoubleBuffer centerSumsAndCountsForThread, int lengthCenterSumsAndCounts) {
        IntStream.range(0, lengthCenterSumsAndCounts).forEach(i -> centerSumsAndCountsForThread.put(i,0.0));
    }
}
