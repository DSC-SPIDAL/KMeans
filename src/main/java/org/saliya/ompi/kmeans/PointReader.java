package org.saliya.ompi.kmeans;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class PointReader
{
    public static PointReader readRowRange(
        String fname, int startRow, int numRows, int dimension,
        boolean isBigEndian) throws IOException
    {
        try (FileChannel fc = (FileChannel) Files
            .newByteChannel(Paths.get(fname), StandardOpenOption.READ))
        {
            long pos = ((long) startRow) * dimension *
                       8; // 8 for double values, which are 8 bytes long
            long size = ((long) numRows) * dimension *
                        8; // 8 for double values, which are 8 bytes long

            int m = Integer.MAX_VALUE -
                    7; // m = (2^31-8) = 8n for some n where n denotes the
            // number of doubles
            int mapCount = (int) Math.ceil((double) size / m);
            DoubleBuffer[] maps = new DoubleBuffer[mapCount];
            for (int i = 0; i < mapCount; ++i)
            {
                maps[i] = fc.map(
                    FileChannel.MapMode.READ_ONLY, pos + (((long) i) * m),
                    i < mapCount - 1 ? m : size % m).order(
                    isBigEndian ? ByteOrder.BIG_ENDIAN
                                : ByteOrder.LITTLE_ENDIAN).asDoubleBuffer();

            }

            return new PointReader()
            {
                @Override
                public void getPoint(int globalRow, double[] points, int dimension, int offset)
                {
                    long pos = ((globalRow - startRow) *
                                ((long) dimension)); // double position
                    // relative to start row
                    int mapIdx = (int) (pos / m);
                    maps[mapIdx].position((int) (pos - (m * ((long) mapIdx))));
                    maps[mapIdx].get(points,offset,dimension);
                }
            };
        }
    }

    public void getPoint(int globalRow, double[] points, int dimension, int offset)
    {
        throw new UnsupportedOperationException();
    }
}
