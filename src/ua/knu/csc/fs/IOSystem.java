package ua.knu.csc.fs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class IOSystem {
    private final byte[][] ldisk;
    public final int blockSize;
    public final int blockCount;
    
    public IOSystem(int blockCount, int blockSize) {
        ldisk = new byte[blockCount][];
        for (int i = 0; i < blockCount; i++)
            ldisk[i] = new byte[blockSize];

        this.blockCount = blockCount;
        this.blockSize = blockSize;
    }

    /**
     * Reads contents of logical block at address i,
     * will read {@link #blockSize} bytes into supplied array.
     */
    public void readBlock(int i, byte[] buffer) {
        System.arraycopy(ldisk[i], 0, buffer, 0, blockSize);
    }
    
    /**
     * Write contents of buffer into logical block at address i,
     * will write {@link #blockSize} bytes from buffer.
     */
    public void writeBlock(int i, byte[] buffer) {
        if (buffer.length < blockSize)
            throw new IllegalArgumentException("Buffer is too small");
        System.arraycopy(buffer, 0, ldisk[i], 0, blockSize);
    }

    /**
     * Save contents of virtual disk to the real filesystem.
     */
    public void saveToFile(String saveFile) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(saveFile)) {

            for (byte[] sector : ldisk)
                outputStream.write(sector);
        }
    }

    /**
     * Read contents of virtual disk from the real filesystem.
     */
    public void readFromFile(String saveFile) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(saveFile)) {
            for (byte[] sector : ldisk) {
                if (inputStream.read(sector) != blockSize)
                    throw new RuntimeException("Wrong byte count in " + saveFile);
            }
        }
    }
}
