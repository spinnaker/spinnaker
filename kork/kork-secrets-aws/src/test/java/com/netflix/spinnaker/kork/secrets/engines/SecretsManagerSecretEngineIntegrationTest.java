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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.Tag;

@SpringBootTest(classes = SecretConfiguration.class)
public class SecretsManagerSecretEngineIntegrationTest {

  @Autowired private LocalStackContainer container;

  // for setting up test data
  @Autowired private UserSecretSerdeFactory serdeFactory;

  @Autowired private UserSecretManager userSecretManager;

  @BeforeAll
  static void setupOnce() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
  }

  @Test
  public void canDecryptUserSecret() {
    SecretsManagerClient client = buildLocalstackClient(container);

    UserSecretMetadata metadata =
        UserSecretMetadata.builder()
            .type("opaque")
            .encoding("cbor")
            .roles(List.of("admin", "sre", "dev"))
            .build();
    UserSecretSerde serde = serdeFactory.serdeFor(metadata);

    Map<String, String> secretMap = Map.of("username", "blade", "password", "hunter2");
    UserSecretData data = new OpaqueUserSecretData(secretMap);
    SdkBytes serializedSecretPayload = SdkBytes.fromByteArray(serde.serialize(data, metadata));

    client.createSecret(
        CreateSecretRequest.builder()
            .name("my-user-secret")
            .secretBinary(serializedSecretPayload)
            .tags(tagsForMetadata(metadata))
            .build());

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

  private static SecretsManagerClient buildLocalstackClient(LocalStackContainer container) {
    return SecretsManagerClient.builder()
        .endpointOverride(container.getEndpoint())
        .region(Region.of(container.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(container.getAccessKey(), container.getSecretKey())))
        .build();
  }

  private static Collection<Tag> tagsForMetadata(UserSecretMetadata metadata) {
    return List.of(
        tagForField(UserSecretMetadataField.TYPE, metadata.getType()),
        tagForField(UserSecretMetadataField.ENCODING, metadata.getEncoding()),
        tagForField(UserSecretMetadataField.ROLES, String.join(", ", metadata.getRoles())));
  }

  private static Tag tagForField(UserSecretMetadataField field, String value) {
    return Tag.builder().key(field.getTagKey()).value(value).build();
  }

  @TestConfiguration
  public static class IntegrationTestConfig {

    private static final DockerImageName DOCKER_IMAGE =
        DockerImageName.parse("localstack/localstack:0.11.3");

    @Bean(initMethod = "start", destroyMethod = "stop")
    public LocalStackContainer localStackContainer() {
      return new LocalStackContainer(DOCKER_IMAGE)
          .withServices(LocalStackContainer.Service.SECRETSMANAGER);
    }

    @Bean
    public SecretsManagerClientProvider localstackClientProvider(LocalStackContainer container) {
      return (params) -> buildLocalstackClient(container);
    }

    @Bean
    public ObjectMapper mapper() {
      return new ObjectMapper();
    }
  }
}
