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
import java.util.regex.Pattern;

import static java.lang.Integer.*;

public class DataConverter {
    private static Options programOptions = new Options();

    static {
        programOptions.addOption("i", true, "Input file");
        programOptions.addOption("d", true, "Dimensionality");
        programOptions.addOption("b", true, "Is big-endian?");
        programOptions.addOption("o", true, "Output directory");
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
        int d = parseInt(cmd.getOptionValue("d"));
        boolean isBigEndian = Boolean.parseBoolean(cmd.getOptionValue("b"));
        String outputDir = cmd.getOptionValue("o");


        convertToBinary(
            file, d, isBigEndian, outputDir);

    }

    private static void convertToBinary(
        String file, int d, boolean isBigEndian, String outputDir)
        throws IOException {
        String name = com.google.common.io.Files.getNameWithoutExtension(file);
        Path outFile = Paths.get(outputDir, name+".bin");
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(file));
            BufferedOutputStream bs = new BufferedOutputStream(
                Files.newOutputStream(outFile, StandardOpenOption.CREATE))) {

            DataOutput outStream = isBigEndian ? new DataOutputStream(
                bs) : new LittleEndianDataOutputStream(
                bs);

            Pattern pat = Pattern.compile(" ");
            String line;
            String[] splits;
            int start;
            while ((line = reader.readLine()) != null){
                splits = pat.split(line);
                if (splits.length != d && splits.length !=(d+1)){
                    System.out.println("Data conversion failed at line: " + line);
                    break;
                }
                start = splits.length == d ? 0 : 1;
                for (int i = start; i < splits.length; ++i){
                    outStream.writeDouble(Double.parseDouble(splits[i]));
                }
            }
        }
    }
}
