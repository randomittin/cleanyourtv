package io.github.randomittin.voiceassistant;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Builds a multipart/form-data body (text fields and one file) for the transcription upload. */
public final class Multipart {

    private final String boundary;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private boolean closed;

    public Multipart(String boundary) {
        this.boundary = boundary;
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public Multipart field(String name, String value) {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n";
        write(head);
        write(value);
        write("\r\n");
        return this;
    }

    public Multipart file(String name, String filename, String mime, byte[] data) {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        write(head);
        out.write(data, 0, data.length);
        write("\r\n");
        return this;
    }

    public byte[] build() {
        if (!closed) {
            write("--" + boundary + "--\r\n");
            closed = true;
        }
        return out.toByteArray();
    }

    private void write(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write(b, 0, b.length);
    }
}
