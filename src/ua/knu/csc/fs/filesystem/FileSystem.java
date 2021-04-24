package ua.knu.csc.fs.filesystem;

import ua.knu.csc.fs.IOSystem;
import ua.knu.csc.fs.MathUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class FileSystem {
    private final IOSystem ioSystem;

    private static final int OFT_SIZE = 25;
    private final OpenFileTable oftTable;
    private final OpenFile root;
    private final Directory directory;
    private final FileDescriptor initFileDescriptor;

    /**
     * k reserved blocks.
     * 1st reserved block contains only the bitmap,
     * the other k-1 blocks each can contain multiple file descriptors
     */
    private final int reservedBlocks;
    private final int numOfFdInBlock;

    private long bitmap;

    public static final int END_OF_FILE = -1;

    public FileSystem(IOSystem ioSystem, int maxFiles) throws FakeIOException {
        if (ioSystem.blockSize % FileDescriptor.BYTES != 0)
            throw new IllegalArgumentException("This file system only supports I/O devices where block size is a multiple of " + FileDescriptor.BYTES);
        
        this.ioSystem = ioSystem;

        this.oftTable = new OpenFileTable(OFT_SIZE, ioSystem.blockSize);

        this.reservedBlocks = 1 + MathUtils.divideCeil(maxFiles * FileDescriptor.BYTES,  ioSystem.blockSize);
        this.numOfFdInBlock = ioSystem.blockSize / FileDescriptor.BYTES;

        // Create init fd
        this.initFileDescriptor = new FileDescriptor(0, new int[] {
                FileDescriptor.BLOCK_UNUSED,
                FileDescriptor.BLOCK_UNUSED,
                FileDescriptor.BLOCK_UNUSED
        });

        // Make sure that root file descriptor is valid
        byte[] buffer = new byte[ioSystem.blockSize];
        ioSystem.readBlock(getBlockWithFd(0), buffer);
        FileDescriptor fileDescriptor = parseFdInBlock(0, buffer);

        if (fileDescriptor.isUnused()) {
            writeFdToBlock(0, initFileDescriptor, buffer);
            ioSystem.writeBlock(getBlockWithFd(0), buffer);

            this.root = oftTable.allocate(0, new FileDescriptor(0, new int[] {
                    FileDescriptor.BLOCK_UNUSED,
                    FileDescriptor.BLOCK_UNUSED,
                    FileDescriptor.BLOCK_UNUSED
            }), 0);

            this.directory = new Directory();
        } else {
            this.root = oftTable.allocate(0, fileDescriptor, 0);

            // Read directory data from file system
            byte[] dirBuffer = new byte[fileDescriptor.fileSize];
            read(this.root, dirBuffer, fileDescriptor.fileSize);
            this.directory = new Directory(dirBuffer);
        }
    }

    /**
     * Read contents of file into buffer
     * @param file file obtained via open()
     * @param buffer data buffer to read into
     * @param count how many bytes to read
     * @return amount of bytes read, {@link #END_OF_FILE} if reached end of file
     */
    public int read(OpenFile file, byte[] buffer, int count) {
        if (count > buffer.length)
            throw new IllegalArgumentException("Byte count is bigger than buffer size!");

        if (file.position == file.fd.fileSize)
            return END_OF_FILE;

        int bytesRead = 0;
        while (bytesRead < count) {
            if (file.position == file.fd.fileSize)
                break;

            //Need to swap buffers
            if (file.position % ioSystem.blockSize == 0) {
                if (file.dirtyBuffer) {
                    //If file was modified, write changes to disk
                    ioSystem.writeBlock(file.fd.blocks[(file.position - 1) / ioSystem.blockSize], file.buffer);
                    file.dirtyBuffer = false;
                }
                ioSystem.readBlock(file.fd.blocks[file.position / ioSystem.blockSize], file.buffer);
            }

            int positionInBuffer = file.position % file.buffer.length;
            int copyCount = Math.min(
                    Math.min(file.fd.fileSize - file.position, count - bytesRead),
                    file.buffer.length - positionInBuffer
            );
            System.arraycopy(
                    file.buffer,
                    positionInBuffer,
                    buffer,
                    bytesRead,
                    copyCount
            );
            bytesRead += copyCount;
            file.position += copyCount;
        }
        return bytesRead;
    }


    /**
     * Write contents of buffer into file
     * @param file file obtained via open()
     * @param buffer data buffer to write from
     * @param count how many bytes to write
     * @return amount of bytes written
     */
    public int write(OpenFile file, byte[] buffer, int count) throws FakeIOException {
        if (count > buffer.length)
            throw new IllegalArgumentException("Byte count is bigger than buffer size!");

        long oldBitmap = bitmap;

        int bytesWritten = 0;
        while (bytesWritten < count) {
            //Need to swap buffers
            if (file.position % ioSystem.blockSize == 0) {
                if (file.dirtyBuffer) {
                    //If file was modified, write changes to disk
                    ioSystem.writeBlock(file.fd.blocks[(file.position - 1) / ioSystem.blockSize], file.buffer);
                    file.dirtyBuffer = false;
                }

                if (file.position / ioSystem.blockSize >= 3)
                    throw new FakeIOException("File can only be 3 blocks long");

                //Get pointer to next block
                if (file.fd.blocks[file.position / ioSystem.blockSize] == FileDescriptor.BLOCK_UNUSED) {
                    //Allocate new block
                    long[] bitmapRef = new long[] { bitmap };
                    int newBlock = allocateDataBlock(bitmapRef);
                    bitmap = bitmapRef[0];
                    
                    file.fd.blocks[file.position / ioSystem.blockSize] = newBlock;
                    file.dirtyFd = true;
                }
                ioSystem.readBlock(file.fd.blocks[file.position / ioSystem.blockSize], file.buffer);
            }

            int positionInBuffer = file.position % file.buffer.length;
            int copyCount = Math.min(
                    count - bytesWritten,
                    file.buffer.length - positionInBuffer
            );
            System.arraycopy(
                    buffer,
                    bytesWritten,
                    file.buffer,
                    file.position % file.buffer.length,
                    copyCount
            );
            bytesWritten += copyCount;
            file.position += copyCount;
            if (file.position > file.fd.fileSize) {
                file.fd.fileSize = file.position;
                file.dirtyFd = true;
            }
            file.dirtyBuffer = true;
        }
        //Update bitmap now
        if (oldBitmap != bitmap) {
            byte[] bitmapBlock = new byte[ioSystem.blockSize];
            MathUtils.toBytes(bitmap, bitmapBlock);
            ioSystem.writeBlock(0, bitmapBlock);
        }
        return bytesWritten;
    }
    
    public void seek(OpenFile file, int position) {
        //Swap buffers if the new position is in another block
        int oldBlockNum = file.position / ioSystem.blockSize;
        int newBlockNum = position / ioSystem.blockSize;
        
        if (oldBlockNum != newBlockNum) {
            if (file.dirtyBuffer) {
                file.dirtyBuffer = false;
                ioSystem.writeBlock(file.fd.blocks[oldBlockNum], file.buffer);
            }
            ioSystem.readBlock(file.fd.blocks[newBlockNum], file.buffer);
        }
        file.position = position;
    }

    /**
     * @param fdIndex index of file descriptor
     * @return index of block with file descriptor
     */
    private int getBlockWithFd(int fdIndex) {
        return 1 + fdIndex * FileDescriptor.BYTES / ioSystem.blockSize;
    }

    /**
     * @param fdIndex index of file descriptor
     * @return position of the fd in block
     */
    private int getPositionInBlock(int fdIndex) {
        return fdIndex * FileDescriptor.BYTES % ioSystem.blockSize;
    }

    /**
     * @param fdIndex index of file descriptor or index in block [0; {@link #numOfFdInBlock} - 1]
     * @param fdBlockBuffer this buffer contains the block with the fd
     * @return parsed {@link FileDescriptor}
     */
    private FileDescriptor parseFdInBlock(int fdIndex, byte[] fdBlockBuffer) {
        ByteBuffer buffer = ByteBuffer.wrap(fdBlockBuffer);
        buffer.position(getPositionInBlock(fdIndex));
        return new FileDescriptor(buffer.getInt(), new int[] {
                buffer.getInt(),
                buffer.getInt(),
                buffer.getInt()
        });
    }

    /**
     * Writes the data stored in a {@link FileDescriptor} into the proper I/O block
     * @param fdIndex the file descriptor number
     * @param fd the parsed file descriptor
     * @param fdBlockBuffer IO block retrieved by {@link #getBlockWithFd(int)}
     */
    private void writeFdToBlock(int fdIndex, FileDescriptor fd, byte[] fdBlockBuffer) {
        ByteBuffer buffer = ByteBuffer.wrap(fdBlockBuffer);
        buffer.position(getPositionInBlock(fdIndex));

        buffer.putInt(fd.fileSize);
        for (int blockPointer : fd.blocks)
            buffer.putInt(blockPointer);
    }

    /**
     * Allocate block for file data
     * @param bitmap reference to bitmap (should be a single Long)
     * @return pointer to block
     * @throws FakeIOException there is no more room in the I/O system
     */
    private int allocateDataBlock(long[] bitmap) throws FakeIOException {
        int freeBlock = MathUtils.findZeroByte(bitmap[0]);
        if (freeBlock < 0 || reservedBlocks + freeBlock > ioSystem.blockCount)
            throw new FakeIOException("No room for new data block!");
        bitmap[0] = MathUtils.setOneByte(bitmap[0], freeBlock);
        return reservedBlocks + freeBlock;
    }

    /**
     * Temporary method for testing
     * TODO: remove
     */
    public OpenFile getRootDirectory() {
        return root;
    }

    /**
     * Flush cached data into I/O system.
     * This should be called before saving the emulated I/O system into real storage
     */
    public void sync() {
        for (int i = 0; i < oftTable.size; i++) {
            OpenFile file = oftTable.getOpenFile(i);
            if (file == null)
                continue;
            sync(file);
        }
    }

    /**
     * Flush cached data into I/O system. This should be called on every CLOSE operation.
     * @param file open file entry which contains cached FD and data buffer.
     */
    private void sync(OpenFile file) {
        if (file.dirtyBuffer) {
            ioSystem.writeBlock(file.fd.blocks[file.position / ioSystem.blockSize], file.buffer);
            file.dirtyBuffer = false;
        }
        if (file.dirtyFd) {
            byte[] fdBlock = new byte[ioSystem.blockSize];
            writeFdToBlock(file.fdIndex, file.fd, fdBlock);
            ioSystem.writeBlock(getBlockWithFd(file.fdIndex), fdBlock);
            file.dirtyFd = false;
        }
    }

    /**
     * Find a free file descriptor in [2; k] blocks
     * @return index of the free file descriptor
     * @throws FakeIOException there is no more free file descriptor
     */
    private int findFreeFd() throws FakeIOException {
        for (int i = 1; i < reservedBlocks; i++) {
            byte[] buffer = new byte[ioSystem.blockSize];
            ioSystem.readBlock(i, buffer);

            for (int j = 0; j < numOfFdInBlock; j++) {
                FileDescriptor fileDescriptor = parseFdInBlock(j, buffer);
                if (fileDescriptor.isUnused()) {
                    return ((i - 1) * numOfFdInBlock) + j;
                }
            }
        }
        throw new FakeIOException("Can't find free file descriptor");
    }

    /**
     * Save the directory to the file system
     * @throws FakeIOException the write function causes an error
     */
    private void saveDirectory() throws FakeIOException {
        byte[] bufferDirectory = directory.toByteArray();
        seek(this.root, 0);
        write(this.root, bufferDirectory, bufferDirectory.length);
    }

    /**
     * Create new file in the file system
     * @param fileName name of created file (max name length 4)
     */
    public void create(String fileName) throws FakeIOException {
        // Checking length of file name
        if (fileName.length() > 4) {
            throw new FakeIOException("Max length of file name is 4");
        }

        // Find a free file descriptor
        int freeFd = findFreeFd();

        // Find a free entry in the directory
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        directory.createEntry(fileNameBytes, freeFd);

        // Initialize fd
        byte[] buffer = new byte[ioSystem.blockSize];
        ioSystem.readBlock(getBlockWithFd(freeFd), buffer);
        writeFdToBlock(freeFd, initFileDescriptor, buffer);
        ioSystem.writeBlock(getBlockWithFd(freeFd), buffer);

        // Save changes in the directory
        saveDirectory();
    }

    public void destroy(String fileName) throws FakeIOException {
        // Checking length of file name
        if (fileName.length() > 4) {
            throw new FakeIOException("File doesn't exist");
        }

        // Find the file descriptor by searching the directory
        // Remove the directory entry
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        int removeFdIndex = directory.removeEntry(fileNameBytes);

        // Scan the file descriptor to find the data blocks which must be freed,
        // and update the bitmap

        byte[] buffer = new byte[ioSystem.blockSize];
        ioSystem.readBlock(getBlockWithFd(removeFdIndex), buffer);
        FileDescriptor fileDescriptor = parseFdInBlock(getBlockWithFd(removeFdIndex), buffer);

        for (int i = 0; i < FileDescriptor.BLOCK_COUNT; i++) {
            int freeBlockIndex = fileDescriptor.blocks[i];
            if (freeBlockIndex != FileDescriptor.BLOCK_UNUSED) {
                bitmap = MathUtils.setZeroByte(bitmap, freeBlockIndex);
            }
        }

        // Free the file descriptor
        writeFdToBlock(
                removeFdIndex,
                new FileDescriptor(0, new int[FileDescriptor.BLOCK_COUNT]),
                buffer
        );
        ioSystem.writeBlock(getBlockWithFd(removeFdIndex), buffer);

        //Save updated bitmap
        MathUtils.toBytes(bitmap, buffer);
        ioSystem.writeBlock(0, buffer);

        // Save changes in the directory
        saveDirectory();
    }
}
