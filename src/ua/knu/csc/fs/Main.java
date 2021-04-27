package ua.knu.csc.fs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 0 && args.length != 2)
            System.err.println("Should have either no arguments or 2 arguments");

        PresentationShell shell;
        if (args.length == 0) {
            shell = new PresentationShell(System.out, new Scanner(System.in));
        } else {
            try (Scanner scanner = new Scanner(args[0])) {
                try (PrintStream printStream = new PrintStream(args[1])) {
                    shell = new PresentationShell(printStream, scanner);
                }
            }
        }
        shell.doCommands();
    }
}
