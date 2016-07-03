package org.saliya.ompi.kmeans;

import java.nio.DoubleBuffer;
import java.util.stream.IntStream;

public class ArrayCopyTester {
    public static void main(String[] args) {
        int size = 5;
        double [] a = new double[size];
        IntStream.range(0, size).forEach(i -> a[i] = i);
        DoubleBuffer buffer = DoubleBuffer.allocate(size);
        System.arraycopy(a, 0, buffer, 0, size);

    }
}
