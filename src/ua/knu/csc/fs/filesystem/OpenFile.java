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

    /**
     * If set to true, buffer should be written to disk
     */
    boolean dirty;

    /**
     * Do not use this directly!
     * Instead, use {@link OpenFileTable#allocate(int, int)} and {@link OpenFileTable#deallocate(OpenFile)}
     */
    OpenFile(int bufferSize, int fd, int pos) {
        this.buffer = new byte[bufferSize];
        reset(fd, pos);
    }
    
    void reset(int fd, int pos) {
        this.fd = fd;
        this.position = pos;
        this.dirty = false;
    }
}
