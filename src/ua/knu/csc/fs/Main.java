package ua.knu.csc.fs;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 0 && args.length != 2)
            System.err.println("Should have either no arguments or 2 arguments");

        if (args.length == 0) {
            new PresentationShell(System.out, new Scanner(System.in)).doCommands();
        } else {
            try (Scanner scanner = new Scanner(new File(args[0]))) {
                try (PrintStream printStream = new PrintStream(args[1])) {
                    new PresentationShell(printStream, scanner).doCommands();
                }
            }
        }
    }
}
