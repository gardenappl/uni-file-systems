package ua.knu.csc.fs.filesystem;

import ua.knu.csc.fs.IOSystem;
import ua.knu.csc.fs.MathUtils;

import java.nio.ByteBuffer;

public final class FileSystem {
    private final IOSystem ioSystem;

    //size of Opened File Table
    private static final int OFT_SIZE = 35;
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
    public static final int MAX_FILE_NAME_SIZE = 10;

    //Limited by bitmap size
    private static final int MAX_DATA_BLOCKS = 64;

    public FileSystem(IOSystem ioSystem) throws FakeIOException {
        if (ioSystem.blockSize % FileDescriptor.BYTES != 0)
            throw new IllegalArgumentException("This file system only supports I/O devices where block size is a multiple of " + FileDescriptor.BYTES);

        this.ioSystem = ioSystem;
        this.oftTable = new OpenFileTable(OFT_SIZE, ioSystem.blockSize);

        this.numOfFdInBlock = ioSystem.blockSize / FileDescriptor.BYTES;

        // Calculate reserved blocks area size
        // Assume that every file, on average, takes up 2 data blocks.
        // In this case, we want the reserved area and the data area to fill up at the same time.

        // If x is the amount of FileDescriptor blocks:
        // (max. amount of data blocks described by FDs == actual amount of data blocks)
        // x * numOfFdInBlock * 2 == min(ioSystem.blockCount - x - 1, MAX_DATA_BLOCKS)

        final int AVG_FILE_BLOCKS = 2;
        // (Add +1 for bitmap block)
        this.reservedBlocks = Math.min(
                1 + (ioSystem.blockCount - 1) / (numOfFdInBlock * AVG_FILE_BLOCKS + 1),
                1 + MAX_DATA_BLOCKS / (numOfFdInBlock * AVG_FILE_BLOCKS)
        );

        System.err.printf(
                "Created FS with 1 bitmap block, %d FD blocks, %d data blocks\n",
                reservedBlocks - 1,
                Math.min(ioSystem.blockCount - reservedBlocks, MAX_DATA_BLOCKS)
        );

        // Create init fd
        this.initFileDescriptor = new FileDescriptor(0, new int[]{
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

            int rootIndex = oftTable.allocate(0, new FileDescriptor(0, new int[]{
                    FileDescriptor.BLOCK_UNUSED,
                    FileDescriptor.BLOCK_UNUSED,
                    FileDescriptor.BLOCK_UNUSED
            }));
            root = oftTable.getOpenFile(rootIndex);

            this.directory = new Directory();
        } else {
            int rootIndex = oftTable.allocate(0, fileDescriptor);
            root = oftTable.getOpenFile(rootIndex);

            // Read directory data from file system
            byte[] dirBuffer = new byte[fileDescriptor.fileSize];
            read(this.root, dirBuffer, fileDescriptor.fileSize);
            this.directory = new Directory(dirBuffer);
        }
    }

    /**
     * Read contents of file into buffer
     *
     * @param openFile open file index obtained via open()
     * @param buffer data buffer to read into
     * @param count how many bytes to read
     * @return amount of bytes read, {@link #END_OF_FILE} if reached end of file
     */
    public int read(int openFile, byte[] buffer, int count) throws FakeIOException {
        return read(oftTable.getOpenFileSafe(openFile), buffer, count);
    }

    private int read(OpenFile file, byte[] buffer, int count) {
        if (count > buffer.length)
            throw new IllegalArgumentException("Byte count is bigger than buffer size!");

        if (file.position == file.fd.fileSize)
            return END_OF_FILE;

        int bytesRead = 0;
        while (bytesRead < count) {
            if (file.position == file.fd.fileSize)
                break;

            //Need to swap buffers
            if (file.bufferBlockNum != file.position / ioSystem.blockSize) {
                if (file.dirtyBuffer) {
                    //If file was modified, write changes to disk
                    ioSystem.writeBlock(file.fd.blocks[file.bufferBlockNum], file.buffer);
                    file.dirtyBuffer = false;
                }
                file.bufferBlockNum = file.position / ioSystem.blockSize;
                ioSystem.readBlock(file.fd.blocks[file.bufferBlockNum], file.buffer);
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
     *
     * @param openFile open file index obtained via open()
     * @param buffer data buffer to write from
     * @param count how many bytes to write
     * @return amount of bytes written
     */
    public int write(int openFile, byte[] buffer, int count) throws FakeIOException {
        return write(oftTable.getOpenFileSafe(openFile), buffer, count);
    }

    private int write(OpenFile file, byte[] buffer, int count) throws FakeIOException {
        if (count > buffer.length)
            throw new IllegalArgumentException("Byte count is bigger than buffer size!");

        long oldBitmap = bitmap;

        int bytesWritten = 0;
        while (bytesWritten < count) {
            //Need to swap buffers
            if (file.bufferBlockNum != file.position / ioSystem.blockSize) {
                if (file.dirtyBuffer) {
                    //If file was modified, write changes to disk
                    ioSystem.writeBlock(file.fd.blocks[file.bufferBlockNum], file.buffer);
                    file.dirtyBuffer = false;
                }

                if (file.position / ioSystem.blockSize >= 3)
                    throw new FakeIOException("File can only be 3 blocks long");

                //Get pointer to next block
                if (file.fd.blocks[file.position / ioSystem.blockSize] == FileDescriptor.BLOCK_UNUSED) {
                    //Allocate new block
                    long[] bitmapRef = new long[]{bitmap};
                    int newBlock = allocateDataBlock(bitmapRef);
                    bitmap = bitmapRef[0];

                    file.fd.blocks[file.position / ioSystem.blockSize] = newBlock;
                    file.dirtyFd = true;
                }
                file.bufferBlockNum = file.position / ioSystem.blockSize;
                ioSystem.readBlock(file.fd.blocks[file.bufferBlockNum], file.buffer);
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

    /**
     * Move current read/write position in open file
     * @param openFile index of open file, obtained via {@link #openFile(String)}
     * @param position new read/write position
     */
    public void seek(int openFile, int position) throws FakeIOException {
        seek(oftTable.getOpenFileSafe(openFile), position);
    }

    private void seek(OpenFile file, int position) throws FakeIOException {
        if (position < 0 || position > file.fd.fileSize)
            throw new FakeIOException("Can't seek to position " + position +
                    ", file size is " + file.fd.fileSize);

        //No need to swap buffers in seek function, read/write functions do this automatically
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
     *
     * @param bitmap reference to bitmap (should be a single Long)
     * @return pointer to block
     * @throws FakeIOException there is no more room in the I/O system
     */
    private int allocateDataBlock(long[] bitmap) throws FakeIOException {
        int freeBlock = MathUtils.findZeroByte(bitmap[0]);
        if (freeBlock < 0 || reservedBlocks + freeBlock >= ioSystem.blockCount)
            throw new FakeIOException("Out of space");
        bitmap[0] = MathUtils.setOneByte(bitmap[0], freeBlock);
        return reservedBlocks + freeBlock;
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
     *
     * @param file open file entry which contains cached FD and data buffer.
     */
    private void sync(OpenFile file) {
        if (file.dirtyBuffer) {
            ioSystem.writeBlock(file.fd.blocks[file.bufferBlockNum], file.buffer);
            file.dirtyBuffer = false;
        }
        if (file.dirtyFd) {
            byte[] fdBlock = new byte[ioSystem.blockSize];
            ioSystem.readBlock(getBlockWithFd(file.fdIndex), fdBlock);

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
     * @param fileName name of created file (max name length is {@link #MAX_FILE_NAME_SIZE})
     */
    public void create(String fileName) throws FakeIOException {
        // Checking length of file name
        if (fileName.length() > MAX_FILE_NAME_SIZE) {
            throw new FakeIOException("Max length of file name is " + MAX_FILE_NAME_SIZE);
        }

        // Find a free file descriptor
        int freeFd = findFreeFd();

        // Find a free entry in the directory
        directory.createEntry(fileName, freeFd);

        // Initialize fd
        byte[] buffer = new byte[ioSystem.blockSize];
        ioSystem.readBlock(getBlockWithFd(freeFd), buffer);
        writeFdToBlock(freeFd, initFileDescriptor, buffer);
        ioSystem.writeBlock(getBlockWithFd(freeFd), buffer);

        // Save changes in the directory
        saveDirectory();
    }

    /**
     * Destroy a file in the file system
     * @param fileName name of the file
     */
    public void destroy(String fileName) throws FakeIOException {
        // Checking length of file name
        if (fileName.length() > MAX_FILE_NAME_SIZE) {
            throw new FakeIOException("Illegal file name");
        }

        // Find the file descriptor by searching the directory
        // Remove the directory entry
        int entryIndex = directory.findEntry(fileName);
        int removeFdIndex = directory.entries.get(entryIndex).fdIndex;

        if (oftTable.isOpened(removeFdIndex)) {
            // Checking if file is opened
            throw new FakeIOException("File is opened");
        }
        directory.removeEntry(entryIndex);


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

        // Save updated bitmap
        MathUtils.toBytes(bitmap, buffer);
        ioSystem.writeBlock(0, buffer);

        // Save changes in the directory
        saveDirectory();
    }

    /**
     * Builds a string with file names and their size
     * @return string with main info about files
     */
    public String listFiles() {
        //Flush cache before listing files
        sync();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.directory.entries.size(); i++) {
            DirectoryEntry entry = this.directory.entries.get(i);
            if (!Directory.isUnused(entry)) {
                byte[] fdBlock = new byte[ioSystem.blockSize];
                ioSystem.readBlock(getBlockWithFd(entry.fdIndex), fdBlock);
                FileDescriptor currDescriptor = parseFdInBlock(entry.fdIndex, fdBlock);
                sb.append(entry.name);
                sb.append(' ');
                sb.append(currDescriptor.fileSize);
                if (i != directory.entries.size() - 1)
                    sb.append(", ");
            }
        }

        return sb.toString();
    }

    /**
     * Open an existing file for read/write operations.
     * @param fileName name of the file in file system
     * @return index of opened file, usable for {@link #read(int, byte[], int)} and {@link #write(int, byte[], int)}
     */
    public int openFile(String fileName) throws FakeIOException {
        if (fileName == null || fileName.length() > MAX_FILE_NAME_SIZE) {
            throw new FakeIOException("Illegal file name");
        }

        for (DirectoryEntry entry : directory.entries) {
            if (fileName.equals(entry.name)) {
                int fdIndex = entry.fdIndex;
                
                byte[] fdBlock = new byte[ioSystem.blockSize];
                ioSystem.readBlock(getBlockWithFd(fdIndex), fdBlock);
                FileDescriptor fd = parseFdInBlock(fdIndex, fdBlock);
                
                return oftTable.allocate(fdIndex, fd);
            }
        }

        throw new FakeIOException("File does not exist: " + fileName);
    }

    /**
     * Closes opened file.
     * @param openFile index of open file, obtainable via {@link #openFile(String)}
     */
    public void closeFile(int openFile) throws FakeIOException {
        closeFile(oftTable.getOpenFileSafe(openFile));
    }
    
    private void closeFile(OpenFile file) {
        sync(file);
        oftTable.deallocate(file);
    }

    public String getFileName(int openFile) throws FakeIOException {
        int fdIndex = oftTable.getOpenFileSafe(openFile).fdIndex;
        for (DirectoryEntry entry : directory.entries) {
            if (entry.fdIndex == fdIndex) {
                return entry.name;
            }
        }
        throw new FakeIOException("File is open but not not found in the root directory? Something is very wrong.");
    }
}
