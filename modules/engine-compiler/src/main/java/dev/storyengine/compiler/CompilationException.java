package dev.storyengine.compiler;

/** Stable compiler diagnostic containing source, JSON path, code and remediation. */
public final class CompilationException extends IllegalArgumentException {
    private final String path;
    public CompilationException(String path, String code, String message) {
        super(code + " " + path + ": " + message); this.path = path;
    }
    public String path() { return path; }
}
