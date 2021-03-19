package ua.knu.csc.fs.filesystem;

import ua.knu.csc.fs.IOSystem;
import ua.knu.csc.fs.MathUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class FileSystem {
    private final IOSystem ioSystem;

    private static final int OFT_SIZE = 25;
    private final OpenFileTable oftTable;
    private final OpenFile rootDirectory;

    /**
     * k reserved blocks.
     * 1st reserved block contains only the bitmap,
     * the other k-1 blocks each can contain multiple file descriptors
     */
    private final int reservedBlocks;

    /**
     * Size of a file descriptor entry, in bytes (see {@link FileDescriptor}):
     * 4 bytes = 1 int, for file size
     * 12 bytes = 3 ints, for pointers to 3 blocks
     * TODO: last int could point to another file descriptor, so we can have more than 3 blocks?
     */
    private static final int FD_SIZE = 16;

    public FileSystem(IOSystem ioSystem, int maxFiles) {
        if (ioSystem.blockCount > Long.SIZE)
            throw new IllegalArgumentException("This file system only supports I/O devices with <=64 sectors");

        this.ioSystem = ioSystem;

        this.oftTable = new OpenFileTable(OFT_SIZE, ioSystem.blockSize);

        byte[] rootDirSizeBytes = new byte[ioSystem.blockSize];
        //Root dir is always the first FD and is always in block 1
        ioSystem.readBlock(1, rootDirSizeBytes);
        int rootDirSize = ByteBuffer.wrap(rootDirSizeBytes).getInt();
        
        try {
            this.rootDirectory = oftTable.allocate(0, 0, rootDirSize);
        } catch (FakeIOException e) {
            throw new RuntimeException(e);
        }
        this.reservedBlocks = 1 + maxFiles * FD_SIZE / ioSystem.blockSize;
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
        FileDescriptor fd = readFdBlock(file, fdBlock);

        int bytesRead = 0;
        while (bytesRead < count) {
            if (file.position == file.fileSize)
                break;

            if (file.position % ioSystem.blockSize == 0) {
                //Read next block
                if (file.dirty) {
                    ioSystem.writeBlock(fd.blocks[(file.position - 1) / ioSystem.blockSize], file.buffer);
                    file.dirty = false;
                }
                ioSystem.readBlock(fd.blocks[file.position / ioSystem.blockSize], file.buffer);
            }

            int positionInBuffer = file.position % file.buffer.length;
            int copyCount = Math.min(
                    Math.min(file.fileSize - file.position, count - bytesRead),
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
        FileDescriptor fd = readFdBlock(file, fdBlock);

        byte[] bitmapBlock = new byte[ioSystem.blockSize];
        ioSystem.readBlock(0, bitmapBlock);
        long bitmap = MathUtils.toLong(bitmapBlock);

        long oldBitmap = bitmap;

        int bytesWritten = 0;
        while (bytesWritten < count) {
            if (file.position % ioSystem.blockSize == 0) {
                //Write block
                if (file.dirty) {
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
            if (file.position > file.fileSize)
                file.fileSize = file.position;
            file.dirty = true;
        }

        //Flush changed data to disk
        fd.length = file.fileSize;
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
        int oldBlockNum = file.position / ioSystem.blockSize;
        int newBlockNum = position / ioSystem.blockSize;

        file.position = position;
        
        if (oldBlockNum != newBlockNum) {
            byte[] fdBlock = new byte[ioSystem.blockSize];
            FileDescriptor fd = readFdBlock(file, fdBlock);

            if (file.dirty) {
                file.dirty = false;
                ioSystem.writeBlock(fd.blocks[oldBlockNum], file.buffer);
            }
            ioSystem.readBlock(fd.blocks[newBlockNum], file.buffer);
        }
    }

    /**
     * Reads the reserved area block which contains the file descriptor
     * @param file the file whose FD we are looking for
     * @param fdBlockBuffer this buffer will contain the block with the fd
     * @return parsed FileDescriptor
     */
    private FileDescriptor readFdBlock(OpenFile file, byte[] fdBlockBuffer) {
        ioSystem.readBlock(
                1 + file.fd * FD_SIZE / ioSystem.blockSize,
                fdBlockBuffer
        );
        ByteBuffer buffer = ByteBuffer.wrap(fdBlockBuffer);
        buffer.position(file.fd * FD_SIZE % ioSystem.blockSize);
        return new FileDescriptor(buffer.getInt(), new int[] {
                buffer.getInt(),
                buffer.getInt(),
                buffer.getInt()
        });
    }

    /**
     * Writes the parsed {@link FileDescriptor}, and the surrounding block buffer
     * @param fdIndex the file descriptor number
     * @param fd the parsed file descriptor
     * @param fdBlockBuffer buffer which contains this FD index as well as other FDs
     */
    private void writeFdBlock(int fdIndex, FileDescriptor fd, byte[] fdBlockBuffer) {
        ByteBuffer buffer = ByteBuffer.wrap(fdBlockBuffer);
        buffer.position(fdIndex * FD_SIZE % ioSystem.blockSize);
        buffer.putInt(fd.length);
        for (int blockPointer : fd.blocks)
            buffer.putInt(blockPointer);
        ioSystem.writeBlock(
                1 + fdIndex * FD_SIZE / ioSystem.blockSize,
                fdBlockBuffer
        );
    }

    /**
     * Allocate block for file data
     * @param bitmap reference to bitmap (should be a single Long)
     * @return pointer to block
     * @throws FakeIOException there is no more room in the I/O system
     */
    private int allocateDataBlock(long[] bitmap) throws FakeIOException {
        int freeBlock = MathUtils.findZeroByte(bitmap[0]);
        if (freeBlock < 0)
            throw new FakeIOException("No room for new data block!");
        bitmap[0] = MathUtils.setOneByte(bitmap[0], freeBlock);
        return reservedBlocks + freeBlock;
    }

    /**
     * Temporary method for testing
     */
    public OpenFile getRootDirectory() {
        return rootDirectory;
    }
}
