package ua.knu.csc.fs;

import ua.knu.csc.fs.filesystem.FakeIOException;
import ua.knu.csc.fs.filesystem.FileSystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

public class PresentationShell {
    static String INPUT_FILE = "input.txt";
    static String OUTPUT_FILE = "output.txt";

    public static boolean checkCommandSize(String[] command) {
        boolean result = false;
        switch (command[0]) {
            case "dr":
            case "ex":
                if (command.length == 1) result = true;
                break;
            case "cr":
            case "de":
            case "op":
            case "cl":
            case "sv":
                if (command.length == 2) result = true;
                break;
            case "rd":
            case "sk":
                if (command.length == 3) result = true;
                break;
            case "wr":
                if (command.length == 4) result = true;
                break;
            case "in":
                if (command.length == 6) result = true;
                break;
        }
        return result;
    }

    public static FileSystem in(String[] command, FileSystem fs) throws FakeIOException {
        String message;
        if (fs == null || !fs.getFileName().equals(command[5])) {
            message = "disk initialized";
        } else {
            message = "disk restored";
        }

        int blockCount =
                Integer.parseInt(command[1]) *
                Integer.parseInt(command[2]) *
                Integer.parseInt(command[3]);
        int blockSize = Integer.parseInt(command[4]);

        IOSystem vdd = new IOSystem(blockCount, blockSize, command[5]);
        FileSystem new_fs = new FileSystem(vdd);

        // Print result
        System.out.println(message);
        return new_fs;
    }

    public static void cr(String[] command, FileSystem fs) throws FakeIOException {
        fs.create(command[1]);
        System.out.println("file " + command[1] + " created");
    }

    public static void de(String[] command, FileSystem fs) throws FakeIOException {
        fs.destroy(command[1]);
        System.out.println("file " + command[1] + " destroyed");
    }

    public static void op(String[] command, FileSystem fs) throws FakeIOException {
        int index = fs.openFile();
        System.out.println("file " + command[1] + " opened, index=" + index);
    }

    public static void cl(String[] command, FileSystem fs) throws FakeIOException {
        fs.closeFile();
        System.out.println("file <name> closed");
    }

    public static void rd(String[] command, FileSystem fs) throws FakeIOException {
        byte[] buffer = new byte[Integer.parseInt(command[2])];

        int readCount = fs.read(, buffer, buffer.length);
        System.out.println(readCount + " bytes read: " + new String(buffer, StandardCharsets.UTF_8));
    }

    public static void wr(String[] command, FileSystem fs) throws FakeIOException {
        if (command[2].length() != 1) {
            throw new FakeIOException("Wrong input");
        }
        byte[] buffer = new byte[Integer.parseInt(command[3])];
        Arrays.fill(buffer, command[2].getBytes(StandardCharsets.UTF_8)[0]);

        fs.write(, buffer, buffer.length);
        System.out.println("<count> bytes written");
    }

    public static void sk(String[] command, FileSystem fs) throws FakeIOException {
        int pos = Integer.parseInt(command[2]);
        fs.seek(, pos);
        System.out.println("current position is " + pos);
    }

    public static void dr(FileSystem fs) throws FakeIOException {
        System.out.println(fs.listFiles());
    }

    public static void sv(String[] command, FileSystem fs) throws FakeIOException {
        fs.sync();
        System.out.println("disk saved");
    }

    public static String[] getCommand(Scanner input) {
        return input.nextLine().split("\\s+");
    }

    public static void doCommands(Scanner input) {
        FileSystem fs = null;
        String[] command;

        while (input.hasNextLine()) {
            try {
                command = getCommand(input);
                if (fs == null && !command[0].equals("in")) {
                    System.out.println("File system isn't created");
                    continue;
                }
                if (!checkCommandSize(command)) {
                    System.out.println("Wrong number of arguments");
                    continue;
                }
                switch (command[0]) {
                    case "cr" -> cr(command, fs);
                    case "de" -> de(command, fs);
                    case "op" -> op(command, fs);
                    case "cl" -> cl(command, fs);
                    case "rd" -> rd(command, fs);
                    case "wr" -> wr(command, fs);
                    case "sk" -> sk(command, fs);
                    case "dr" -> dr(fs);
                    case "in" -> fs = in(command, fs);
                    case "sv" -> sv(command, fs);
                    default -> System.out.println("Wrong command");
                }
            } catch (FakeIOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public static void startFileSystem() {
        // Input stream
        Scanner input;
        try {
            input = new Scanner(new File(INPUT_FILE));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        // Output stream
        PrintStream printStream = null;
        try {
            printStream = new PrintStream(new FileOutputStream(OUTPUT_FILE));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.setOut(printStream);

        // Do commands
        doCommands(input);

        // Close
        input.close();
    }

    public static void main(String[] args) {
        startFileSystem();
    }
}
