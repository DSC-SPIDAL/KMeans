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

import static java.lang.Integer.*;

public class DataConverter {
    private static Options programOptions = new Options();

    static {
        programOptions.addOption("i", true, "Input file");
        programOptions.addOption("n", true, "Number of points");
        programOptions.addOption("d", true, "Dimensionality");
        programOptions.addOption("b", true, "Is big-endian?");
        programOptions.addOption("o", true, "Output directory");
        programOptions.addOption("t", true, "Type [tb | bb | bt]");
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
        String type = cmd.getOptionValue("t").toLowerCase();

        switch (type){
            case "tb":
                convertTextToBinary(
                        file, d, isBigEndian, outputDir);
                break;
            case "bb":
                convertBinaryToBinary(file, n, d, isBigEndian, outputDir);
                break;
            case "bt":
                convertBinaryToText(file, n, d, isBigEndian, outputDir);
                break;
            default:
                throw new RuntimeException("Unsupported type " + type + " Has to be either tb or bb or bt");
        }
    }

    private static void convertBinaryToText(String file, int n, int d, boolean isBigEndian, String outputDir) {
        throw new RuntimeException("Not implemented yet :)");
    }

    private static void convertBinaryToBinary(String file, int n, int d, boolean isBigEndian, String outputDir) throws IOException {
        String name = com.google.common.io.Files.getNameWithoutExtension(file);
        Path outFile = Paths.get(outputDir, name+ (isBigEndian ? "_LittleEndian" : "_BigEndian") +".bin");
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(Paths.get(file), StandardOpenOption.READ));
             BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outFile, StandardOpenOption.CREATE))) {

            DataInput inStream = isBigEndian ? new DataInputStream(bis) : new LittleEndianDataInputStream(bis);
            // The idea is to change from big endian to little endian and vice versa
            DataOutput outStream = isBigEndian ? new LittleEndianDataOutputStream(bos) : new DataOutputStream(bos);

            for (int i = 0; i < n; i++)
            {
                for (int j = 0; j < d; j++)
                {
                    outStream.writeDouble(inStream.readDouble());
                }
            }
        }
    }

    private static void convertTextToBinary(
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
