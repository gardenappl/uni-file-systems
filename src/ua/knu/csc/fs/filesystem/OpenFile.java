package ua.knu.csc.fs.filesystem;

final class OpenFile {
    /**
     * Current buffer for read/write operations, corresponds to one data block.
     */
    byte[] buffer;

    /**
     * The relative index of the data block which the buffer corresponds to.
     */
    int bufferBlockNum;
    private final int BUFFER_BLOCK_NUM_NONE = -1;

    /**
     * Current read/write position relative to start of file
     */
    int position;

    /**
     * Index of file descriptor
     */
    int fdIndex;

    /**
     * Cached file descriptor
     */
    FileDescriptor fd;

    /**
     * If set to true, buffer should be written to disk at some point
     */
    boolean dirtyBuffer;

    /**
     * If set to true, the cached file descriptor should be written to disk at some point
     */
    boolean dirtyFd;

    private final int bufferSize;

    /**
     * Do not use this directly!
     * Instead, use {@link OpenFileTable#allocate(int, FileDescriptor)} and {@link OpenFileTable#deallocate(OpenFile)}
     */
    OpenFile(int bufferSize, int fdIndex) {
        this.bufferSize = bufferSize;
        reset(fdIndex, null);
    }
    
    void reset(int fdIndex, FileDescriptor fd) {
        if (buffer == null)
            buffer = new byte[bufferSize];
        this.fdIndex = fdIndex;
        this.fd = fd;
        this.position = 0;
        this.bufferBlockNum = BUFFER_BLOCK_NUM_NONE;
        this.dirtyBuffer = false;
    }

    void reset() {
        this.buffer = null;
        this.fdIndex = OpenFileTable.FD_UNUSED;
        this.fd = null;
    }
}
