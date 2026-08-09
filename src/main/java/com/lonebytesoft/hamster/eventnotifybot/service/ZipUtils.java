package com.lonebytesoft.hamster.eventnotifybot.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

public class ZipUtils {

    private ZipUtils() {
        throw new UnsupportedOperationException();
    }

    public static byte[] compress(byte[] data) {
        try {
            return wrap(data, DeflaterOutputStream::new);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not compress %d bytes".formatted(data.length), e);
        }
    }

    public static byte[] decompress(byte[] data) {
        try {
            return wrap(data, InflaterOutputStream::new);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not decompress %d bytes".formatted(data.length), e);
        }
    }

    private static byte[] wrap(
            final byte[] data,
            final Function<? super OutputStream, ? extends OutputStream> wrapper
    ) throws IOException {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (
                final OutputStream outputStream = wrapper.apply(byteStream)
        ) {
            outputStream.write(data);
        }
        return byteStream.toByteArray();
    }

}
