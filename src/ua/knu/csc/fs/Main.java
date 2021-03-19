package ua.knu.csc.fs;

import ua.knu.csc.fs.filesystem.FileSystem;
import ua.knu.csc.fs.filesystem.OFTEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
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
        
        OFTEntry root = fs.getRootDirectory();
        byte[] string = "Hello, world! Hahahahahaha, yes this is a very very long string, yes yes!".getBytes(StandardCharsets.UTF_8);
        
        try {
            fs.write(root, string, string.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] bytes = new byte[string.length];

        fs.seek(root, 0);
        fs.read(root, bytes, bytes.length);

        System.out.println(new String(bytes, StandardCharsets.UTF_8));
    }
}
