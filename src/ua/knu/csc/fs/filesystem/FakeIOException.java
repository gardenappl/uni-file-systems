package ua.knu.csc.fs.filesystem;

import java.io.IOException;

public class FakeIOException extends IOException {
    public FakeIOException(String message) {
        super(message);
    }
}
