package com.videoinsight.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** local | s3 */
    private String provider = "local";

    private S3 s3 = new S3();

    @Data
    public static class S3 {
        /** Endpoint override. Leave blank for real AWS S3. Set to http://localhost:9000 for MinIO. */
        private String endpoint;
        private String region = "us-east-1";
        private String accessKey;
        private String secretKey;
        private String bucket = "vidinsight";
        /** MinIO requires path-style addressing; AWS S3 supports both. */
        private boolean pathStyle = true;
        /** Presigned GET URL expiration in minutes. */
        private int presignExpirationMinutes = 60;
        /**
         * If non-blank, replaces the host of the presigned URL — needed when MinIO runs in docker
         * with a hostname unreachable from the browser (e.g. internal "minio:9000" → public "localhost:9000").
         */
        private String publicEndpoint;
    }
}
