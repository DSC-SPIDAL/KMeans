package org.saliya.ompi.kmeans;

import com.google.common.base.Optional;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;

public class DataDuplicator {
    private static Options programOptions = new Options();

    static {
        programOptions.addOption("i", true, "Input file");
        programOptions.addOption("n", true, "Number of points");
        programOptions.addOption("d", true, "Dimensionality");
        programOptions.addOption("b", true, "Is big-endian?");
        programOptions.addOption("o", true, "Output directory");
        programOptions.addOption("x", true, "Number of replicas");
    }

    public static void main(String[] args) throws IOException {
        Optional<CommandLine> parserResult = Utils
            .parseCommandLineArguments(args, programOptions);
        if (!parserResult.isPresent()) {
            System.out.println(Utils.ERR_PROGRAM_ARGUMENTS_PARSING_FAILED);
            new HelpFormatter().printHelp(Utils.PROGRAM_NAME, programOptions);
            return;
        }

        CommandLine cmd = parserResult.get();
        if (!(cmd.hasOption("i") && cmd.hasOption("d") &&
              cmd.hasOption("o") && cmd.hasOption("b"))) {
            System.out.println(Utils.ERR_INVALID_PROGRAM_ARGUMENTS);
            new HelpFormatter().printHelp(Utils.PROGRAM_NAME, programOptions);
            return;
        }

        String file = cmd.getOptionValue("i");
        int n = Integer.parseInt(cmd.getOptionValue("n"));
        int d = parseInt(cmd.getOptionValue("d"));
        boolean isBigEndian = Boolean.parseBoolean(cmd.getOptionValue("b"));
        String outputDir = cmd.getOptionValue("o");
        int x = Integer.parseInt(cmd.getOptionValue("x"));

        replicate(file, n, d, x, isBigEndian, outputDir);
    }

    private static void replicate(String file, int n, int d, int x, boolean isBigEndian, String outputDir) throws IOException {
        String name = com.google.common.io.Files.getNameWithoutExtension(file);
        Path outFile = Paths.get(outputDir, name+ "_X" +x +".bin");
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(Paths.get(file), StandardOpenOption.READ));
             BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outFile, StandardOpenOption.CREATE))) {

            DataInput inStream = isBigEndian ? new DataInputStream(bis) : new LittleEndianDataInputStream(bis);
            DataOutput outStream = isBigEndian ? new DataOutputStream(bos):new LittleEndianDataOutputStream(bos);

            double [] tmp = new double[d];

            for (int i = 0; i < n; i++)
            {
                for (int j = 0; j < d; j++)
                {
                    tmp[j] = inStream.readDouble();
                }
                for (int k = 0; k < x; ++k) {
                    for (int j = 0; j < d; ++d) {
                        outStream.writeDouble(tmp[j]);
                    }
                }
            }
        }
    }

}
