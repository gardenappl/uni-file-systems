package ua.knu.csc.fs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class IOSystem {
    private final byte[][] ldisk;
    public final int blockSize;
    public final int blockCount;
    private final String saveFile;

    public static final String DEFAULT_SAVE_FILE = "virtualdisk.bin";
    
    public IOSystem(int blockCount, int blockSize, String saveFile) {
        ldisk = new byte[blockCount][];
        for (int i = 0; i < blockCount; i++)
            ldisk[i] = new byte[blockSize];

        this.blockCount = blockCount;
        this.blockSize = blockSize;
        this.saveFile = saveFile;
    }

    /**
     * Reads contents of logical block at address i,
     * will write at most {@link #blockSize} bytes into supplied array.
     */
    public void readBlock(int i, byte[] buffer) {
        System.arraycopy(ldisk[i], 0, buffer, 0, Math.min(blockSize, buffer.length));
    }

    /**
     * Reads contents of logical block at address i + startReadPos, into buffer + startWritePos,
     * will write at most {@link #blockSize} bytes into supplied buffer.
     */
    public void readBlock(int i, byte[] buffer, int startReadPos, int startWritePos) {
        System.arraycopy(ldisk[i], startReadPos, buffer, startWritePos,
                Math.min(blockSize - startReadPos, buffer.length - startWritePos));
    }
    
    /**
     * Write contents of buffer into logical block at address i,
     * will read at most {@link #blockSize} bytes from buffer.
     */
    public void writeBlock(int i, byte[] buffer) {
        System.arraycopy(buffer, 0, ldisk[i], 0, Math.min(blockSize, buffer.length));
    }

    /**
     * Write contents of buffer, starting from startReadPos, into logical block at address i + startWritePos,
     * will read at most {@link #blockSize} bytes from buffer.
     */
    public void writeBlock(int i, byte[] buffer, int startReadPos, int startWritePos) {
        System.arraycopy(buffer, startReadPos, ldisk[i], startWritePos,
                Math.min(blockSize - startWritePos, buffer.length - startReadPos));
    }

    /**
     * Save contents of virtual disk to the real filesystem.
     */
    public void saveToFile() throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(saveFile)) {

            for (byte[] sector : ldisk)
                outputStream.write(sector);
        }
    }

    /**
     * Read contents of virtual disk from the real filesystem.
     */
    public void readFromFile() throws IOException {
        try (FileInputStream inputStream = new FileInputStream(saveFile)) {
            for (byte[] sector : ldisk) {
                if (inputStream.read(sector) != blockSize)
                    throw new RuntimeException("Wrong byte count in " + saveFile);
            }
        }
    }
}
