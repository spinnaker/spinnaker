package com.netflix.spinnaker.front50.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

public class S3ClientFactoryTest {

  @Test
  void create_shouldNotThrowSdkClientException_whenRegionIsProvidedWithEndpoint() {
    AwsCredentialsProvider mockProvider = mock(AwsCredentialsProvider.class);
    S3Properties props = new S3MetadataStorageProperties();
    props.setRegion("us-west-2");
    props.setEndpoint("https://minio-host:9000");
    S3Client client = S3ClientFactory.create(mockProvider, props);
    assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("us-west-2");
  }
}
