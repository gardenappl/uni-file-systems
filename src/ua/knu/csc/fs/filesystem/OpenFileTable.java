package ua.knu.csc.fs.filesystem;

/**
 * Table of open files, this is kept in RAM.
 */
final class OpenFileTable {
    private final OFTEntry[] entryPool;
    private static final int FD_UNUSED = -1;

    public OpenFileTable(int entries, int bufferSize) {
        entryPool = new OFTEntry[entries];
        for (int i = 0; i < entryPool.length; i++)
            entryPool[i] = new OFTEntry(bufferSize, FD_UNUSED, 0, 0);
    }

    /**
     * @throws FakeIOException if file is already open
     */
    public OFTEntry allocate(int fd, int position, int fileSize) throws FakeIOException {
        OFTEntry freeEntry = null;
        for (OFTEntry entry : entryPool) {
            if (entry.fd == FD_UNUSED && freeEntry == null)
                freeEntry = entry;
            if (entry.fd == fd)
                throw new FakeIOException("File already opened");
        }

        if (freeEntry != null) {
            freeEntry.reset(fd, position, fileSize);
            return freeEntry;
        } else {
            throw new RuntimeException("Not enough space for new OFT entry");
        }
    }

    public void deallocate(OFTEntry entry) {
        entry.fd = FD_UNUSED;
    }
}
