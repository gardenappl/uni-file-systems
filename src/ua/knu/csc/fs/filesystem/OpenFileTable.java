package ua.knu.csc.fs.filesystem;

/**
 * Table of open files, this is kept in RAM.
 */
final class OpenFileTable {
    private final OpenFile[] entryPool;
    private static final int FD_UNUSED = -1;

    public OpenFileTable(int entries, int bufferSize) {
        entryPool = new OpenFile[entries];
        for (int i = 0; i < entryPool.length; i++)
            entryPool[i] = new OpenFile(bufferSize, FD_UNUSED, 0);
    }

    /**
     * @throws FakeIOException if file is already open
     */
    public OpenFile allocate(int fd, int position) throws FakeIOException {
        OpenFile freeEntry = null;
        for (OpenFile entry : entryPool) {
            if (entry.fd == FD_UNUSED && freeEntry == null)
                freeEntry = entry;
            if (entry.fd == fd)
                throw new FakeIOException("File already opened");
        }

        if (freeEntry != null) {
            freeEntry.reset(fd, position);
            return freeEntry;
        } else {
            throw new RuntimeException("Not enough space for new OFT entry");
        }
    }

    public void deallocate(OpenFile entry) {
        entry.fd = FD_UNUSED;
    }
}
