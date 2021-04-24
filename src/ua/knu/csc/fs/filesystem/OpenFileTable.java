package ua.knu.csc.fs.filesystem;

/**
 * Table of open files, this is kept in RAM.
 */
final class OpenFileTable {
    private final OpenFile[] entryPool;
    private static final int FD_UNUSED = -1;

    public final int size;

    public OpenFileTable(int entries, int bufferSize) {
        entryPool = new OpenFile[entries];
        size = entries;
        for (int i = 0; i < entryPool.length; i++)
            entryPool[i] = new OpenFile(bufferSize, FD_UNUSED, 0);
    }

    /**
     * @throws FakeIOException if file is already open
     */
    public OpenFile allocate(int fdIndex, FileDescriptor fd, int position) throws FakeIOException {
        OpenFile freeEntry = null;
        for (OpenFile entry : entryPool) {
            if (entry.fdIndex == FD_UNUSED && freeEntry == null)
                freeEntry = entry;
            if (entry.fdIndex == fdIndex)
                throw new FakeIOException("File already opened");
        }

        if (freeEntry != null) {
            freeEntry.reset(fdIndex, fd, position);
            return freeEntry;
        } else {
            throw new RuntimeException("Not enough space for new OFT entry");
        }
    }

    public void deallocate(OpenFile entry) {
        entry.fdIndex = FD_UNUSED;
    }

    /**
     * Get the Open File Table entry associated with an index
     * @param index index in the OFT
     * @return null if this index does not point to an opened file, otherwise returns an OpenFile instance
     */
    public OpenFile getOpenFile(int index) {
        if (entryPool[index].fdIndex == FD_UNUSED)
            return null;
        else
            return entryPool[index];
    }
}
