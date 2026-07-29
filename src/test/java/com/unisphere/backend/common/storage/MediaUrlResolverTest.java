package com.unisphere.backend.common.storage;

import com.unisphere.backend.config.ObjectStorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaUrlResolverTest {

    private static final String KEY    = "posts/9/photo.jpeg";
    private static final String SIGNED = "https://storage.example.com/posts/posts/9/photo.jpeg?X-Amz-Signature=abc";

    private MediaStorageClient storageClient;
    private MediaUrlResolver resolver;

    @BeforeEach
    void setUp() {
        storageClient = Mockito.mock(MediaStorageClient.class);

        ObjectStorageConfig config = new ObjectStorageConfig();
        config.setEndpoint("https://storage.example.com");
        config.setPresignGetExpiryMinutes(60);

        resolver = new MediaUrlResolver(storageClient, config);
    }

    @Test
    void signsAgainstTheConfiguredBucketAndExpiry() {
        when(storageClient.presignGetUrl(eq("posts"), eq(KEY), eq(60))).thenReturn(SIGNED);

        assertThat(resolver.toViewableUrl(KEY)).isEqualTo(SIGNED);
    }

    @Test
    void signsEveryCall_soAccessLossIsNotMaskedByASharedUrl() {
        when(storageClient.presignGetUrl(anyString(), eq(KEY), anyInt())).thenReturn(SIGNED);

        resolver.toViewableUrl(KEY);
        resolver.toViewableUrl(KEY);

        verify(storageClient, Mockito.times(2)).presignGetUrl(anyString(), eq(KEY), anyInt());
    }

    @Test
    void presignFailure_returnsNullRatherThanFailingTheWholeResponse() {
        when(storageClient.presignGetUrl(anyString(), eq(KEY), anyInt()))
                .thenThrow(new RuntimeException("signer failure"));

        assertThat(resolver.toViewableUrl(KEY)).isNull();
    }

    @Test
    void blankKeyAndAlreadyResolvedUrl_bypassStorageEntirely() {
        assertThat(resolver.toViewableUrl(null)).isNull();
        assertThat(resolver.toViewableUrl("  ")).isNull();
        assertThat(resolver.toViewableUrl("https://legacy.example.com/posts/old.jpg"))
                .isEqualTo("https://legacy.example.com/posts/old.jpg");
        verify(storageClient, never()).presignGetUrl(anyString(), anyString(), anyInt());
    }
}
