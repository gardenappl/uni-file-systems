package ua.knu.csc.fs;

import ua.knu.csc.fs.filesystem.FakeIOException;
import ua.knu.csc.fs.filesystem.FileSystem;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

public class PresentationShell {
    private FileSystem currentFS = null;
    private IOSystem currentIOSystem = null;

    private final PrintStream output;
    private final Scanner input;

    public PresentationShell(PrintStream output, Scanner input) {
        this.output = output;
        this.input = input;
    }

    private void load(
            int cylinderCount,
            int surfaceCount,
            int sectorCount,
            int sectorSize,
            String saveFileName
    ) throws IOException {
        File saveFile = new File(saveFileName);

        int blockCount = cylinderCount * surfaceCount * sectorCount;

        currentIOSystem = new IOSystem(blockCount, sectorSize);

        String message;
        if (saveFile.isFile()) {
            currentIOSystem.readFromFile(saveFileName);
            message = "disk restored";
        } else {
            message = "disk initialized";
        }
        currentFS = new FileSystem(currentIOSystem);

        output.println(message);
    }

    private void create(String fileName) throws FakeIOException {
        currentFS.create(fileName);
        output.println("file " + fileName + " created");
    }

    private void destroy(String fileName) throws FakeIOException {
        currentFS.destroy(fileName);
        output.println("file " + fileName + " destroyed");
    }

    private void open(String fileName) throws FakeIOException {
        int index = currentFS.openFile(fileName);
        output.println("file " + fileName + " opened, index=" + index);
    }

    private void close(int fileIndex) throws FakeIOException {
        String name = currentFS.getFileName(fileIndex);
        currentFS.closeFile(fileIndex);
        output.println("file " + name + " closed");
    }

    private void read(int fileIndex, int count) throws FakeIOException {
        byte[] buffer = new byte[count];

        int readCount = currentFS.read(fileIndex, buffer, count);
        if (readCount == FileSystem.END_OF_FILE)
            output.println("end of file");
        else
            output.println(readCount + " bytes read: " +
                    new String(buffer, 0, readCount, StandardCharsets.UTF_8));
    }

    private void write(int fileIndex, char c, int count) throws FakeIOException {
        byte[] buffer = new byte[count];
        Arrays.fill(buffer, (byte) c);

        int writeCount = currentFS.write(fileIndex, buffer, buffer.length);
        output.println(writeCount + " bytes written");
    }

    private void seek(int fileIndex, int pos) throws FakeIOException {
        currentFS.seek(fileIndex, pos);
        output.println("current position is " + pos);
    }

    private void dir() throws FakeIOException {
        output.println(currentFS.listFiles());
    }

    private void save(String saveFileName) throws IOException {
        currentFS.sync();
        currentIOSystem.saveToFile(saveFileName);
        output.println("disk saved");
    }

    private String[] getCommand(Scanner input) {
        return input.nextLine().split("\\s+");
    }

    private boolean checkCommandSize(String[] command) {
        return switch (command[0]) {
            case "dr", "ex" -> command.length == 1;
            case "cr", "de", "op", "cl", "sv" -> command.length == 2;
            case "rd", "sk" -> command.length == 3;
            case "wr" -> command.length == 4;
            case "in" -> command.length == 6;
            default -> true;
        };
    }

    public void doCommands() throws IOException {
        String[] command;

        while (input.hasNextLine()) {
            try {
                command = getCommand(input);
                if (currentFS == null && !command[0].equals("in")) {
                    output.println("File system isn't created");
                    continue;
                }
                if (!checkCommandSize(command)) {
                    output.println("Wrong argument count");
                    continue;
                }
                switch (command[0]) {
                    case "cr" -> create(command[1]);
                    case "de" -> destroy(command[1]);
                    case "op" -> open(command[1]);
                    case "cl" -> close(Integer.parseInt(command[1]));
                    case "rd" -> read(Integer.parseInt(command[1]), Integer.parseInt(command[2]));
                    case "wr" ->  {
                        if (command[2].length() > 1) {
                            output.println("Insert one char at a time, please.");
                            continue;
                        }
                        write(
                                Integer.parseInt(command[1]),
                                command[2].charAt(0),
                                Integer.parseInt(command[3])
                        );
                    }
                    case "sk" -> seek(Integer.parseInt(command[1]), Integer.parseInt(command[2]));
                    case "dr" -> dir();
                    case "in" -> load(
                            Integer.parseInt(command[1]),
                            Integer.parseInt(command[2]),
                            Integer.parseInt(command[3]),
                            Integer.parseInt(command[4]),
                            command[5]
                    );
                    case "sv" -> save(command[1]);
                    default -> output.println("Wrong command");
                }
            } catch (NumberFormatException e) {
                output.println("Invalid number: " + e.getMessage());
            } catch (FakeIOException e) {
                output.println("error: " + e.getMessage());
            } catch (IOException e) {
                output.println("Actual I/O exception occured!");
                throw e;
            }
        }
    }
}
