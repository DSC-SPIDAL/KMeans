package org.saliya.ompi.kmeans;

import com.google.common.base.Optional;
import mpi.MPI;
import mpi.MPIException;
import org.apache.commons.cli.*;

import java.util.Arrays;

public class Utils
{
    public static final String PROGRAM_NAME = "K-Means";
    public static final String ERR_PROGRAM_ARGUMENTS_PARSING_FAILED
        = "Argument parsing failed!";
    public static final String ERR_INVALID_PROGRAM_ARGUMENTS
        = "Invalid program arguments!";
    public static final String ERR_EMPTY_FILE_NAME = "File name is null or empty!";

    /**
     * Parse command line arguments
     *
     * @param args Command line arguments
     * @param opts Command line options
     * @return An <code>Optional&lt;CommandLine&gt;</code> object
     */
    public static Optional<CommandLine> parseCommandLineArguments(
        String[] args, Options opts)
    {

        CommandLineParser optParser = new GnuParser();

        try
        {
            return Optional.fromNullable(optParser.parse(opts, args));
        }
        catch (ParseException e)
        {
            System.out.println(e);
        }
        return Optional.fromNullable(null);
    }

    public static void printPoints(double[][] points) throws MPIException
    {
        int[] nextRank = new int[1];
        for (int i = 0; i < ParallelOps.worldProcsCount; i++)
        {
            if (ParallelOps.worldProcRank == 0)
            {
                nextRank[0] = i;
            }
            ParallelOps.worldProcsComm.bcast(nextRank, 1, MPI.INT, 0);
            if (nextRank[0] == ParallelOps.worldProcRank)
            {
                System.out.println("Rank: " + ParallelOps.worldProcRank);
                for (int j = 0; j < points.length; j++)
                {
                    double[] point = points[j];
                    System.out.println(Arrays.toString(point));
                }
            }
        }
    }
}
