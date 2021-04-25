package ua.knu.csc.fs.filesystem;

public final class OpenFile {
    /**
     * Current buffer for read/write operations, corresponds to one data block.
     */
    byte[] buffer;

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
     * Instead, use {@link OpenFileTable#allocate(int, FileDescriptor, int)} and {@link OpenFileTable#deallocate(OpenFile)}
     */
    OpenFile(int bufferSize, int fdIndex, int pos) {
        this.bufferSize = bufferSize;
        reset(fdIndex, null, pos);
    }
    
    void reset(int fdIndex, FileDescriptor fd, int pos) {
        if (buffer == null)
            buffer = new byte[bufferSize];
        this.fdIndex = fdIndex;
        this.fd = fd;
        this.position = pos;
        this.dirtyBuffer = false;
    }
}
