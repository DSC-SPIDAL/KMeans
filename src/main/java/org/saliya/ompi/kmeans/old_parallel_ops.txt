package org.saliya.ompi.kmeans;

import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

import java.util.Arrays;
import java.util.stream.IntStream;

public class ParallelOptions
{

    public static int rank = 0;
    public static int size = 1;
    public static Intracomm comm;

    public static int numThreads = 1;

    public static int myNumVec;
    public static int globalVecStartIdx;
    public static int [] myNumVecForThread;
    public static int [] vecStartIdxForThread;


    public static void setupParallelism(String[] args, int numVec, int numThreads)
        throws MPIException
    {
        MPI.Init(args);
        comm = MPI.COMM_WORLD;
        rank = comm.getRank();
        size = comm.getSize();
        ParallelOptions.numThreads = numThreads;
        myNumVecForThread = new int[numThreads];
        vecStartIdxForThread = new int[numThreads];
        decomposeDomain(numVec);
    }

    public static void endParallelism() throws MPIException
    {
        MPI.Finalize();
    }

    private static void decomposeDomain(int numVec)
    {
        int div = numVec / size;
        int rem = numVec % size;
        myNumVec = rank < rem ? div + 1 : div;
        globalVecStartIdx = rank * div + (rank < rem ? rank : rem);
        decomposeDomainAmongThreads();
    }

    private static void decomposeDomainAmongThreads()
    {
        int div = myNumVec / numThreads;
        int rem = myNumVec % numThreads;
        IntStream.range(0, numThreads).forEach(
            i ->
            {
                myNumVecForThread[i] = i < rem ? div+1 : div;
                vecStartIdxForThread[i] = (i * div + (i < rem ? i : rem));
            });
    }

    public static int[] getLengthsArray(int numVec)
    {
        int div = numVec / size;
        int rem = numVec % size;
        int[] lengths = new int[size];
        IntStream.range(0, size)
                 .forEach(i -> lengths[i] = i >= rem ? div : div + 1);
        return lengths;
    }
}
