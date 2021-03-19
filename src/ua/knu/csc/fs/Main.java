package ua.knu.csc.fs;

import ua.knu.csc.fs.filesystem.FileSystem;
import ua.knu.csc.fs.filesystem.OpenFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) {
        IOSystem ioTest = new IOSystem(64, 64, "io_test.bin");

        byte[] buffer = new byte[ioTest.blockSize];

        byte[] stringBytes = "Hello World".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(stringBytes, 0, buffer, 0, stringBytes.length);
        ioTest.writeBlock(0, buffer);
        
        try {
            ioTest.saveToFile();
            ioTest.readFromFile();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        buffer = new byte[ioTest.blockSize];
        ioTest.readBlock(0, buffer);
        System.out.println(new String(buffer, StandardCharsets.UTF_8));


        /////////////


        //4 cylinders, 2 surfaces, 8 sectors/track, 64 bytes/sector
        //according to presentation.pdf
        IOSystem vdd = new IOSystem(64, 64, IOSystem.DEFAULT_SAVE_FILE);
        FileSystem fs = new FileSystem(vdd, 25);
        
        OpenFile root = fs.getRootDirectory();
        stringBytes = "Hello, world! Hahahahahaha, yes this is a very very long string, longer than 64 bytes!"
                .getBytes(StandardCharsets.UTF_8);
        
        try {
            //Write, reset position, and read
            fs.write(root, stringBytes, stringBytes.length);

            fs.seek(root, 0);

            byte[] bytes = new byte[stringBytes.length];
            fs.read(root, bytes, bytes.length);
            System.out.println(new String(bytes, StandardCharsets.UTF_8));

            //Write to file and re-create FS

            vdd.saveToFile();

            vdd = new IOSystem(64, 64, IOSystem.DEFAULT_SAVE_FILE);
            vdd.readFromFile();

            fs = new FileSystem(vdd, 25);
            
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        root = fs.getRootDirectory();

        byte[] bytes = new byte[stringBytes.length];
        fs.read(root, bytes, bytes.length);
        System.out.println(new String(bytes, StandardCharsets.UTF_8));
    }
}
