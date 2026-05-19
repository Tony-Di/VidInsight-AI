package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.config.StorageProperties;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.LocalAccess;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3FileStorageServiceImpl implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".mp4", ".mov", ".avi", ".mkv", ".webm");
    private static final String VIDEO_URL_PREFIX = "/uploads/videos/";
    private static final String AUDIO_URL_PREFIX = "/uploads/audio/";
    private static final String VIDEO_KEY_PREFIX = "videos/";
    private static final String AUDIO_KEY_PREFIX = "audio/";
    private static final String CHUNK_KEY_PREFIX = "chunks/";

    private final StorageProperties storageProperties;

    private S3Client s3Client;
    /** Used internally by backend (signs against configured endpoint). */
    private S3Presigner internalPresigner;
    /** Used to generate URLs returned to the browser (signs against publicEndpoint, falling back to endpoint). */
    private S3Presigner publicPresigner;
    private String bucket;

    @PostConstruct
    public void init() {
        StorageProperties.S3 cfg = storageProperties.getS3();
        if (!StringUtils.hasText(cfg.getAccessKey()) || !StringUtils.hasText(cfg.getSecretKey())) {
            throw new IllegalStateException("app.storage.s3.access-key and secret-key are required when provider=s3");
        }
        this.bucket = cfg.getBucket();

        AwsBasicCredentials creds = AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey());
        StaticCredentialsProvider credsProvider = StaticCredentialsProvider.create(creds);
        Region region = Region.of(cfg.getRegion());
        S3Configuration s3Cfg = S3Configuration.builder().pathStyleAccessEnabled(cfg.isPathStyle()).build();

        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(region)
                .credentialsProvider(credsProvider)
                .httpClientBuilder(ApacheHttpClient.builder())
                .serviceConfiguration(s3Cfg);
        if (StringUtils.hasText(cfg.getEndpoint())) {
            clientBuilder.endpointOverride(URI.create(cfg.getEndpoint()));
        }
        this.s3Client = clientBuilder.build();

        this.internalPresigner = buildPresigner(credsProvider, region, s3Cfg, cfg.getEndpoint());
        String publicEndpoint = StringUtils.hasText(cfg.getPublicEndpoint())
                ? cfg.getPublicEndpoint() : cfg.getEndpoint();
        this.publicPresigner = buildPresigner(credsProvider, region, s3Cfg, publicEndpoint);

        ensureBucket();
        log.info("S3 storage initialized: endpoint={}, bucket={}, publicEndpoint={}",
                cfg.getEndpoint(), bucket, publicEndpoint);
    }

    private S3Presigner buildPresigner(StaticCredentialsProvider creds, Region region,
                                       S3Configuration s3Cfg, String endpoint) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(creds)
                .serviceConfiguration(s3Cfg);
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            log.info("Bucket {} not found, creating", bucket);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.info("Bucket {} not found (404), creating", bucket);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } else {
                throw e;
            }
        }
    }

    @Override
    public String saveVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("video file is required");
        }
        String originalFilename = file.getOriginalFilename() == null ? "video" : file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        validateExtension(extension);

        String storedFilename = UUID.randomUUID() + extension;
        String key = VIDEO_KEY_PREFIX + storedFilename;
        try (InputStream in = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket).key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(in, file.getSize())
            );
        } catch (IOException e) {
            throw new IllegalStateException("failed to upload video to S3", e);
        }
        return VIDEO_URL_PREFIX + storedFilename;
    }

    @Override
    public String saveVideo(Path file, String originalFilename) {
        if (file == null || !Files.exists(file)) {
            throw new IllegalArgumentException("video file is required");
        }
        String filename = StringUtils.hasText(originalFilename) ? originalFilename : file.getFileName().toString();
        String extension = getExtension(filename);
        validateExtension(extension);

        String storedFilename = UUID.randomUUID() + extension;
        String key = VIDEO_KEY_PREFIX + storedFilename;
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromFile(file)
        );
        return VIDEO_URL_PREFIX + storedFilename;
    }

    @Override
    public String saveAudio(Path file, String filename) {
        if (file == null || !Files.exists(file)) {
            throw new IllegalArgumentException("audio file is required");
        }
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("audio filename is required");
        }
        String key = AUDIO_KEY_PREFIX + filename;
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType("audio/mpeg").build(),
                RequestBody.fromFile(file)
        );
        return AUDIO_URL_PREFIX + filename;
    }

    @Override
    public void validateVideoFilename(String filename) {
        validateExtension(getExtension(filename));
    }

    @Override
    public boolean saveChunk(String uploadId, Integer chunkIndex, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("chunk file is required");
        }
        String key = CHUNK_KEY_PREFIX + uploadId + "/" + chunkIndex + ".part";
        boolean alreadyUploaded = objectExists(key);
        try (InputStream in = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromInputStream(in, file.getSize())
            );
        } catch (IOException e) {
            throw new IllegalStateException("failed to upload chunk to S3", e);
        }
        return alreadyUploaded;
    }

    @Override
    public String mergeChunks(String uploadId, String originalFilename, Integer totalChunks) {
        String extension = getExtension(originalFilename);
        validateExtension(extension);

        ensureAllChunksExist(uploadId, totalChunks);

        // Download all chunks to a single temp file, then upload the merged file as one object.
        // Simpler than S3 multipart UploadPartCopy and avoids the 5MB-per-part minimum constraint.
        Path tempMerged;
        try {
            tempMerged = Files.createTempFile("vid-merge-", extension);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create temp file for merge", e);
        }

        try {
            try (OutputStream out = Files.newOutputStream(tempMerged)) {
                for (int i = 0; i < totalChunks; i++) {
                    String chunkKey = CHUNK_KEY_PREFIX + uploadId + "/" + i + ".part";
                    try (InputStream in = s3Client.getObject(
                            GetObjectRequest.builder().bucket(bucket).key(chunkKey).build())) {
                        in.transferTo(out);
                    }
                }
            }

            String storedFilename = UUID.randomUUID() + extension;
            String key = VIDEO_KEY_PREFIX + storedFilename;
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromFile(tempMerged)
            );
            return VIDEO_URL_PREFIX + storedFilename;
        } catch (IOException e) {
            throw new IllegalStateException("failed to merge chunks from S3", e);
        } finally {
            try { Files.deleteIfExists(tempMerged); } catch (IOException ignored) {}
        }
    }

    @Override
    public void deleteChunks(String uploadId) {
        String prefix = CHUNK_KEY_PREFIX + uploadId + "/";
        List<ObjectIdentifier> toDelete = new ArrayList<>();
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(prefix);
            if (continuationToken != null) {
                reqBuilder.continuationToken(continuationToken);
            }
            ListObjectsV2Response resp = s3Client.listObjectsV2(reqBuilder.build());
            resp.contents().forEach(o -> toDelete.add(ObjectIdentifier.builder().key(o.key()).build()));
            continuationToken = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
        } while (continuationToken != null);

        if (toDelete.isEmpty()) {
            return;
        }
        // S3 deleteObjects caps at 1000 keys per request — chunked uploads won't hit that.
        s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(toDelete).build())
                .build());
    }

    @Override
    public void deleteFile(String sourceUrl) {
        String key = keyFromSourceUrlOrNull(sourceUrl);
        if (key == null) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new IllegalStateException("failed to delete object " + key, e);
        }
    }

    @Override
    public LocalAccess accessLocal(String sourceUrl) {
        String key = keyFromSourceUrlOrNull(sourceUrl);
        if (key == null) {
            throw new IllegalArgumentException("only internal uploaded files are supported");
        }
        String suffix = getExtension(sourceUrl);
        if (suffix.isEmpty()) {
            suffix = ".bin";
        }
        Path temp;
        try {
            temp = Files.createTempFile("vid-access-", suffix);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create temp file", e);
        }
        try (InputStream in = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchKeyException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw new IllegalArgumentException("object does not exist: " + key, e);
        } catch (IOException | SdkException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw new IllegalStateException("failed to download object " + key, e);
        }
        return LocalAccess.temp(temp);
    }

    @Override
    public String publicUrl(String sourceUrl) {
        if (sourceUrl == null) {
            return null;
        }
        // External URLs (e.g. videos created via /api/videos without import) pass through.
        if (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) {
            return sourceUrl;
        }
        String key = keyFromSourceUrlOrNull(sourceUrl);
        if (key == null) {
            return sourceUrl;
        }
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        GetObjectPresignRequest preReq = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(storageProperties.getS3().getPresignExpirationMinutes()))
                .getObjectRequest(get)
                .build();
        return publicPresigner.presignGetObject(preReq).url().toString();
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private void ensureAllChunksExist(String uploadId, Integer totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            String chunkKey = CHUNK_KEY_PREFIX + uploadId + "/" + i + ".part";
            if (!objectExists(chunkKey)) {
                throw new IllegalArgumentException("chunk " + i + " is missing");
            }
        }
    }

    private String keyFromSourceUrlOrNull(String sourceUrl) {
        if (sourceUrl == null) {
            return null;
        }
        if (sourceUrl.startsWith(VIDEO_URL_PREFIX)) {
            return VIDEO_KEY_PREFIX + sourceUrl.substring(VIDEO_URL_PREFIX.length());
        }
        if (sourceUrl.startsWith(AUDIO_URL_PREFIX)) {
            return AUDIO_KEY_PREFIX + sourceUrl.substring(AUDIO_URL_PREFIX.length());
        }
        return null;
    }

    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("unsupported video file type");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }
}
