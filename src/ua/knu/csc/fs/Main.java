package ua.knu.csc.fs;

import ua.knu.csc.fs.filesystem.FileSystem;
import ua.knu.csc.fs.filesystem.OFTEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) {
        //4 cylinders, 2 surfaces, 8 sectors/track, 64 bytes/sector
        //according to presentation.pdf
        IOSystem ioTest = new IOSystem(64, 64, "io_test.bin");

        ioTest.writeBlock(0, "Hello World".getBytes(StandardCharsets.UTF_8));
        
        try {
            ioTest.saveToFile();
            ioTest.readFromFile();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        
        byte[] buffer = new byte[ioTest.blockSize];
        ioTest.readBlock(0, buffer);
        System.out.println(new String(buffer, StandardCharsets.UTF_8));

        //////////
        
        long l = 567;
        System.out.println(MathUtils.toLong(MathUtils.toBytes(l)));

        //////////

        IOSystem vdd = new IOSystem(64, 64, IOSystem.DEFAULT_SAVE_FILE);
        FileSystem fs = new FileSystem(vdd, 25);
        
        OFTEntry root = fs.getRootDirectory();
        byte[] string = "Hello, world! Hahahahahaha, yes this is a very very long string! (more than 64 bytes)"
                .getBytes(StandardCharsets.UTF_8);
        
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
