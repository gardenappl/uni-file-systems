package ua.knu.csc.fs.filesystem;

final class FileDescriptor {
    /**
     * File size in bytes
     */
    int length;
    /**
     * Pointers to blocks which contain the file data
     * blocks[i] == {@link #BLOCK_UNUSED} means the block is not used
     */
    final int[] blocks;
    static final int BLOCK_UNUSED = 0;

    public FileDescriptor(int length, int[] blocks) {
        this.length = length;
        this.blocks = blocks;
    }
}
