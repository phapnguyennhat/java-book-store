package com.example.book_store.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
class S3Config {
    private final S3Properties props;

    @Bean(destroyMethod = "close")
    S3Client s3Client() {
        var b = S3Client.builder().region(Region.of(props.region())).credentialsProvider(credentials())
                .serviceConfiguration(s3Config());
        if (!props.endpoint().isBlank()) b.endpointOverride(URI.create(props.endpoint()));
        return b.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner() {
        var b = S3Presigner.builder().region(Region.of(props.region())).credentialsProvider(credentials())
                .serviceConfiguration(s3Config());
        if (!props.endpoint().isBlank()) b.endpointOverride(URI.create(props.endpoint()));
        return b.build();
    }

    // LocalStack: path-style (http://localhost:4566/bucket/key), no bucket subdomains
    private S3Configuration s3Config() {
        return S3Configuration.builder().pathStyleAccessEnabled(!props.endpoint().isBlank()).build();
    }

    /** Explicit keys when given (LocalStack), otherwise the standard AWS chain (env, profile, IAM role). */
    private AwsCredentialsProvider credentials() {
        return props.accessKey().isBlank() ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    }
}
