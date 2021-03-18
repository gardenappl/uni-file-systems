package ua.knu.csc.fs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class IOSystem {
    private final byte[][] ldisk;
    public final int blockSize;

    private static final String SAVE_FILE = "virtualdisk.bin";
    
    public IOSystem(int blockCount, int blockSize) {
        ldisk = new byte[blockCount][];
        for (int i = 0; i < blockCount; i++)
            ldisk[i] = new byte[blockSize];
        
        this.blockSize = blockSize;
    }

    /**
     * Reads contents of logical block at address i,
     * will write at most {@link #blockSize} bytes into supplied array.
     */
    public void readBlock(int i, byte[] buffer) {
        System.arraycopy(ldisk[i], 0, buffer, 0, Math.min(blockSize, buffer.length));
    }
    
    /**
     * Write contents of buffer into logical block at address i,
     * will read at most {@link #blockSize} bytes from buffer.
     */
    public void writeBlock(int i, byte[] buffer) {
        System.arraycopy(buffer, 0, ldisk[i], 0, Math.min(blockSize, buffer.length));
    }

    /**
     * Save contents of virtual disk to the real filesystem.
     */
    public void saveToFile() throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(SAVE_FILE)) {

            for (byte[] sector : ldisk)
                outputStream.write(sector);
        }
    }

    /**
     * Read contents of virtual disk from the real filesystem.
     */
    public void readFromFile() throws IOException {
        try (FileInputStream inputStream = new FileInputStream(SAVE_FILE)) {
            for (byte[] sector : ldisk) {
                if (inputStream.read(sector) != blockSize)
                    throw new RuntimeException("Wrong byte count in " + SAVE_FILE);
            }
        }
    }
}
