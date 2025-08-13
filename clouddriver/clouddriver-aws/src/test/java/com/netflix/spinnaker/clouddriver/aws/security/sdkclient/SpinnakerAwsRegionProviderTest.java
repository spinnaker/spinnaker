/*
 * Copyright 2022 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static uk.org.webcompere.systemstubs.resource.Resources.with;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

class SpinnakerAwsRegionProviderTest {

  /** See https://github.com/aws/amazon-ec2-metadata-mock/releases. */
  private static DockerImageName metadataMockImage =
      DockerImageName.parse("public.ecr.aws/aws-ec2/amazon-ec2-metadata-mock:v1.10.1");

  /**
   * From
   * https://github.com/aws/amazon-ec2-metadata-mock/blob/v1.10.1/docs/defaults.md#aemm-configuration
   */
  private static final int METADATA_MOCK_PORT = 1338;

  private static GenericContainer metadataMock =
      new GenericContainer(metadataMockImage).withExposedPorts(METADATA_MOCK_PORT);

  @BeforeAll
  static void setupOnce() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    metadataMock.start();
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  public void testProfileCredentialsProviderRegionFromCredentialsFile(@TempDir Path tempDir)
      throws Throwable {
    // Build a credentials file and tell the aws sdk where to look for it
    Path testPath = tempDir.resolve("profileInfo");
    String profileName = "myProfileName";
    String profileRegion = "myRegion";
    writeConfig(testPath, profileName, profileRegion);

    ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider(profileName);
    SpinnakerAwsRegionProvider spinnakerAwsRegionProvider =
        new SpinnakerAwsRegionProvider(credentialsProvider);

    // This is equivalent to having info in ~/.aws/credentials
    with(new EnvironmentVariables()
            .set("AWS_CREDENTIAL_PROFILES_FILE", testPath.toFile().getCanonicalPath())
            .set(
                "AWS_EC2_METADATA_SERVICE_ENDPOINT",
                "http://"
                    + metadataMock.getHost()
                    + ":"
                    + metadataMock.getMappedPort(METADATA_MOCK_PORT)))
        .execute(
            () -> {
              String region = spinnakerAwsRegionProvider.getRegion();
              // Turns out that if the region is in the credentials file, then the aws
              // the aws sdk doesn't see it.  That's why we mock the instance
              // metadata service, so there's something there even on a local
              // (i.e. not at AWS) machine, and so there's a consistent answer
              // if we are running in aws (e.g. in CI).
              //
              // us-east-1 comes from
              // https://github.com/aws/amazon-ec2-metadata-mock/blob/v1.10.1/pkg/config/defaults/aemm-metadata-default-values.json#L172
              // and it seems simpler to hard-code it here than to do whatever
              // dance is necessary to explicitly configure it ourselves.
              assertEquals("us-east-1", region);
            });
  }

  @Test
  public void testProfileCredentialsProviderRegionFromConfigFile(@TempDir Path tempDir)
      throws Throwable {
    // Build a config file and tell the aws sdk where to look for it
    Path testPath = tempDir.resolve("profileInfo");
    String profileName = "myProfileName";
    String profileRegion = "myRegion";
    writeConfig(testPath, profileName, profileRegion);

    ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider(profileName);
    SpinnakerAwsRegionProvider spinnakerAwsRegionProvider =
        new SpinnakerAwsRegionProvider(credentialsProvider);

    // This is equivalent to having info in ~/.aws/config
    with(new EnvironmentVariables().set("AWS_CONFIG_FILE", testPath.toFile().getCanonicalPath()))
        .execute(
            () -> {
              String region = spinnakerAwsRegionProvider.getRegion();
              // If the region is in the config file, the aws sdk sees it
              assertEquals(profileRegion, region);
            });
  }

  void writeConfig(Path path, String profileName, String region) throws IOException {
    String profileInfo = "[" + profileName + "]\nregion=" + region + "\n";
    byte[] bytes = profileInfo.getBytes(StandardCharsets.UTF_8);
    Files.write(path, bytes);
  }
}
