package ua.knu.csc.fs.filesystem;

public final class OpenFile {
    final byte[] buffer;

    /**
     * Current read/write position relative to start of file
     */
    int position;

    /**
     * Index of file descriptor
     */
    int fd;

    int fileSize;

    /**
     * If set to true, buffer should be written to disk
     */
    boolean dirty;

    /**
     * Do not use this directly!
     * Instead, use {@link OpenFileTable#allocate(int, int, int)} and {@link OpenFileTable#deallocate(OpenFile)}
     */
    OpenFile(int bufferSize, int fd, int pos, int fileSize) {
        this.buffer = new byte[bufferSize];
        reset(fd, pos, fileSize);
    }
    
    void reset(int fd, int pos, int fileSize) {
        this.fd = fd;
        this.position = pos;
        this.fileSize = fileSize;
        this.dirty = false;
    }
}
