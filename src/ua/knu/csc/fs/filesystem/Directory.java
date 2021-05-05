package ua.knu.csc.fs.filesystem;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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
    static final int ENTRY_SIZE = FileSystem.MAX_FILE_NAME_SIZE + Integer.BYTES;
    ArrayList<DirectoryEntry> entries;
    int unusedEntriesCount;
    int maxEntryNumber;
    int changedEntryIndex = -1;

    Directory(int maxFileSize) {
        this.entries = new ArrayList<>();
        this.unusedEntriesCount = 0;
        this.maxEntryNumber = maxFileSize / ENTRY_SIZE;
    }

    Directory(byte[] buffer, int maxFileSize) throws FakeIOException {
        if (buffer.length % ENTRY_SIZE != 0) {
            throw new FakeIOException("Directory data is corrupted");
        }
        this.maxEntryNumber = maxFileSize / ENTRY_SIZE;

        int size = buffer.length / ENTRY_SIZE;
        if (size > maxEntryNumber) {
            throw new FakeIOException("Reached limit of entries number");
        }
        this.entries = new ArrayList<>(size);

        // Reading directory from byte array
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        for (int i = 0; i < size; i++) {
            byte[] name = new byte[FileSystem.MAX_FILE_NAME_SIZE];
            byteBuffer.get(name);
            int fdIndex = byteBuffer.getInt();
            this.entries.add(new DirectoryEntry(
                    name,
                    fdIndex
            ));

            // Checking for unused entries
            if (fdIndex == UNUSED_ENTRY) {
                unusedEntriesCount += 1;
            }
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
     * @return true if the directory has unused entry, else - false
     */
    public boolean hasUnusedEntries() {
        return unusedEntriesCount > 0;
    }

    /**
     * Convert this directory into byte array
     * @return byte array that represents the directory
     */
    public byte[] toByteArray() {
        byte[] buffer = new byte[entries.size() * ENTRY_SIZE];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        for (DirectoryEntry entry : entries) {
            byteBuffer.put(entry.name.getBytes(StandardCharsets.UTF_8));
            if (entry.name.length() < FileSystem.MAX_FILE_NAME_SIZE)
                byteBuffer.put(new byte[FileSystem.MAX_FILE_NAME_SIZE - entry.name.length()]);
            byteBuffer.putInt(entry.fdIndex);
        }
        return byteBuffer.array();
    }

    public byte[] entryToByteArray(int entryIndex) {
        byte[] buffer = new byte[ENTRY_SIZE];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        DirectoryEntry entry = entries.get(entryIndex);
        byteBuffer.put(entry.name.getBytes(StandardCharsets.UTF_8));
        if (entry.name.length() < FileSystem.MAX_FILE_NAME_SIZE)
            byteBuffer.put(new byte[FileSystem.MAX_FILE_NAME_SIZE - entry.name.length()]);
        byteBuffer.putInt(entry.fdIndex);

        return byteBuffer.array();
    }

    /**
     * Create new entry in the directory
     * @param name file name
     * @param fdIndex index of the file descriptor
     * @throws FakeIOException file already exists
     */
    public void createEntry(String name, int fdIndex) throws FakeIOException {
        // Check entries limit
        if (!hasUnusedEntries() && entries.size() >= maxEntryNumber) {
            throw new FakeIOException("Reached limit of entries number");
        }

        // Looking for free entry
        int entryIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entryIndex == -1) {
                if (isUnused(entries.get(i))) {
                    entryIndex = i;
                }
            } else if (entries.get(i).name.equals(name)) {
                throw new FakeIOException("File already exists");
            }
        }

        // Adding new entry
        if (entryIndex == -1) {
            entries.add(new DirectoryEntry(name, fdIndex));
            changedEntryIndex = entries.size() - 1;
        } else {
            entries.get(entryIndex).name = name;
            entries.get(entryIndex).fdIndex = fdIndex;
            changedEntryIndex = entryIndex;
            unusedEntriesCount -= 1;
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
    public void removeEntry(int entryIndex)
    {
        entries.get(entryIndex).fdIndex = UNUSED_ENTRY;
        changedEntryIndex = entryIndex;
        unusedEntriesCount += 1;
    }
}
