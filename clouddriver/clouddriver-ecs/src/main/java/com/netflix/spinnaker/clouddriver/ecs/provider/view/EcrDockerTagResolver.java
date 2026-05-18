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

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.DescribeImagesRequest;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
import com.amazonaws.services.ecr.model.ImageDetail;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Given a fully-qualified ECR image reference whose tag is a moving alias (e.g. {@code latest}),
 * looks up the underlying image digest and returns the highest stable semver tag that shares that
 * digest. "Stable semver" here matches {@code N.N.N} (no pre-release suffix), which lines up with
 * the format that jib publishes for non-SNAPSHOT releases.
 *
 * <p>Throws on no peer match (caller must not silently fall through; that reintroduces the bug this
 * class exists to fix).
 */
@Component
public class EcrDockerTagResolver {
  private static final Pattern STABLE_SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

  private final AmazonClientProvider amazonClientProvider;
  private final CredentialsRepository<NetflixECSCredentials> credentialsRepository;

  @Autowired
  public EcrDockerTagResolver(
      AmazonClientProvider amazonClientProvider,
      CredentialsRepository<NetflixECSCredentials> credentialsRepository) {
    this.amazonClientProvider = amazonClientProvider;
    this.credentialsRepository = credentialsRepository;
  }

  public ResolveResult resolve(String reference) {
    EcrReference parsed = EcrReference.parse(reference);
    NetflixAmazonCredentials credentials = lookupCredentials(parsed.accountId, parsed.region);
    if (!isValidRegion(credentials, parsed.region)) {
      throw new IllegalArgumentException(
          "ECR reference "
              + reference
              + " uses region "
              + parsed.region
              + " which is not enabled on the matched credentials.");
    }

    AmazonECR ecr = amazonClientProvider.getAmazonEcr(credentials, parsed.region, false);
    ImageDetail detail = describeImage(ecr, parsed.accountId, parsed.repository, parsed.tag);
    return resolveFromDetail(detail, reference, parsed.tag);
  }

  /**
   * Resolves a short-form {@code repository:tag} reference (e.g. {@code
   * moderne/recipe-worker-arm64:latest}) by scanning all registered ECR credentials until one that
   * holds the repository is found. The returned {@link ResolveResult#resolvedReference} uses the
   * same short form so downstream pipeline stages store a pinned short reference rather than a full
   * URI.
   */
  public ResolveResult resolveByName(String repository, String tag) {
    List<String> tried = new ArrayList<>();
    for (NetflixECSCredentials credentials : credentialsRepository.getAll()) {
      for (AmazonCredentials.AWSRegion awsRegion : credentials.getRegions()) {
        String region = awsRegion.getName();
        try {
          AmazonECR ecr = amazonClientProvider.getAmazonEcr(credentials, region, false);
          ImageDetail detail = describeImage(ecr, null, repository, tag);
          return resolveFromDetail(detail, repository + ":" + tag, tag);
        } catch (com.amazonaws.services.ecr.model.RepositoryNotFoundException ignored) {
          tried.add(credentials.getAccountId() + "/" + region);
        }
      }
    }
    throw new NotFoundException(
        "Repository " + repository + " not found in any registered ECR account; tried: " + tried);
  }

  private static ImageDetail describeImage(
      AmazonECR ecr, String registryId, String repository, String tag) {
    DescribeImagesRequest request =
        new DescribeImagesRequest()
            .withRepositoryName(repository)
            .withImageIds(new ImageIdentifier().withImageTag(tag));
    if (registryId != null) {
      request.withRegistryId(registryId);
    }
    DescribeImagesResult result = ecr.describeImages(request);
    if (result.getImageDetails() == null || result.getImageDetails().isEmpty()) {
      throw new NotFoundException(
          "No ECR image found for tag " + tag + " in repository " + repository);
    }
    return result.getImageDetails().get(0);
  }

  private static ResolveResult resolveFromDetail(
      ImageDetail detail, String originalReference, String originalTag) {
    List<String> peerTags =
        Optional.ofNullable(detail.getImageTags()).orElseGet(Collections::emptyList);

    Optional<String> resolved =
        peerTags.stream()
            .filter(t -> !originalTag.equals(t))
            .filter(t -> STABLE_SEMVER.matcher(t).matches())
            .max(EcrDockerTagResolver::compareSemver);

    if (resolved.isPresent()) {
      return new ResolveResult(
          resolved.get(), rewriteReference(originalReference, originalTag, resolved.get()));
    }

    throw new NotFoundException(
        "ECR image "
            + originalReference
            + " (digest "
            + detail.getImageDigest()
            + ") has no peer tag matching stable semver "
            + STABLE_SEMVER.pattern()
            + "; peer tags were "
            + peerTags);
  }

  private NetflixAmazonCredentials lookupCredentials(String accountId, String region) {
    for (NetflixECSCredentials credentials : credentialsRepository.getAll()) {
      if (credentials.getAccountId().equals(accountId)
          && (credentials.getRegions().isEmpty()
              || credentials.getRegions().stream().anyMatch(r -> r.getName().equals(region)))) {
        return credentials;
      }
    }
    throw new NotFoundException(
        "No AWS credentials match ECR account " + accountId + " in region " + region);
  }

  private static boolean isValidRegion(NetflixAmazonCredentials credentials, String region) {
    return credentials.getRegions().stream()
        .map(AmazonCredentials.AWSRegion::getName)
        .anyMatch(region::equals);
  }

  private static String rewriteReference(String reference, String oldTag, String newTag) {
    int colon = reference.lastIndexOf(':' + oldTag);
    if (colon < 0) {
      return reference + ":" + newTag;
    }
    return reference.substring(0, colon) + ":" + newTag;
  }

  static int compareSemver(String left, String right) {
    String[] leftParts = left.split("\\.");
    String[] rightParts = right.split("\\.");
    int len = Math.min(leftParts.length, rightParts.length);
    for (int i = 0; i < len; i++) {
      int cmp = Integer.compare(Integer.parseInt(leftParts[i]), Integer.parseInt(rightParts[i]));
      if (cmp != 0) {
        return cmp;
      }
    }
    return Integer.compare(leftParts.length, rightParts.length);
  }

  public static final class ResolveResult {
    public final String resolvedTag;
    public final String resolvedReference;

    ResolveResult(String resolvedTag, String resolvedReference) {
      this.resolvedTag = resolvedTag;
      this.resolvedReference = resolvedReference;
    }
  }

  static final class EcrReference {
    final String accountId;
    final String region;
    final String repository;
    final String tag;

    private EcrReference(String accountId, String region, String repository, String tag) {
      this.accountId = accountId;
      this.region = region;
      this.repository = repository;
      this.tag = tag;
    }

    private static final Pattern PATTERN =
        Pattern.compile(
            "^(?:https?://)?(\\d{12})\\.dkr\\.ecr\\.([a-z0-9-]+)\\.amazonaws\\.com/([^:]+):(.+)$");

    static EcrReference parse(String reference) {
      Matcher m = PATTERN.matcher(reference);
      if (!m.matches()) {
        throw new IllegalArgumentException("Reference is not a valid tagged ECR URI: " + reference);
      }
      return new EcrReference(m.group(1), m.group(2), m.group(3), m.group(4));
    }
  }
}
