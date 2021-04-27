package ua.knu.csc.fs.filesystem;

/**
 * Table of open files, this is kept in RAM.
 */
final class OpenFileTable {
    private final OpenFile[] entryPool;
    static final int FD_UNUSED = -1;

    public final int size;

    public OpenFileTable(int entries, int bufferSize) {
        entryPool = new OpenFile[entries];
        size = entries;
        for (int i = 0; i < entryPool.length; i++)
            entryPool[i] = new OpenFile(bufferSize, FD_UNUSED, 0);
    }

    /**
     * @throws FakeIOException if file is already open
     * @return index of opened file, usable for {@link #getOpenFile(int)}
     */
    public int allocate(int fdIndex, FileDescriptor fd, int position) throws FakeIOException {
        OpenFile freeEntry = null;
        int freeEntryIndex = -1;
        for (int i = 0; i < entryPool.length; i++) {
            if (entryPool[i].fdIndex == FD_UNUSED && freeEntry == null) {
                freeEntry = entryPool[i];
                freeEntryIndex = i;
            } else if (entryPool[i].fdIndex == fdIndex) {
                throw new FakeIOException("File already opened");
            }
        }

        if (freeEntry != null) {
            freeEntry.reset(fdIndex, fd, position);
            return freeEntryIndex;
        } else {
            throw new RuntimeException("Not enough space for new OFT entry");
        }
    }

    public void deallocate(OpenFile entry) {
        entry.reset();
    }

    /**
     * Get the Open File Table entry associated with an index
     * @param index index in the OFT
     * @return null if this index does not point to an opened file, otherwise returns an OpenFile instance
     */
    public OpenFile getOpenFile(int index) {
        if (index < 0 || index >= entryPool.length)
            return null;
        else if (entryPool[index].fdIndex == FD_UNUSED)
            return null;
        else
            return entryPool[index];
    }

    /**
     * Get the Open File Table entry associated with an index. Throws an exception if the file is not open.
     * @param index index in the OFT
     * @return null if this index does not point to an opened file, otherwise returns an OpenFile instance
     */
    public OpenFile getOpenFileSafe(int index) throws FakeIOException {
        OpenFile file = getOpenFile(index);
        if (file == null)
            throw new FakeIOException("No opened file with index " + index);
        return file;
    }

    public boolean isOpened(int fdIndex) {
        for (OpenFile entry : entryPool) {
            if (entry.fdIndex == fdIndex) {
                return true;
            }
        }
        return false;
    }
}
