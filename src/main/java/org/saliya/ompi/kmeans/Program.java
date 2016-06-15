package org.saliya.ompi.kmeans;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.primitives.Doubles;
import mpi.MPI;
import mpi.MPIException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static edu.rice.hj.Module0.launchHabaneroApp;
import static edu.rice.hj.Module1.forallChunked;

public class Program {
    private static DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    private static Options programOptions = new Options();

    static {
        programOptions.addOption("n", true, "Number of points");
        programOptions.addOption("d", true, "Dimensionality");
        programOptions.addOption("k", true, "Number of centers");
        programOptions.addOption("t", true, "Error threshold");
        programOptions.addOption("m", true, "Max iteration count");
        programOptions.addOption("b", true, "Is big-endian?");
        programOptions.addOption("T", true, "Number of threads");
        programOptions.addOption("c", true, "Initial center file");
        programOptions.addOption("p", true, "Points file");
        programOptions.addOption("o", true, "Cluster assignment output file");
        programOptions.addOption("mmpn", true, "mmaps per node");
        programOptions.addOption("mmdir", true, "mmaps dir");
    }

    public static void main(String[] args) {
        Optional<CommandLine> parserResult = Utils.parseCommandLineArguments(args, programOptions);
        if (!parserResult.isPresent()) {
            System.out.println(Utils.ERR_PROGRAM_ARGUMENTS_PARSING_FAILED);
            new HelpFormatter().printHelp(Utils.PROGRAM_NAME, programOptions);
            return;
        }

        CommandLine cmd = parserResult.get();
        if (!(cmd.hasOption("n") && cmd.hasOption("d") && cmd.hasOption("k") &&
                cmd.hasOption("t") && cmd.hasOption("m") && cmd.hasOption("b") &&
                cmd.hasOption("c") && cmd.hasOption("p") && cmd.hasOption("T"))) {
            System.out.println(Utils.ERR_INVALID_PROGRAM_ARGUMENTS);
            new HelpFormatter().printHelp(Utils.PROGRAM_NAME, programOptions);
            return;
        }

        int numPoints = Integer.parseInt(cmd.getOptionValue("n"));
        int numDimensions = Integer.parseInt(cmd.getOptionValue("d"));
        int numCenters = Integer.parseInt(cmd.getOptionValue("k"));
        int maxIterations = Integer.parseInt(cmd.getOptionValue("m"));
        double errorThreshold = Double.parseDouble(cmd.getOptionValue("t"));
        int numThreads = Integer.parseInt(cmd.getOptionValue("T"));
        boolean isBigEndian = Boolean.parseBoolean(cmd.getOptionValue("b"));
        String outputFile = cmd.hasOption("o") ? cmd.getOptionValue("o") : "";
        String centersFile = cmd.hasOption("c") ? cmd.getOptionValue("c") : "";
        String pointsFile = cmd.hasOption("p") ? cmd.getOptionValue("p") : "";

        try {
            ParallelOptions.setupParallelism(args, numPoints, numThreads);

            Stopwatch mainTimer = Stopwatch.createStarted();

            print("=== Program Started on " + dateFormat.format(new Date()) + " ===");
            print("  Reading points ... ");

            Stopwatch timer = Stopwatch.createStarted();
            final double[][] points = readPoints(pointsFile, numDimensions, ParallelOptions.globalVecStartIdx,
                    ParallelOptions.myNumVec, isBigEndian);

            timer.stop();
            print("    Done in " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms");
            timer.reset();

            print("  Reading centers ...");
            timer.start();
            double[][] centers = readCenters(centersFile, numCenters, numDimensions, isBigEndian);
            timer.stop();
            print("    Done in " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms");
            timer.reset();

            DoubleBuffer doubleBuffer = null;
            IntBuffer intBuffer = null;
            IntBuffer intBuffer2 = null;
            if (ParallelOptions.size > 1) {
                print("  Allocating buffers");
                timer.start();
                doubleBuffer = MPI.newDoubleBuffer(numCenters * numDimensions);
                intBuffer = MPI.newIntBuffer(numCenters);
                intBuffer2 = MPI.newIntBuffer(numPoints);
                timer.stop();
                // This would be similar across
                // all processes, so no need to do average
                print("  Done in " + timer.elapsed(TimeUnit.MILLISECONDS));
                timer.reset();
            }

            final double[][][] centerSumsForThread = new double[numThreads][numCenters][numDimensions];
            final int[][] pointsPerCenterForThread = new int[numThreads][numCenters];
            final int[] clusterAssignments = new int[ParallelOptions.myNumVec];

            resetPointsPerCenter(pointsPerCenterForThread);

            int itrCount = 0;
            boolean converged = false;
            print("  Computing K-Means .. ");
            Stopwatch loopTimer = Stopwatch.createStarted();
            Stopwatch commTimerWithCopy = Stopwatch.createUnstarted();
            Stopwatch commTimer = Stopwatch.createUnstarted();
            long[] times = new long[]{0, 0, 0};
            while (!converged && itrCount < maxIterations) {
                ++itrCount;
                resetCenterSums(centerSumsForThread, numDimensions);
                resetPointsPerCenter(pointsPerCenterForThread);

                final double[][] immutableCenters = centers;
                launchHabaneroApp(() -> {
                    forallChunked(0, numThreads - 1, (threadIndex) -> {
                        int threadLocalMyNumVec = ParallelOptions.myNumVecForThread[threadIndex];
                        int threadLocalVecStartIdx = ParallelOptions.vecStartIdxForThread[threadIndex];

                        for (int i = 0; i < threadLocalMyNumVec; ++i) {
                            double[] point = points[i + threadLocalVecStartIdx];
                            int dMinIdx = findCenterWithMinDistance(point, immutableCenters);
                            ++pointsPerCenterForThread[threadIndex][dMinIdx];
                            accumulate(point, centerSumsForThread[threadIndex], dMinIdx);
                            clusterAssignments[i + threadLocalVecStartIdx] = dMinIdx;
                        }
                    });
                });

                // Sum over threads
                // Place results to arrays of thread 0
                for (int i = 1; i < numThreads; ++i) {
                    for (int c = 0; c < numCenters; ++c) {
                        pointsPerCenterForThread[0][c] += pointsPerCenterForThread[i][c];
                        for (int d = 0; d < numDimensions; ++d) {
                            centerSumsForThread[0][c][d] += centerSumsForThread[i][c][d];
                        }
                    }
                }

                if (ParallelOptions.size > 1) {
                    commTimerWithCopy.start();
                    copyToBuffer(centerSumsForThread[0], doubleBuffer);
                    copyToBuffer(pointsPerCenterForThread[0], intBuffer);
                    commTimer.start();
                    ParallelOptions.comm.allReduce(doubleBuffer, numDimensions * numCenters, MPI.DOUBLE, MPI.SUM);
                    commTimer.stop();
                    copyFromBuffer(doubleBuffer, centerSumsForThread[0]);
                    commTimer.start();
                    ParallelOptions.comm.allReduce(intBuffer, numCenters, MPI.INT, MPI.SUM);
                    commTimer.stop();
                    copyFromBuffer(intBuffer, pointsPerCenterForThread[0]);
                    commTimerWithCopy.stop();
                    times[0] += commTimerWithCopy.elapsed(TimeUnit.MILLISECONDS);
                    times[1] += commTimer.elapsed(TimeUnit.MILLISECONDS);
                    commTimerWithCopy.reset();
                    commTimer.reset();
                }

                converged = true;
                for (int i = 0; i < numCenters; ++i) {
                    double[] centerSum = centerSumsForThread[0][i];
                    int tmpI = i;
                    IntStream.range(0, numDimensions).forEach(j -> centerSum[j] /= pointsPerCenterForThread[0][tmpI]);
                    double dist = getEuclideanDistance(centerSum, centers[i]);
                    if (dist > errorThreshold) {
                        // Can't break as center sums need to be divided to
                        // form new centers
                        converged = false;
                    }
                }
                // Swap original center with centerSumsForThread[0],
                // which now contains the updated centers. The swapping
                // will NOT introduce even though centerSumsForThread[0] is
                // set to old values as they will be reset before next
                // iteration.
                double[][] tmp = centers;
                centers = centerSumsForThread[0];
                centerSumsForThread[0] = tmp;
            }
            loopTimer.stop();
            times[2] = loopTimer.elapsed(TimeUnit.MILLISECONDS);
            loopTimer.reset();

            if (ParallelOptions.size > 1) {
                ParallelOptions.comm.reduce(times, 3, MPI.LONG, MPI.SUM, 0);
            }
            if (!converged) {
                print("    Stopping K-Means as max iteration count " +
                        maxIterations +
                        " has reached");
            }
            print("    Done in " + itrCount + " iterations and " +
                    times[2] * 1.0 / ParallelOptions.size + " ms on average (across all MPI)");
            if (ParallelOptions.size > 1) {
                print("    Avg. comm time " +
                        times[1] * 1.0 / ParallelOptions.size +
                        " ms (across all MPI)");
                print("    Avg. comm time w/ copy " +
                        times[0] * 1.0 / ParallelOptions.size + " ms (across all MPI)");
            }

            if (!Strings.isNullOrEmpty(outputFile)) {
                if (ParallelOptions.size > 1) {
                    // Gather cluster assignments
                    print("  Gathering cluster assignments ...");
                    timer.start();
                    int[] lengths = ParallelOptions.getLengthsArray(numPoints);
                    int[] displas = new int[ParallelOptions.size];
                    displas[0] = 0;
                    System.arraycopy(lengths, 0, displas, 1, ParallelOptions.size - 1);
                    Arrays.parallelPrefix(displas, (p, q) -> p + q);
                    intBuffer2.position(ParallelOptions.globalVecStartIdx);
                    intBuffer2.put(clusterAssignments);
                    ParallelOptions.comm.allGatherv(intBuffer2, lengths, displas, MPI.INT);
                    timer.stop();
                    long[] time = new long[]{timer.elapsed(TimeUnit.MILLISECONDS)};
                    timer.reset();
                    ParallelOptions.comm.reduce(time, 1, MPI.LONG, MPI.SUM, 0);
                    print("    Done in " + time[0] * 1.0 / ParallelOptions.size +
                            " ms on average");
                }

                if (ParallelOptions.rank == 0) {
                    print("  Writing output file ...");
                    timer.start();
                    try (PrintWriter writer = new PrintWriter(
                            Files.newBufferedWriter(Paths.get(outputFile), Charset.defaultCharset(),
                                    StandardOpenOption.CREATE, StandardOpenOption.WRITE), true)) {
                        PointReader reader = PointReader.readRowRange(pointsFile, 0, numPoints, numDimensions,
                                isBigEndian);
                        double[] point = new double[numDimensions];
                        for (int i = 0; i < numPoints; ++i) {
                            reader.getPoint(i, point);
                            writer.println(i + "\t" + Doubles.join("\t", point) + "\t" +
                                    ((ParallelOptions.size > 1) ? intBuffer2.get(i) : clusterAssignments[i]));
                        }
                    }
                    timer.stop();
                    print("    Done in " + timer.elapsed(TimeUnit.MILLISECONDS) +
                            "ms");
                    timer.reset();
                }
            }
            mainTimer.stop();
            print("=== Program terminated successfully on " +
                    dateFormat.format(new Date()) + " took " +
                    (mainTimer.elapsed(TimeUnit.MILLISECONDS)) + " ms ===");

            ParallelOptions.endParallelism();
        } catch (MPIException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void resetPointsPerCenter(int[][] pointsPerCenterForThread) {
        for (int[] tmp : pointsPerCenterForThread) {
            for (int j = 0; j < tmp.length; ++j) {
                tmp[j] = 0;
            }
        }
    }


    private static void copyFromBuffer(IntBuffer buffer, int[] pointsPerCenter) {
        buffer.position(0);
        buffer.get(pointsPerCenter);
    }

    private static void copyFromBuffer(DoubleBuffer buffer, double[][] centerSums) {
        buffer.position(0);
        for (double[] centerSum : centerSums) {
            buffer.get(centerSum);
        }
    }

    private static void copyToBuffer(int[] pointsPerCenter, IntBuffer buffer) {
        buffer.position(0);
        buffer.put(pointsPerCenter);
    }

    private static void copyToBuffer(double[][] centerSums, DoubleBuffer buffer) {
        buffer.position(0);
        for (double[] centerSum : centerSums) {
            buffer.put(centerSum);
        }
    }

    private static void print(String msg) {
        if (ParallelOptions.rank == 0) {
            System.out.println(msg);
        }
    }

    private static int findCenterWithMinDistance(double[] point, double[][] centers) {
        int k = centers.length;
        double dMin = Double.MAX_VALUE;
        int dMinIdx = -1;
        for (int j = 0; j < k; ++j) {
            double dist = getEuclideanDistance(point, centers[j]);
            if (dist < dMin) {
                dMin = dist;
                dMinIdx = j;
            }
        }
        return dMinIdx;
    }

    private static void accumulate(double[] point, double[][] centerSums, int idx) {
        double[] center = centerSums[idx];
        for (int i = 0; i < center.length; ++i) {
            center[i] += point[i];
        }
    }

    private static double getEuclideanDistance(double[] point1, double[] point2) {
        int length = point1.length;
        double d = 0.0;
        for (int i = 0; i < length; ++i) {
            d += Math.pow(point1[i] - point2[i], 2);
        }
        return Math.sqrt(d);
    }

    private static void resetCenterSums(double[][][] centerSumsForThread, int d) {
        Arrays.stream(centerSumsForThread).forEach(
                threadLocalCenterSums -> Arrays.stream(threadLocalCenterSums).forEach(
                        centerSum -> IntStream.range(0, d).forEach(i -> centerSum[i] = 0.0)));
    }

    private static double[][] readPoints(String pointsFile, int d, int globalVecStartIdx, int myNumVec, boolean isBigEndian) throws IOException {
        double[][] points = new double[myNumVec][d];
        PointReader reader = PointReader.readRowRange(pointsFile, globalVecStartIdx, myNumVec, d, isBigEndian);
        for (int i = 0; i < myNumVec; i++) {
            reader.getPoint(i + globalVecStartIdx, points[i]);
        }
        return points;
    }

    private static double[][] readCenters(String centersFile, int k, int d, boolean isBigEndian) throws IOException {
        double[][] centers = new double[k][d];
        PointReader reader = PointReader.readRowRange(centersFile, 0, k, d, isBigEndian);
        for (int i = 0; i < k; i++) {
            reader.getPoint(i, centers[i]);
        }
        return centers;
    }


}
