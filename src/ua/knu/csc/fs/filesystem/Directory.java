package ua.knu.csc.fs.filesystem;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

class DirectoryEntry {
    byte[] name;
    int fdIndex;

    DirectoryEntry(byte[] name, int fdIndex) {
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
     * Convert this directory into byte array
     * @return byte array that represents the directory
     */
    public byte[] toByteArray() {
        byte[] buffer = new byte[entries.size() * 8];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        for (DirectoryEntry entry : entries) {
            byteBuffer.put(entry.name);
            byteBuffer.putInt(entry.fdIndex);
        }
        return byteBuffer.array();
    }

    public void createEntry(byte[] name, int fdIndex) throws FakeIOException {
        // Create name of 4 byte length
        byte[] name4Byte = new byte[4];
        System.arraycopy(name, 0, name4Byte, 0, name.length);

        // Looking for free entry
        int entryIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).fdIndex == UNUSED_ENTRY) {
                if (entryIndex == -1) {
                    entryIndex = i;
                }
            } else if (Arrays.equals(entries.get(i).name, name4Byte)) {
                throw new FakeIOException("File already exists");
            }
        }

        // Adding new entry
        if (entryIndex == -1) {
            entries.add(new DirectoryEntry(name4Byte, fdIndex));
        } else {
            entries.get(entryIndex).name = name4Byte;
            entries.get(entryIndex).fdIndex = fdIndex;
        }
    }

    public int removeEntry(byte[] name) throws FakeIOException {
        // Create name of 4 byte length
        byte[] name4Byte = new byte[4];
        System.arraycopy(name, 0, name4Byte, 0, name.length);

        // Looking for entry and set fdIndex to UNUSED_ENTRY
        for (int i = 0; i < entries.size(); i++) {
            if (Arrays.equals(entries.get(i).name, name4Byte)) {
                int fdIndex = entries.get(i).fdIndex;
                entries.get(i).fdIndex = UNUSED_ENTRY;
                return fdIndex;
            }
        }
        throw new FakeIOException("File doesn't exist");
    }
}
