package ua.knu.csc.fs.filesystem;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

class DirectoryEntry {
    String name;
    int fdIndex;

    DirectoryEntry(byte[] name, int fdIndex) {
        StringBuilder sb = new StringBuilder();
        for (byte b : name) {
            if (b == 0)
                break;
            sb.append((char) b);
        }
        this.name = sb.toString();
        this.fdIndex = fdIndex;
    }

    DirectoryEntry(String name, int fdIndex) {
        this.name = name;
        this.fdIndex = fdIndex;
    }
}

public class Directory {
    static final int UNUSED_ENTRY = -1;
    ArrayList<DirectoryEntry> entries;

    Directory() {
        this.entries = new ArrayList<>();
    }

    Directory(byte[] buffer) throws FakeIOException {
        if (buffer.length % 8 != 0) {
            throw new FakeIOException("Directory data is corrupted");
        }
        int size = buffer.length / 8;
        this.entries = new ArrayList<>(size);

        // Reading directory from byte array
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        for (int i = 0; i < size; i++) {
            byte[] name = new byte[4];
            byteBuffer.get(name);
            this.entries.add(new DirectoryEntry(
                    name,
                    byteBuffer.getInt()
            ));
        }
    }

    /**
     * @param entry directory entry
     * @return true if entry is unused, else - false
     */
    public static boolean isUnused(DirectoryEntry entry) {
        return entry.fdIndex == Directory.UNUSED_ENTRY;
    }

    /**
     * Convert this directory into byte array
     * @return byte array that represents the directory
     */
    public byte[] toByteArray() {
        byte[] buffer = new byte[entries.size() * 8];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        for (DirectoryEntry entry : entries) {
            byteBuffer.put(entry.name.getBytes(StandardCharsets.UTF_8));
            if (entry.name.length() < FileSystem.MAX_FILE_SIZE)
                byteBuffer.put(new byte[FileSystem.MAX_FILE_SIZE - entry.name.length()]);
            byteBuffer.putInt(entry.fdIndex);
        }
        return byteBuffer.array();
    }

    /**
     * Create new entry in the directory
     * @param name file name
     * @param fdIndex index of the file descriptor
     * @throws FakeIOException file already exists
     */
    public void createEntry(String name, int fdIndex) throws FakeIOException {
        // Looking for free entry
        int entryIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (isUnused(entries.get(i))) {
                if (entryIndex == -1) {
                    entryIndex = i;
                }
            } else if (entries.get(i).name.equals(name)) {
                throw new FakeIOException("File already exists");
            }
        }

        // Adding new entry
        if (entryIndex == -1) {
            entries.add(new DirectoryEntry(name, fdIndex));
        } else {
            entries.get(entryIndex).name = name;
            entries.get(entryIndex).fdIndex = fdIndex;
        }
    }

    /**
     * Find the entry in the directory
     * @param name file name
     * @return index of the entry in the directory
     * @throws FakeIOException file doesn't exist
     */
    public int findEntry(String name) throws FakeIOException {
        // Looking for entry and return entry index
        for (int i = 0; i < entries.size(); i++) {
            if (name.equals(entries.get(i).name)) {
                return i;
            }
        }
        throw new FakeIOException("File doesn't exist");
    }

    /**
     * Remove entry from the directory by changing fdIndex in {@link DirectoryEntry}
     * to {@link #UNUSED_ENTRY}
     * @param entryIndex index of the entry in the directory
     */
    public void removeEntry(int entryIndex) {
        entries.get(entryIndex).fdIndex = UNUSED_ENTRY;
    }
}
