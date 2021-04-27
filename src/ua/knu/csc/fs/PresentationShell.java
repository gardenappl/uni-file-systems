package ua.knu.csc.fs;

import ua.knu.csc.fs.filesystem.FakeIOException;
import ua.knu.csc.fs.filesystem.FileSystem;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

public class PresentationShell {
    static Scanner input = new Scanner(System.in);

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

    public static String[] getCommand() {
        return input.nextLine().split("\\s+");
    }

    public static void startFileSystem() {
        System.out.println("Welcome to the file system\n");
        FileSystem fs = null;

        try {
            String[] command;
            while (true) {
                command = getCommand();
                if (fs == null && !command[0].equals("in")) {
                    System.out.println("File system isn't created");
                    continue;
                }
                if (!checkCommandSize(command)) {
                    System.out.println("Wrong number of arguments");
                    continue;
                }
                switch (command[0]) {
                    case "cr":
                        cr(command, fs);
                        continue;
                    case "de":
                        de(command, fs);
                        continue;
                    case "op":
                        op(command, fs);
                        continue;
                    case "cl":
                        cl(command, fs);
                        continue;
                    case "rd":
                        rd(command, fs);
                        continue;
                    case "wr":
                        wr(command, fs);
                        continue;
                    case "sk":
                        sk(command, fs);
                        continue;
                    case "dr":
                        dr(fs);
                        continue;
                    case "in":
                        fs = in(command, fs);
                        continue;
                    case "sv":
                        sv(command, fs);
                        continue;
                    case "ex":
                        break;
                    default:
                        System.out.println("Wrong command");
                        continue;
                }
                break;
            }
        } catch (FakeIOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
//        try {
//            // 4 cylinders, 2 surfaces, 8 sectors/track
//            // according to presentation.pdf
//            // 4 * 2 * 8 = 64 bytes/sector
//            // IOSystem vdd = new IOSystem(64, 64, IOSystem.DEFAULT_SAVE_FILE);
//            // FileSystem fs = new FileSystem(vdd);
//            startFileSystem();
//        } catch (FakeIOException e) {
//            System.out.println(e.getMessage());
//        }
    }
}
