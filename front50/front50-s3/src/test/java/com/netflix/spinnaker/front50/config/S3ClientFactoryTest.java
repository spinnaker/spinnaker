package com.netflix.spinnaker.front50.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;

public class S3ClientFactoryTest {

  @Test
  void create_shouldNotThrowSdkClientException_whenRegionIsProvidedWithEndpoint() {
    AwsCredentialsProvider mockProvider = mock(AwsCredentialsProvider.class);
    S3Properties props = new S3MetadataStorageProperties();
    props.setRegion("us-west-2");
    props.setEndpoint("https://minio-host:9000");
    // FIXME: this should not throw an exception
    assertThatThrownBy(() -> S3ClientFactory.create(mockProvider, props))
        .isInstanceOf(SdkClientException.class)
        .hasMessageContaining(
            "Unable to load region from system settings. Region must be specified either via environment variable (AWS_REGION) or  system property (aws.region)");
  }
}
