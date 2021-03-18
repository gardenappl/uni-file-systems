package ua.knu.csc.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) {
        //4 cylinders, 2 surfaces, 8 sectors/track, 64 bytes/sector
        //according to presentation.pdf
        IOSystem hdd = new IOSystem(64, 64);
        
        hdd.writeBlock(0, "Hello World".getBytes(StandardCharsets.UTF_8));
        
        try {
            hdd.saveToFile();
            hdd.readFromFile();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        
        byte[] buffer = new byte[hdd.blockSize];
        hdd.readBlock(0, buffer);
        System.out.println(new String(buffer, StandardCharsets.UTF_8));
    }
}
