package org.example.aisurv.camera;

public final class DuplicateCameraException extends RuntimeException {
    public enum Field { DISPLAY_NAME, STREAM }
    private final Field field;
    public DuplicateCameraException(Field field) {
        super(field == Field.DISPLAY_NAME ? "A camera with this display name already exists"
                : "A camera with this stream URL already exists");
        this.field = field;
    }
    public Field field() { return field; }
}
