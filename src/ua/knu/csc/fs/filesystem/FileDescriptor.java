package ua.knu.csc.fs.filesystem;

final class FileDescriptor {
    /**
     * Size of a file descriptor entry, in bytes:
     * 4 bytes = 1 int, for file size
     * 12 bytes = 3 ints, for pointers to 3 blocks
     * TODO: last int could point to another file descriptor, so we can have more than 3 blocks?
     */
    static final int BYTES = 16;

    /**
     * File size in bytes
     */
    int fileSize;
    /**
     * Pointers to blocks which contain the file data
     * blocks[i] == {@link #BLOCK_UNUSED} means the block is not used
     */
    final int[] blocks;
    static final int BLOCK_UNUSED = -1;
    static final int BLOCK_COUNT = 3;

    public FileDescriptor(int fileSize, int[] blocks) {
        this.fileSize = fileSize;
        this.blocks = blocks;
    }

    /**
     * @return true if this file descriptor can be overwritten by another FD for a new file.
     */
    public boolean isUnused() {
        return (fileSize == 0 && blocks[0] == 0);
    }
}
