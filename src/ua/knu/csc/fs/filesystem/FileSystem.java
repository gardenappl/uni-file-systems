package ua.knu.csc.fs.filesystem;

import ua.knu.csc.fs.IOSystem;
import ua.knu.csc.fs.MathUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class FileSystem {
    private final IOSystem ioSystem;

    private static final int OFT_SIZE = 25;
    private final OpenFileTable oftTable;
    private final OpenFile rootDirectory;
    private final Directory directory;
    private final FileDescriptor initFileDescriptor;

    /**
     * k reserved blocks.
     * 1st reserved block contains only the bitmap,
     * the other k-1 blocks each can contain multiple file descriptors
     */
    private final int reservedBlocks;
    private final int numOfFdInBlock;


    public FileSystem(IOSystem ioSystem, int maxFiles) throws FakeIOException {
        if (ioSystem.blockSize % FileDescriptor.BYTES != 0)
            throw new IllegalArgumentException("This file system only supports I/O devices where block size is a multiple of " + FileDescriptor.BYTES);
        
        this.ioSystem = ioSystem;

        // Create init fd
        this.initFileDescriptor = new FileDescriptor(0, new int[] {
                FileDescriptor.BLOCK_UNUSED,
                FileDescriptor.BLOCK_UNUSED,
                FileDescriptor.BLOCK_UNUSED
        });

        //Root dir is always open
        this.oftTable = new OpenFileTable(OFT_SIZE, ioSystem.blockSize);
        
        try {
            this.rootDirectory = oftTable.allocate(0, 0);
        } catch (FakeIOException e) {
            throw new RuntimeException(e);
        }
        this.reservedBlocks = 1 + MathUtils.divideCeil(maxFiles * FileDescriptor.BYTES,  ioSystem.blockSize);
        this.numOfFdInBlock = ioSystem.blockSize / FileDescriptor.BYTES;

        // Make sure that root file descriptor is valid
        byte[] buffer = new byte[ioSystem.blockSize];
        FileDescriptor fileDescriptor = readFdBlock(0, buffer);
        if (fileDescriptor.isUnused()) {
            writeFdBlock(0, initFileDescriptor, buffer);
            this.directory = new Directory();
        } else {
            // Read directory data from file system
            byte[] dirBuffer = new byte[fileDescriptor.fileSize];
            read(this.rootDirectory, dirBuffer, fileDescriptor.fileSize);
            this.directory = new Directory(dirBuffer);
        }
    }

    /**
     * Read contents of file into buffer
     * @param file file obtained via open()
     * @param buffer data buffer to read into
     * @param count how many bytes to read
     * @return amount of bytes read, 0 if end of file
     */
    public int read(OpenFile file, byte[] buffer, int count) {
        if (count > buffer.length)
            throw new IllegalArgumentException("Byte count is bigger than buffer size!");

        byte[] fdBlock = new byte[ioSystem.blockSize];
        FileDescriptor fd = readFdBlock(file.fd, fdBlock);

        int bytesRead = 0;
        while (bytesRead < count) {
            if (file.position == fd.fileSize)
                break;

            //Need to swap buffers
            if (file.position % ioSystem.blockSize == 0) {
                if (file.dirty) {
                    //If file was modified, write changes to disk
                    ioSystem.writeBlock(fd.blocks[(file.position - 1) / ioSystem.blockSize], file.buffer);
                    file.dirty = false;
                }
                ioSystem.readBlock(fd.blocks[file.position / ioSystem.blockSize], file.buffer);
            }

            int positionInBuffer = file.position % file.buffer.length;
            int copyCount = Math.min(
                    Math.min(fd.fileSize - file.position, count - bytesRead),
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
     * @return amount of bytes written, 0 if end of file
     */
    public int write(OpenFile file, byte[] buffer, int count) throws IOException {
        if (count > buffer.length)
            throw new IllegalArgumentException("Byte count is bigger than buffer size!");

        byte[] fdBlock = new byte[ioSystem.blockSize];
        FileDescriptor fd = readFdBlock(file.fd, fdBlock);

        byte[] bitmapBlock = new byte[ioSystem.blockSize];
        ioSystem.readBlock(0, bitmapBlock);
        long bitmap = MathUtils.toLong(bitmapBlock);

        long oldBitmap = bitmap;

        int bytesWritten = 0;
        while (bytesWritten < count) {
            //Need to swap buffers
            if (file.position % ioSystem.blockSize == 0) {
                if (file.dirty) {
                    //If file was modified, write changes to disk
                    ioSystem.writeBlock(fd.blocks[(file.position - 1) / ioSystem.blockSize], file.buffer);
                    file.dirty = false;
                }

                if (file.position / ioSystem.blockSize >= 3)
                    throw new FakeIOException("File can only be 3 blocks long");

                //Get pointer to next block
                if (fd.blocks[file.position / ioSystem.blockSize] == FileDescriptor.BLOCK_UNUSED) {
                    //Allocate new block
                    long[] bitmapRef = new long[] { bitmap };
                    int newBlock = allocateDataBlock(bitmapRef);
                    bitmap = bitmapRef[0];
                    
                    fd.blocks[file.position / ioSystem.blockSize] = newBlock;
                }
                ioSystem.readBlock(fd.blocks[file.position / ioSystem.blockSize], file.buffer);
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
            if (file.position > fd.fileSize)
                fd.fileSize = file.position;
            file.dirty = true;
        }

        //Flush changed data to disk
        writeFdBlock(file.fd, fd, fdBlock);
        if (bitmap != oldBitmap) {
            MathUtils.toBytes(bitmap, bitmapBlock);
            ioSystem.writeBlock(0, bitmapBlock);
        }

        if (file.dirty) {
            ioSystem.writeBlock(fd.blocks[file.position / ioSystem.blockSize], file.buffer);
            file.dirty = false;
        }


        return bytesWritten;
    }
    
    public void seek(OpenFile file, int position) {
        file.position = position;

        //Swap buffers if the new position is in another block
        int oldBlockNum = file.position / ioSystem.blockSize;
        int newBlockNum = position / ioSystem.blockSize;
        
        if (oldBlockNum != newBlockNum) {
            byte[] fdBlock = new byte[ioSystem.blockSize];
            FileDescriptor fd = readFdBlock(file.fd, fdBlock);

            if (file.dirty) {
                file.dirty = false;
                ioSystem.writeBlock(fd.blocks[oldBlockNum], file.buffer);
            }
            ioSystem.readBlock(fd.blocks[newBlockNum], file.buffer);
        }
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
     * Reads the reserved area block which contains the file descriptor
     * @param fdIndex index of file descriptor
     * @param fdBlockBuffer this buffer will contain the block with the fd
     * @return parsed {@link FileDescriptor}
     */
    private FileDescriptor readFdBlock(int fdIndex, byte[] fdBlockBuffer) {
        int blockIndex = getBlockWithFd(fdIndex);
        ioSystem.readBlock(blockIndex, fdBlockBuffer);

        return parseFdInBlock(fdIndex, fdBlockBuffer);
    }

    /**
     * Writes the parsed {@link FileDescriptor}, and the surrounding block buffer
     * @param fdIndex the file descriptor number
     * @param fd the parsed file descriptor
     * @param fdBlockBuffer buffer which contains this FD index as well as other FDs
     */
    private void writeFdBlock(int fdIndex, FileDescriptor fd, byte[] fdBlockBuffer) {
        ByteBuffer buffer = ByteBuffer.wrap(fdBlockBuffer);
        buffer.position(getPositionInBlock(fdIndex));

        buffer.putInt(fd.fileSize);
        for (int blockPointer : fd.blocks)
            buffer.putInt(blockPointer);
        ioSystem.writeBlock(getBlockWithFd(fdIndex), fdBlockBuffer);
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
        return rootDirectory;
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
     * @throws IOException the write function causes an error
     */
    private void saveDirectory() throws IOException {
        byte[] bufferDirectory = directory.toByteArray();
        seek(this.rootDirectory, 0);
        write(this.rootDirectory, bufferDirectory, bufferDirectory.length);
    }

    /**
     * Create new file in the file system
     * @param fileName name of created file (max name length 4)
     */
    public void create(String fileName) throws IOException {
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
        writeFdBlock(freeFd, initFileDescriptor, buffer);

        // Save changes in the directory
        saveDirectory();
    }

<<<<<<< Updated upstream
    public void destroy(String fileName) throws IOException {
=======
    /**
     * Destroy a file in the file system
     * @param fileName name of the file
     */
    public void destroy(String fileName) throws FakeIOException {
>>>>>>> Stashed changes
        // Checking length of file name
        if (fileName.length() > 4) {
            throw new FakeIOException("File doesn't exist");
        }

        // Find the file descriptor by searching the directory
        // Remove the directory entry
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        int entryIndex = directory.findEntry(fileNameBytes);
        int removeFdIndex = directory.entries.get(entryIndex).fdIndex;

        if (oftTable.isOpened(removeFdIndex)) {
            // Checking if file is opened
            throw new FakeIOException("File is opened");
        }
        directory.removeEntry(entryIndex);


<<<<<<< Updated upstream
        // Update the bitmap to reflect the freed blocks
        byte[] bitmapBlock = new byte[ioSystem.blockSize];
        ioSystem.readBlock(0, bitmapBlock);
        long bitmap = MathUtils.toLong(bitmapBlock);

=======
        // Scan the file descriptor to find the data blocks which must be freed,
        // and update the bitmap
>>>>>>> Stashed changes
        byte[] buffer = new byte[ioSystem.blockSize];
        FileDescriptor fileDescriptor = readFdBlock(removeFdIndex, buffer);
        for (int i = 0; i < FileDescriptor.BLOCK_COUNT; i++) {
            int freeBlockIndex = fileDescriptor.blocks[i];
            if (freeBlockIndex != FileDescriptor.BLOCK_UNUSED) {
                bitmap = MathUtils.setZeroByte(bitmap, freeBlockIndex);
            }
        }

        MathUtils.toBytes(bitmap, bitmapBlock);
        ioSystem.writeBlock(0, bitmapBlock);

        // Free the file descriptor
        writeFdBlock(
                removeFdIndex,
                new FileDescriptor(0, new int[FileDescriptor.BLOCK_COUNT]),
                buffer
        );
<<<<<<< Updated upstream
=======
        ioSystem.writeBlock(getBlockWithFd(removeFdIndex), buffer);

        // Save updated bitmap
        MathUtils.toBytes(bitmap, buffer);
        ioSystem.writeBlock(0, buffer);
>>>>>>> Stashed changes

        // Save changes in the directory
        saveDirectory();
    }

//    public static void main(String[] args) {
//        try {
//            // 4 cylinders, 2 surfaces, 8 sectors/track, 64 bytes/sector
//            // according to presentation.pdf
//            IOSystem vdd = new IOSystem(64, 64, IOSystem.DEFAULT_SAVE_FILE);
//            FileSystem fs = new FileSystem(vdd, 25);
//
//            fs.create("foo");
//            fs.create("foo2");
//            fs.destroy("foo");
//
//            System.out.println("Everything is working");
//        } catch (IOException e) {
//            e.printStackTrace();
//            System.out.println("Message: " + e.getMessage());
//        }
//    }
}
