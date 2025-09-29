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
import com.netflix.spinnaker.kork.secrets.SecretReference;
import com.netflix.spinnaker.kork.secrets.user.OpaqueUserSecretData;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretData;
import com.netflix.spinnaker.kork.secrets.user.UserSecretManager;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadata;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadataField;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerde;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerdeFactory;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

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
    AWSSecretsManager client =
        AWSSecretsManagerClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    container.getEndpoint().toString(), container.getRegion()))
            .withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(container.getAccessKey(), container.getSecretKey())))
            .build();

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

    private static final DockerImageName DOCKER_IMAGE =
        DockerImageName.parse("localstack/localstack:0.11.3");

    @Bean(initMethod = "start", destroyMethod = "stop")
    public LocalStackContainer localStackContainer() {
      return new LocalStackContainer(DOCKER_IMAGE)
          .withServices(LocalStackContainer.Service.SECRETSMANAGER);
    }

    @Bean
    public ObjectMapper mapper() {
      return new ObjectMapper();
    }
  }

  @TestComponent
  @RequiredArgsConstructor
  public static class LocalstackClientProvider implements SecretsManagerClientProvider {
    private final LocalStackContainer container;

    @Override
    public AWSSecretsManager getClientForSecretParameters(Map<String, String> parameters) {
      return create();
    }

    @Override
    public AWSSecretsManager getClientForSecret(SecretReference reference) {
      return create();
    }

    private AWSSecretsManager create() {
      return AWSSecretsManagerClientBuilder.standard()
          .withEndpointConfiguration(
              new AwsClientBuilder.EndpointConfiguration(
                  container.getEndpoint().toString(), container.getRegion()))
          .withCredentials(
              new AWSStaticCredentialsProvider(
                  new BasicAWSCredentials(container.getAccessKey(), container.getSecretKey())))
          .build();
    }
  }
}
