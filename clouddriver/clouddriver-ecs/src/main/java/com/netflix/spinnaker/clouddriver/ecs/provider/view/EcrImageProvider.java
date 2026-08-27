/*
 * Copyright 2017 Lookout, Inc.
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

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsDockerImage;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ecr.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ecr.model.ImageDetail;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesRequest;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;

@Component
public class EcrImageProvider implements ImageRepositoryProvider {
  private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^([0-9]{12})");
  private static final Pattern REPOSITORY_NAME_PATTERN =
      Pattern.compile(
          "\\/(((?:[a-z0-9]+(?:[._-][a-z0-9]+)*\\/)*[a-z0-9]+(?:[._-][a-z0-9]+)*){2,})");
  private static final String IDENTIFIER_PATTERN = "(:([a-zA-Z0-9._-]+)|@(sha256:[0-9a-f]{64}))";
  private static final Pattern REGION_PATTERN = Pattern.compile("(\\w+-\\w+-\\d+)");
  static final Pattern ECR_REPOSITORY_URI_PATTERN =
      Pattern.compile(
          ACCOUNT_ID_PATTERN.toString()
              + "\\.dkr\\.ecr\\."
              + REGION_PATTERN.toString()
              + ".+?"
              + REPOSITORY_NAME_PATTERN.toString()
              + IDENTIFIER_PATTERN);

  private final AmazonClientProvider amazonClientProvider;

  private final CredentialsRepository<NetflixECSCredentials> credentialsRepository;

  @Autowired
  public EcrImageProvider(
      AmazonClientProvider amazonClientProvider,
      CredentialsRepository<NetflixECSCredentials> credentialsRepository) {
    this.amazonClientProvider = amazonClientProvider;
    this.credentialsRepository = credentialsRepository;
  }

  @Override
  public String getRepositoryName() {
    return "ECR";
  }

  @Override
  public boolean handles(String url) {
    return isValidEcrUrl(url);
  }

  @Override
  public List<EcsDockerImage> findImage(String url) {
    // HTTP(S) part is not needed.
    url = url.replace("http://", "").replace("https://", "");

    String accountId = extractAwsAccountId(url);
    String repository = extractEcrRepositoryName(url);
    String identifier = extractEcrIdentifier(repository, url);
    boolean isTag =
        !(identifier.startsWith("sha256:") && identifier.length() == ("sha256:".length() + 64));
    String region = extractAwsRegion(url);

    NetflixAmazonCredentials credentials = getCredentials(accountId, region);

    if (!isValidRegion(credentials, region)) {
      throw new IllegalArgumentException(
          "The repository URI provided does not belong to a region that the credentials have access to or the region is not valid.");
    }

    EcrClient amazonECR = amazonClientProvider.getAmazonEcrV2(credentials, region);

    List<ImageIdentifier> imageIds =
        getImageIdentifiers(amazonECR, accountId, repository, identifier, isTag);
    DescribeImagesResponse imagesResult =
        amazonECR.describeImages(
            DescribeImagesRequest.builder()
                .registryId(accountId)
                .repositoryName(repository)
                .imageIds(imageIds)
                .build());

    // TODO - what is the user interface we want to have here?  We should discuss with Lars and
    // Ethan from the community as this whole thing will undergo a big refactoring
    List<ImageDetail> imagesWithThisIdentifier = imagesResult.imageDetails();

    if (imagesWithThisIdentifier.size() > 1) {
      throw new IllegalArgumentException(
          "More than 1 image has this "
              + (isTag ? "tag" : "digest")
              + "!  This is currently not supported.");
    } else if (imagesWithThisIdentifier.size() == 0) {
      throw new IllegalArgumentException(
          String.format(
              "No image with the " + (isTag ? "tag" : "digest") + " %s was found.", identifier));
    }

    ImageDetail matchedImage = imagesWithThisIdentifier.get(0);

    EcsDockerImage ecsDockerImage = new EcsDockerImage();
    ecsDockerImage.setRegion(region);
    ecsDockerImage.addAmiForRegion(region, matchedImage.imageDigest());
    ecsDockerImage.setAttribute("creationDate", matchedImage.imagePushedAt());
    ecsDockerImage.setImageName(
        buildFullDockerImageUrl(
            matchedImage.imageDigest(),
            matchedImage.registryId(),
            matchedImage.repositoryName(),
            region));

    return Collections.singletonList(ecsDockerImage);
  }

  private boolean imageFilter(ImageIdentifier imageIdentifier, String identifier, boolean isTag) {
    return isTag
        ? imageIdentifier.imageTag() != null && imageIdentifier.imageTag().equals(identifier)
        : imageIdentifier.imageDigest().equals(identifier);
  }

  private NetflixAmazonCredentials getCredentials(String accountId, String region) {

    for (NetflixECSCredentials credentials : credentialsRepository.getAll()) {
      if (credentials.getAccountId().equals(accountId)
          && (credentials.getRegions().isEmpty()
              || credentials.getRegions().stream()
                  .anyMatch(oneRegion -> oneRegion.getName().equals(region)))) {
        return credentials;
      }
    }
    throw new NotFoundException(
        String.format(
            "AWS account %s with region %s was not found.  Please specify a valid account name and region",
            accountId, region));
  }

  private List<ImageIdentifier> getImageIdentifiers(
      EcrClient ecr, String accountId, String repository, String identifier, boolean isTag) {
    List<ImageIdentifier> imageIdentifiers = new ArrayList<ImageIdentifier>();
    String token = null;

    do {
      ListImagesRequest.Builder requestBuilder =
          ListImagesRequest.builder().registryId(accountId).repositoryName(repository);
      if (token != null) {
        requestBuilder.nextToken(token);
      }
      ListImagesResponse result = ecr.listImages(requestBuilder.build());
      result.imageIds().stream()
          .filter(imageId -> imageFilter(imageId, identifier, isTag))
          .forEachOrdered(imageIdentifiers::add);

      token = result.nextToken();
    } while (token != null);

    return imageIdentifiers;
  }

  private boolean isValidRegion(NetflixAmazonCredentials credentials, String region) {
    return credentials.getRegions().stream()
        .map(AmazonCredentials.AWSRegion::getName)
        .anyMatch(region::equals);
  }

  private boolean isValidEcrUrl(String imageUrl) {
    imageUrl = imageUrl.replace("http://", "").replace("https://", "");
    Matcher matcher = ECR_REPOSITORY_URI_PATTERN.matcher(imageUrl);
    return matcher.find();
  }

  private String extractAwsAccountId(String imageUrl) {
    return extractString(
        ACCOUNT_ID_PATTERN,
        imageUrl,
        1,
        "The repository URI provided does not contain a proper account ID.");
  }

  private String extractEcrRepositoryName(String imageUrl) {
    return extractString(
        REPOSITORY_NAME_PATTERN,
        imageUrl,
        1,
        "The repository URI provided does not contain a proper repository name.");
  }

  private String extractAwsRegion(String imageUrl) {
    return extractString(
        REGION_PATTERN,
        imageUrl,
        0,
        "The repository URI provided does not contain a proper region.");
  }

  private String extractString(Pattern pattern, String imageUrl, int group, String error) {
    Matcher matcher = pattern.matcher(imageUrl);
    if (!matcher.find()) {
      throw new IllegalArgumentException(error);
    }
    return matcher.group(group);
  }

  private String extractEcrIdentifier(String repository, String imageUrl) {
    final Pattern identifierPatter = Pattern.compile(repository + IDENTIFIER_PATTERN);
    Matcher matcher = identifierPatter.matcher(imageUrl);
    if (!matcher.find()) {
      throw new IllegalArgumentException(
          "The repository URI provided does not contain a proper tag or sha256 digest.");
    }
    return matcher.group(1).startsWith(":") ? matcher.group(2) : matcher.group(3);
  }

  private String buildFullDockerImageUrl(
      String imageDigest, String registryId, String repositoryName, String region) {
    return registryId
        + ".dkr.ecr."
        + region
        + ".amazonaws.com/"
        + repositoryName
        + "@"
        + imageDigest;
  }
}
