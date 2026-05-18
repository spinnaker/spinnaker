/*
 * Copyright 2026 Moderne, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.DescribeImagesRequest;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
import com.amazonaws.services.ecr.model.ImageDetail;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

final class EcrDockerTagResolverTest {

  private AmazonClientProvider amazonClientProvider;
  private CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;
  private AmazonECR ecr;
  private EcrDockerTagResolver target;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    amazonClientProvider = org.mockito.Mockito.mock(AmazonClientProvider.class);
    credentialsRepository = org.mockito.Mockito.mock(CredentialsRepository.class);
    ecr = org.mockito.Mockito.mock(AmazonECR.class);

    NetflixECSCredentials creds = stubCredentials("123456789012", "us-west-2");
    org.mockito.Mockito.when(credentialsRepository.getAll()).thenReturn(Set.of(creds));
    org.mockito.Mockito.when(
            amazonClientProvider.getAmazonEcr(
                ArgumentMatchers.any(),
                ArgumentMatchers.eq("us-west-2"),
                ArgumentMatchers.eq(false)))
        .thenReturn(ecr);

    target = new EcrDockerTagResolver(amazonClientProvider, credentialsRepository);
  }

  @Test
  void picksHighestStableSemverFromPeerTags() {
    stubDescribeImages(List.of("latest", "0.147.3", "0.146.0", "0.147.3-rc1"));
    EcrDockerTagResolver.ResolveResult result =
        target.resolve(
            "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:latest");
    assertThat(result.resolvedTag).isEqualTo("0.147.3");
    assertThat(result.resolvedReference)
        .isEqualTo(
            "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:0.147.3");
  }

  @Test
  void excludesPreReleaseTagsEvenWhenSolePeer() {
    stubDescribeImages(List.of("latest", "0.147.3-rc1", "0.147.3-SNAPSHOT-20260427-181523"));
    assertThatThrownBy(
            () ->
                target.resolve(
                    "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:latest"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("no peer tag matching stable semver");
  }

  @Test
  void onlyLatestTag_throws() {
    stubDescribeImages(List.of("latest"));
    assertThatThrownBy(
            () ->
                target.resolve(
                    "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:latest"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void noImageForTag_throws() {
    org.mockito.Mockito.when(ecr.describeImages(ArgumentMatchers.any(DescribeImagesRequest.class)))
        .thenReturn(new DescribeImagesResult());
    assertThatThrownBy(
            () ->
                target.resolve(
                    "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:latest"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("No ECR image found for tag latest");
  }

  @Test
  void compareSemverNumerically() {
    // Ensure 0.10.0 > 0.9.9 (numeric, not lexicographic)
    stubDescribeImages(List.of("latest", "0.9.9", "0.10.0"));
    EcrDockerTagResolver.ResolveResult result =
        target.resolve(
            "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:latest");
    assertThat(result.resolvedTag).isEqualTo("0.10.0");
  }

  @Test
  void invalidEcrReference_throws() {
    assertThatThrownBy(() -> target.resolve("docker.io/library/nginx:latest"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a valid tagged ECR URI");
  }

  // resolveByName — short-form references as stored by moderne-saas pipelines

  @Test
  void resolveByName_picksHighestStableSemver() {
    // Exact shape stored in metapipelines.libsonnet defaultArtifact: "moderne/repo:latest"
    stubDescribeImages(List.of("latest", "0.147.3", "0.146.0", "0.147.3-rc1"));
    EcrDockerTagResolver.ResolveResult result =
        target.resolveByName("moderne/recipe-worker-arm64", "latest");
    assertThat(result.resolvedTag).isEqualTo("0.147.3");
    // resolvedReference preserves the short form so repave stores a pinned short reference
    assertThat(result.resolvedReference).isEqualTo("moderne/recipe-worker-arm64:0.147.3");
  }

  @Test
  void resolveByName_noStableSemverPeer_throws() {
    stubDescribeImages(List.of("latest", "0.147.3-SNAPSHOT-20260427-181523"));
    assertThatThrownBy(() -> target.resolveByName("moderne/recipe-worker-arm64", "latest"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("no peer tag matching stable semver");
  }

  @Test
  void resolveByName_repositoryNotFound_throws() {
    org.mockito.Mockito.when(ecr.describeImages(ArgumentMatchers.any(DescribeImagesRequest.class)))
        .thenThrow(new com.amazonaws.services.ecr.model.RepositoryNotFoundException("not found"));
    assertThatThrownBy(() -> target.resolveByName("moderne/nonexistent", "latest"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("not found in any registered ECR account");
  }

  @Test
  void parsesReferenceComponents() {
    EcrDockerTagResolver.EcrReference parsed =
        EcrDockerTagResolver.EcrReference.parse(
            "123456789012.dkr.ecr.us-west-2.amazonaws.com/moderne/recipe-worker-arm64:latest");
    assertThat(parsed.accountId).isEqualTo("123456789012");
    assertThat(parsed.region).isEqualTo("us-west-2");
    assertThat(parsed.repository).isEqualTo("moderne/recipe-worker-arm64");
    assertThat(parsed.tag).isEqualTo("latest");
  }

  private void stubDescribeImages(List<String> tags) {
    DescribeImagesResult describe =
        new DescribeImagesResult()
            .withImageDetails(
                new ImageDetail().withImageDigest("sha256:abc123").withImageTags(tags));
    org.mockito.Mockito.when(ecr.describeImages(ArgumentMatchers.any(DescribeImagesRequest.class)))
        .thenReturn(describe);
  }

  private static NetflixECSCredentials stubCredentials(String accountId, String region) {
    NetflixECSCredentials credentials = org.mockito.Mockito.mock(NetflixECSCredentials.class);
    org.mockito.Mockito.when(credentials.getAccountId()).thenReturn(accountId);
    AmazonCredentials.AWSRegion awsRegion =
        org.mockito.Mockito.mock(AmazonCredentials.AWSRegion.class);
    org.mockito.Mockito.when(awsRegion.getName()).thenReturn(region);
    org.mockito.Mockito.when(credentials.getRegions()).thenReturn(List.of(awsRegion));
    return (NetflixECSCredentials) credentials;
  }
}
