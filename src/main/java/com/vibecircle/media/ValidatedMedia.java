package com.vibecircle.media;

import org.springframework.web.multipart.MultipartFile;

public record ValidatedMedia(MultipartFile file, MediaKind kind, String extension, String contentType, long size) {
}
