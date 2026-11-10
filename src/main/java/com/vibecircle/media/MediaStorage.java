package com.vibecircle.media;

import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * Storage abstraction. Today: files on local disk. Later: an S3 implementation with the same
 * methods (keep the bucket private; MediaController still authorizes every read).
 */
public interface MediaStorage {
    /** Saves the stream and returns an opaque key (never derived from the client's file name). */
    String store(InputStream in, String extension);

    Resource load(String key);

    void delete(String key);
}
