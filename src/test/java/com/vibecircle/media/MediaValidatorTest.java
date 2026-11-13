package com.vibecircle.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vibecircle.common.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class MediaValidatorTest {

    private final MediaValidator validator = new MediaValidator();

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    /** int... so that chars like 'P' and constants like 0x0D can be passed without casts. */
    private static byte[] withHeader(int size, int... header) {
        byte[] bytes = new byte[Math.max(size, header.length)];
        for (int i = 0; i < header.length; i++) {
            bytes[i] = (byte) header[i];
        }
        return bytes;
    }

    private void assertRejected(MockMultipartFile f, HttpStatus status) {
        assertThatThrownBy(() -> validator.validate(f))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    @Test
    void recognisesAllowedImageTypesByTheirFirstBytes() {
        assertThat(validator.validate(file("a.bin", withHeader(32, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0))).extension()).isEqualTo("jpg");
        assertThat(validator.validate(file("a.bin", withHeader(32, (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))).extension()).isEqualTo("png");
        assertThat(validator.validate(file("a.bin", "GIF89a....".getBytes(StandardCharsets.ISO_8859_1))).extension()).isEqualTo("gif");
        assertThat(validator.validate(file("a.bin", "RIFF....WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).extension()).isEqualTo("webp");
    }

    @Test
    void recognisesMp4Video() {
        byte[] mp4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};
        ValidatedMedia media = validator.validate(file("clip.bin", mp4));
        assertThat(media.kind()).isEqualTo(MediaKind.VIDEO);
        assertThat(media.contentType()).isEqualTo("video/mp4");
    }

    @Test
    void trustsContentNotTheFileNameOrDeclaredType() {
        byte[] png = withHeader(32, (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
        MockMultipartFile lying = new MockMultipartFile("file", "photo.exe", "text/plain", png);
        ValidatedMedia media = validator.validate(lying);
        assertThat(media.kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(media.extension()).isEqualTo("png");
        assertThat(media.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsTextHtmlAndExecutablesPretendingToBeImages() {
        assertRejected(new MockMultipartFile("file", "fake.png", "image/png", "plain text".getBytes()), HttpStatus.BAD_REQUEST);
        assertRejected(file("x.png", "<html><script>alert(1)</script></html>".getBytes()), HttpStatus.BAD_REQUEST);
        assertRejected(file("x.png", withHeader(32, 'M', 'Z')), HttpStatus.BAD_REQUEST);
        assertRejected(file("x.png", new byte[0]), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsVideoContainersThatAreNotMp4() {
        byte[] heic = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        assertRejected(file("x.heic", heic), HttpStatus.BAD_REQUEST);
    }

    @Test
    void enforcesTheImageSizeLimit() {
        byte[] justOver = withHeader(10 * 1024 * 1024 + 1, (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
        assertThatThrownBy(() -> validator.validate(file("big.png", justOver)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(413));

        byte[] atLimit = withHeader(10 * 1024 * 1024, (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
        assertThat(validator.validate(file("ok.png", atLimit)).kind()).isEqualTo(MediaKind.IMAGE);
    }
}
