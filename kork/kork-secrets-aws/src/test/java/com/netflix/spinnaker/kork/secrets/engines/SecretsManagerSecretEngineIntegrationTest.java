/*
 * Copyright 2022 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.secrets.engines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.secrets.SecretConfiguration;
import com.netflix.spinnaker.kork.secrets.user.OpaqueUserSecretData;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretData;
import com.netflix.spinnaker.kork.secrets.user.UserSecretManager;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadata;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadataField;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerde;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerdeFactory;
<<<<<<< HEAD
import java.nio.ByteBuffer;
=======
import java.net.URI;
>>>>>>> ad69f46 (test(aws): replace MinIO and LocalStack with MiniStack in AWS integration tests (#8010))
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ministack.testcontainers.MiniStackContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
<<<<<<< HEAD
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
=======
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.Tag;
>>>>>>> ad69f46 (test(aws): replace MinIO and LocalStack with MiniStack in AWS integration tests (#8010))

@SpringBootTest(classes = SecretConfiguration.class)
public class SecretsManagerSecretEngineIntegrationTest {

  @Autowired private MiniStackContainer container;

  // for setting up test data
  @Autowired private UserSecretSerdeFactory serdeFactory;

  @Autowired private UserSecretManager userSecretManager;

  @BeforeAll
  static void setupOnce() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
  }

  @Test
  public void canDecryptUserSecret() {
<<<<<<< HEAD
    AWSSecretsManager client =
        AWSSecretsManagerClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    container.getEndpoint().toString(), container.getRegion()))
            .withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(container.getAccessKey(), container.getSecretKey())))
            .build();
=======
    SecretsManagerClient client = buildMiniStackClient(container);
>>>>>>> ad69f46 (test(aws): replace MinIO and LocalStack with MiniStack in AWS integration tests (#8010))

    UserSecretMetadata metadata =
        UserSecretMetadata.builder()
            .type("opaque")
            .encoding("cbor")
            .roles(List.of("admin", "sre", "dev"))
            .build();
    UserSecretSerde serde = serdeFactory.serdeFor(metadata);

    Map<String, String> secretMap = Map.of("username", "blade", "password", "hunter2");
    UserSecretData data = new OpaqueUserSecretData(secretMap);
    ByteBuffer serializedSecretPayload = ByteBuffer.wrap(serde.serialize(data, metadata));

    client.createSecret(
        new CreateSecretRequest()
            .withName("my-user-secret")
            .withSecretBinary(serializedSecretPayload)
            .withTags(tagsForMetadata(metadata)));

    var baseRefUri =
        String.format("secret://secrets-manager?r=%s&s=my-user-secret", container.getRegion());
    UserSecretReference ref = UserSecretReference.parse(baseRefUri);
    UserSecret userSecret = userSecretManager.getUserSecret(ref);

    assertEquals(metadata.getType(), userSecret.getType());
    assertEquals(metadata.getEncoding(), userSecret.getEncoding());
    assertEquals(metadata.getRoles(), userSecret.getRoles());
    secretMap.forEach(
        (key, value) -> {
          var keyRef = UserSecretReference.parse(baseRefUri + "&k=" + key);
          assertEquals(value, userSecret.getSecretString(keyRef));
        });
  }

<<<<<<< HEAD
=======
  private static SecretsManagerClient buildMiniStackClient(MiniStackContainer container) {
    return SecretsManagerClient.builder()
        .endpointOverride(URI.create(container.getEndpoint()))
        .region(Region.of(container.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(container.getAccessKey(), container.getSecretKey())))
        .build();
  }

>>>>>>> ad69f46 (test(aws): replace MinIO and LocalStack with MiniStack in AWS integration tests (#8010))
  private static Collection<Tag> tagsForMetadata(UserSecretMetadata metadata) {
    return List.of(
        tagForField(UserSecretMetadataField.TYPE).withValue(metadata.getType()),
        tagForField(UserSecretMetadataField.ENCODING).withValue(metadata.getEncoding()),
        tagForField(UserSecretMetadataField.ROLES)
            .withValue(String.join(", ", metadata.getRoles())));
  }

  private static Tag tagForField(UserSecretMetadataField field) {
    return new Tag().withKey(field.getTagKey());
  }

  @TestConfiguration
  public static class IntegrationTestConfig {

    /**
     * Pinned deliberately: {@code MiniStackContainer}'s no-arg constructor resolves {@code latest},
     * and the emulator releases weekly, so the tag is the only thing that fixes the version this
     * test runs against.
     */
    private static final String MINISTACK_IMAGE_TAG = "1.5.10";

    @Bean(initMethod = "start", destroyMethod = "stop")
    public MiniStackContainer miniStackContainer() {
      return new MiniStackContainer(MINISTACK_IMAGE_TAG);
    }

    @Bean
<<<<<<< HEAD
    public SecretsManagerClientProvider localstackClientProvider(LocalStackContainer container) {
      return (params) ->
          AWSSecretsManagerClientBuilder.standard()
              .withEndpointConfiguration(
                  new AwsClientBuilder.EndpointConfiguration(
                      container.getEndpoint().toString(), container.getRegion()))
              .withCredentials(
                  new AWSStaticCredentialsProvider(
                      new BasicAWSCredentials(container.getAccessKey(), container.getSecretKey())))
              .build();
=======
    public SecretsManagerClientProvider miniStackClientProvider(MiniStackContainer container) {
      return (params) -> buildMiniStackClient(container);
>>>>>>> ad69f46 (test(aws): replace MinIO and LocalStack with MiniStack in AWS integration tests (#8010))
    }

    @Bean
    public ObjectMapper mapper() {
      return new ObjectMapper();
    }
  }
}
