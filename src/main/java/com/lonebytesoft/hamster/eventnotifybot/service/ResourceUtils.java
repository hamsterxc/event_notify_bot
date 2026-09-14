package com.lonebytesoft.hamster.eventnotifybot.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ResourceUtils {

    private ResourceUtils() {
        throw new UnsupportedOperationException();
    }

    public static String read(final String fileName) {
        return Optional.ofNullable(ResourceUtils.class.getClassLoader().getResource(fileName))
                .map(url -> {
                    try {
                        return url.toURI();
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Path::of)
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .orElseThrow(() -> new UncheckedIOException(new IOException("File not found: " + fileName)));
    }

}
