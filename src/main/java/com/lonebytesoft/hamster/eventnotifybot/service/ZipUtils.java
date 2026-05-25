package com.lonebytesoft.hamster.eventnotifybot.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Function;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

public class ZipUtils {

    private ZipUtils() {
        throw new UnsupportedOperationException();
    }

    public static byte[] compress(byte[] data) throws IOException {
        return wrap(data, DeflaterOutputStream::new);
    }

    public static byte[] decompress(byte[] data) throws IOException {
        return wrap(data, InflaterOutputStream::new);
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
