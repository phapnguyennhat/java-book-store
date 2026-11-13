package com.example.book_store.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.s3.*}. Empty endpoint = real AWS (otherwise LocalStack, path-style); empty keys = the standard AWS
 * credentials chain (env, profile, IAM role).
 */
@ConfigurationProperties(prefix = "app.s3")
@Validated
record S3Properties(
        @DefaultValue("") String endpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @DefaultValue("") String accessKey,
        @DefaultValue("") String secretKey) {
}
