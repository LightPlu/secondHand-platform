package com.example.auction.global.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class R2UploadService {

    private final S3Client s3Client;

    @Value("${cloud.r2.bucket}")
    private String bucket;

    @Value("${cloud.r2.public-url}")
    private String publicUrl;

    // 이미지 업로드
    public String upload(MultipartFile file, String folder) {
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String key = folder + "/" + UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));
            log.info("R2 업로드 완료: key={}", key);

            return publicUrl + "/" + key;

        } catch (IOException e) {
            log.error("R2 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("이미지 업로드에 실패했습니다.");
        }
    }

    // 이미지 삭제
    public void delete(String imageUrl) {
        String key = imageUrl.replace(publicUrl + "/", "");

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.deleteObject(deleteRequest);
        log.info("R2 삭제 완료: key={}", key);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new RuntimeException("올바르지 않은 파일 형식입니다.");
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}

