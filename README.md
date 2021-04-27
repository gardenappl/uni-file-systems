# uni-file-systems
**Filesystems university group project**

Implement a basic filesystem on top of an emulated I/O device.

Project uses IntelliJ IDEA.

**Make sure to read all three of these documents!**

[project.pdf](project.pdf), [presentation.pdf](presentation.pdf) and [addendum.pdf](addendum.pdf).

**Some facts:**
* This file system uses minimal caching for the bitmap, root folder contents, and FDs/data buffers for open files.
  * Bitmaps and folder contents are cached in a write-through fashion.
  * Data buffers for open files are cached in a "write-back" fashion, as in, the buffers are only read or written from disk at the last possible moment. This might improve performance e.g. when writing multiple small buffers in a row, possibly at the cost of stability.
    * Added method named `FileSystem.sync()` which flushes all buffers for all open files, this should be done before saving the virtual I/O device to disk, for example.
* File system automatically picks a good size for the reserved area.
