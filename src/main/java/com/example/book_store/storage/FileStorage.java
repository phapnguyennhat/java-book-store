package com.example.book_store.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URL;
import java.time.Duration;

/** Private bucket: clients never get bucket access, only short-lived presigned URLs for one object. */
@Service
@RequiredArgsConstructor
public class FileStorage {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3Properties props;

    /** Content-Type and Content-Length are signed: the upload fails unless the client sends exactly these. */
    public URL uploadUrl(String key, String contentType, long size, Duration ttl) {
        return presigner.presignPutObject(p -> p.signatureDuration(ttl).putObjectRequest(o -> o
                .bucket(props.bucket()).key(key).contentType(contentType).contentLength(size))).url();
    }

    public URL downloadUrl(String key, Duration ttl) {
        return presigner.presignGetObject(p -> p.signatureDuration(ttl).getObjectRequest(o -> o
                .bucket(props.bucket()).key(key))).url();
    }

    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(key).build());
            return true;
        } catch (S3Exception e) { // HEAD has no error body, so a missing key is only a 404 status
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }
}
