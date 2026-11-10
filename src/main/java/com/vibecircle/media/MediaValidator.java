package com.vibecircle.media;

import com.vibecircle.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Decides what a file really is from its first bytes ("magic numbers").
 * The client's file name and Content-Type are never trusted.
 */
@Component
public class MediaValidator {

    public static final int MAX_FILES = 10;
    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;
    private static final Set<String> MP4_BRANDS = Set.of("isom", "iso2", "mp41", "mp42", "avc1", "M4V ");

    private record Detected(MediaKind kind, String extension, String contentType) {
    }

    public ValidatedMedia validate(MultipartFile file) {
        byte[] head = new byte[16];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        Detected d = detect(head, n);
        if (d == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Unsupported or corrupt file. Allowed: JPEG, PNG, GIF, WebP images and MP4 video");
        }
        long limit = d.kind() == MediaKind.IMAGE ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (file.getSize() > limit) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    d.kind() == MediaKind.IMAGE ? "Images are limited to 10 MB" : "Videos are limited to 50 MB");
        }
        return new ValidatedMedia(file, d.kind(), d.extension(), d.contentType(), file.getSize());
    }

    private static Detected detect(byte[] b, int n) {
        if (n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new Detected(MediaKind.IMAGE, "jpg", "image/jpeg");
        }
        if (n >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return new Detected(MediaKind.IMAGE, "png", "image/png");
        }
        if (n >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return new Detected(MediaKind.IMAGE, "gif", "image/gif");
        }
        if (n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new Detected(MediaKind.IMAGE, "webp", "image/webp");
        }
        if (n >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p'
                && MP4_BRANDS.contains(new String(b, 8, 4, StandardCharsets.ISO_8859_1))) {
            return new Detected(MediaKind.VIDEO, "mp4", "video/mp4");
        }
        return null;
    }
}
