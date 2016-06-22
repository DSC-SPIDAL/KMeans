package org.saliya.ompi.kmeans;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import mpi.MPI;
import mpi.MPIException;
import net.openhft.affinity.Affinity;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.saliya.ompi.kmeans.threads.ThreadCommunicator;

import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.BitSet;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static edu.rice.hj.Module0.launchHabaneroApp;
import static edu.rice.hj.Module1.forallChunked;

public class ProgramLRT {
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
        programOptions.addOption("bind", true, "Bind threads [true/false]");
    }

    public static void main(String[] args) throws IOException, MPIException {
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
        int dimension = Integer.parseInt(cmd.getOptionValue("d"));
        int numCenters = Integer.parseInt(cmd.getOptionValue("k"));
        int maxIterations = Integer.parseInt(cmd.getOptionValue("m"));
        double errorThreshold = Double.parseDouble(cmd.getOptionValue("t"));
        int numThreads = Integer.parseInt(cmd.getOptionValue("T"));
        boolean isBigEndian = Boolean.parseBoolean(cmd.getOptionValue("b"));
        String outputFile = cmd.hasOption("o") ? cmd.getOptionValue("o") : "";
        String centersFile = cmd.hasOption("c") ? cmd.getOptionValue("c") : "";
        String pointsFile = cmd.hasOption("p") ? cmd.getOptionValue("p") : "";
        int mmapsPerNode = cmd.hasOption("mmpn") ? Integer.parseInt(cmd.getOptionValue("mmpn")) : 1;
        String mmapDir = cmd.hasOption("mmdir") ? cmd.getOptionValue("mmdir") : "/dev/shm";
        boolean bind = !cmd.hasOption("bind") || Boolean.parseBoolean(cmd.getOptionValue("bind"));
        System.out.println("***bind=" + bind);

        ParallelOps.setupParallelism(args, mmapsPerNode, mmapDir);
        ParallelOps.setParallelDecomposition(numPoints, dimension, numCenters, numThreads);

        Stopwatch mainTimer = Stopwatch.createStarted();

        print("=== Program Started on " + dateFormat.format(new Date()) + " ===");
        print("  Reading points ... ");

        Stopwatch timer = Stopwatch.createStarted();
        final double[] points = readPoints(pointsFile, dimension, ParallelOps.pointStartIdxForProc,
                ParallelOps.pointsForProc, isBigEndian);

        timer.stop();
        print("    Done in " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms");
        timer.reset();

        print("  Reading centers ...");
        timer.start();
        final double[] centers = readCenters(centersFile, numCenters, dimension, isBigEndian);
        timer.stop();
        print("    Done in " + timer.elapsed(TimeUnit.MILLISECONDS) + " ms");
        timer.reset();

        ThreadCommunicator tcomm = new ThreadCommunicator(numThreads, numCenters, dimension);
        if (ParallelOps.numThreads > 1) {
            launchHabaneroApp(() -> forallChunked(0, numThreads - 1, (threadIdx) -> {
                if (bind) {
                    BitSet bitSet = ThreadBitAssigner.getBitSet(ParallelOps.worldProcRank, threadIdx, numThreads, (ParallelOps.nodeCount));
                    Affinity.setAffinity(bitSet);
                }
                try {
                    final ProgramWorker worker = new ProgramWorker(threadIdx, tcomm, numPoints, dimension, numCenters, maxIterations, errorThreshold, numThreads, points, centers, outputFile, pointsFile, isBigEndian);
                    worker.run();
                } catch (MPIException | IOException e) {
                    e.printStackTrace();
                }
            }));
        } else {
            if (bind) {
                BitSet bitSet = ThreadBitAssigner.getBitSet(ParallelOps.worldProcRank, 0, numThreads, (ParallelOps.nodeCount));
                Affinity.setAffinity(bitSet);
                System.out.println("Rank: " + ParallelOps.worldProcRank + " binding to " + bitSet);
            }
            final ProgramWorker worker = new ProgramWorker(0, tcomm, numPoints, dimension, numCenters, maxIterations, errorThreshold, numThreads, points, centers, outputFile, pointsFile, isBigEndian);
            worker.run();
        }

        mainTimer.stop();
        print("=== Program terminated successfully on " +
                dateFormat.format(new Date()) + " took " +
                (mainTimer.elapsed(TimeUnit.MILLISECONDS)) + " ms ===");

        ParallelOps.endParallelism();

    }

    private static void print(String msg) {
        if (ParallelOps.worldProcRank == 0) {
            System.out.println(msg);
        }
    }

    private static double[] readPoints(String pointsFile, int dimension, int pointStartIdxForProc, int pointCountForProc, boolean isBigEndian) throws IOException {
        double[] points = new double[pointCountForProc*dimension];
        PointReader reader = PointReader.readRowRange(pointsFile, pointStartIdxForProc, pointCountForProc, dimension, isBigEndian);
        for (int i = 0; i < pointCountForProc; i++) {
            reader.getPoint(i + pointStartIdxForProc, points, dimension, i*dimension);
        }
        return points;
    }

    private static double[] readCenters(String centersFile, int k, int dimension, boolean isBigEndian) throws IOException {
        double[] centers = new double[k*dimension];
        PointReader reader = PointReader.readRowRange(centersFile, 0, k, dimension, isBigEndian);
        for (int i = 0; i < k; i++) {
            reader.getPoint(i, centers, dimension, i*dimension);
        }
        return centers;
    }
}
