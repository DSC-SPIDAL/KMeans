package org.saliya.ompi.kmeans;

import com.google.common.base.Optional;
import com.google.common.io.LittleEndianDataOutputStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;

public class DataGenerator
{
    private static Options programOptions = new Options();

    static
    {
        programOptions.addOption("n", true, "Number of points");
        programOptions.addOption("d", true, "Dimensionality");
        programOptions.addOption("k", true, "Number of centers");
        programOptions.addOption("b", true, "Is big-endian?");
        programOptions.addOption("o", true, "Output directory");
        programOptions.addOption("t", true, "Is text?");
    }

    public static void main(String[] args) throws IOException
    {
        Optional<CommandLine> parserResult = Utils
            .parseCommandLineArguments(args, programOptions);
        if (!parserResult.isPresent())
        {
            System.out.println(Utils.ERR_PROGRAM_ARGUMENTS_PARSING_FAILED);
            new HelpFormatter().printHelp(Utils.PROGRAM_NAME, programOptions);
            return;
        }

        CommandLine cmd = parserResult.get();
        if (!(cmd.hasOption("n") && cmd.hasOption("d") && cmd.hasOption("k") &&
              cmd.hasOption("o") && cmd.hasOption("b") && cmd.hasOption("t")))
        {
            System.out.println(Utils.ERR_INVALID_PROGRAM_ARGUMENTS);
            new HelpFormatter().printHelp(Utils.PROGRAM_NAME, programOptions);
            return;
        }

        int n = Integer.parseInt(cmd.getOptionValue("n"));
        int d = Integer.parseInt(cmd.getOptionValue("d"));
        int k = Integer.parseInt(cmd.getOptionValue("k"));
        boolean isBigEndian = Boolean.parseBoolean(cmd.getOptionValue("b"));
        boolean isText = Boolean.parseBoolean(cmd.getOptionValue("t"));
        String outputDir = cmd.getOptionValue("o");


        if (isText)
        {
            generatePointsAsText(n, d, k, outputDir);
        }
        else
        {
            generatePointsAsBinary(
                n, d, k, isBigEndian, outputDir);

        }
    }

    private static void generatePointsAsText(
        int n, int d, int k, String outputDir)
    {
        Path pointsFile = Paths.get(outputDir, "points.txt");
        Path centersFile = Paths.get(outputDir, "centers.txt");

        try (PrintWriter pointsWriter = new PrintWriter(
            Files.newBufferedWriter(
                pointsFile, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE));
            PrintWriter centersWriter = new PrintWriter(
                Files.newBufferedWriter(
                    centersFile, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)))
        {
            DecimalFormat twoDForm = new DecimalFormat("0.0000");
            for (int i = 0; i < n; i++)
            {
                for (int j = 0; j < d; j++)
                {
                    double coord = Math.random();
                    pointsWriter.print(
                        twoDForm.format(coord) + ((j == (d - 1)) ? "\n" : ","));
                    if (i >= k)
                    {
                        continue;
                    }
                    centersWriter.print(
                        twoDForm.format(coord) + ((j == (d - 1)) ? "\n" : ","));
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    private static void generatePointsAsBinary(
        int n, int d, int k, boolean isBigEndian, String outputDir)
        throws IOException
    {
        Path pointsFile = Paths.get(outputDir, "points.bin");
        Path centersFile = Paths.get(outputDir, "centers.bin");
        try (
            BufferedOutputStream pointBufferedStream = new BufferedOutputStream(
                Files.newOutputStream(pointsFile, StandardOpenOption.CREATE));
            BufferedOutputStream centerBufferedStream = new
                BufferedOutputStream(
                Files.newOutputStream(centersFile, StandardOpenOption.CREATE)))
        {
            DataOutput pointStream = isBigEndian ? new DataOutputStream(
                pointBufferedStream) : new LittleEndianDataOutputStream(
                pointBufferedStream);
            DataOutput centerStream = isBigEndian ? new DataOutputStream(
                centerBufferedStream) : new LittleEndianDataOutputStream(
                centerBufferedStream);
            for (int i = 0; i < n; i++)
            {
                for (int j = 0; j < d; j++)
                {
                    double coord = Math.random();
                    pointStream.writeDouble(coord);
                    if (i >= k)
                    {
                        continue;
                    }
                    centerStream.writeDouble(coord);
                }
            }
        }
    }
}
