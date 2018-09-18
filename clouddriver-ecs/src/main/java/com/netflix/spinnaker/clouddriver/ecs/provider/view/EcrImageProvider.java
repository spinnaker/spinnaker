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

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.DescribeImagesRequest;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
import com.amazonaws.services.ecr.model.ImageDetail;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsDockerImage;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class EcrImageProvider implements ImageRepositoryProvider {
  private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^([0-9]{12})");
  private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("\\/([a-z0-9._-]+)");
  private static final String IDENTIFIER_PATTERN = "(:([a-z0-9._-]+)|@(sha256:[0-9a-f]{64}))";
  private static final Pattern REGION_PATTERN = Pattern.compile("(\\w+-\\w+-\\d+)");
  static final Pattern ECR_REPOSITORY_URI_PATTERN = Pattern.compile(ACCOUNT_ID_PATTERN.toString() + "\\.dkr\\.ecr\\." +
    REGION_PATTERN.toString() + ".+" +
    REPOSITORY_NAME_PATTERN.toString() +
    IDENTIFIER_PATTERN);

  private final AmazonClientProvider amazonClientProvider;

  private final AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public EcrImageProvider(AmazonClientProvider amazonClientProvider,
                          AccountCredentialsProvider accountCredentialsProvider) {
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
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
    boolean isTag = !(identifier.startsWith("sha256:") && identifier.length() == ("sha256:".length() + 64));
    String region = extractAwsRegion(url);

    NetflixAmazonCredentials credentials = getCredentials(accountId);

    if (!isValidRegion(credentials, region)) {
      throw new IllegalArgumentException("The repository URI provided does not belong to a region that the credentials have access to or the region is not valid.");
    }

    AmazonECR amazonECR = amazonClientProvider.getAmazonEcr(credentials, region, false);

    List<ImageIdentifier> imageIds = getImageIdentifiers(amazonECR, accountId, repository, identifier, isTag);
    DescribeImagesResult imagesResult = amazonECR.describeImages(new DescribeImagesRequest().withRegistryId(accountId).withRepositoryName(repository).withImageIds(imageIds));

    // TODO - what is the user interface we want to have here?  We should discuss with Lars and Ethan from the community as this whole thing will undergo a big refactoring
    List<ImageDetail> imagesWithThisIdentifier = imagesResult.getImageDetails();

    if (imagesWithThisIdentifier.size() > 1) {
      throw new IllegalArgumentException("More than 1 image has this " + (isTag ? "tag" : "digest") + "!  This is currently not supported.");
    } else if (imagesWithThisIdentifier.size() == 0) {
      throw new IllegalArgumentException(String.format("No image with the " + (isTag ? "tag" : "digest") + " %s was found.", identifier));
    }

    ImageDetail matchedImage = imagesWithThisIdentifier.get(0);

    EcsDockerImage ecsDockerImage = new EcsDockerImage();
    ecsDockerImage.setRegion(region);
    ecsDockerImage.addAmiForRegion(region, matchedImage.getImageDigest());
    ecsDockerImage.setAttribute("creationDate", matchedImage.getImagePushedAt());
    ecsDockerImage.setImageName(buildFullDockerImageUrl(matchedImage.getImageDigest(),
      matchedImage.getRegistryId(),
      matchedImage.getRepositoryName(),
      region));

    return Collections.singletonList(ecsDockerImage);
  }

  private boolean imageFilter(ImageIdentifier imageIdentifier, String identifier, boolean isTag) {
    return isTag ?
      imageIdentifier.getImageTag() != null && imageIdentifier.getImageTag().equals(identifier) :
      imageIdentifier.getImageDigest().equals(identifier);
  }

  private NetflixAmazonCredentials getCredentials(String accountId) {
    for (AccountCredentials credentials : accountCredentialsProvider.getAll()) {
      if (credentials instanceof NetflixAmazonCredentials) {
        NetflixAmazonCredentials amazonCredentials = (NetflixAmazonCredentials) credentials;
        if (amazonCredentials.getAccountId().equals(accountId)) {
          return amazonCredentials;
        }
      }
    }
    throw new NotFoundException(String.format("AWS account %s was not found.  Please specify a valid account name", accountId));
  }

  private List<ImageIdentifier> getImageIdentifiers(AmazonECR ecr, String accountId, String repository, String identifier, boolean isTag) {
    List<ImageIdentifier> imageIdentifiers = new ArrayList<ImageIdentifier>();
    String token = null;

    ListImagesRequest request = new ListImagesRequest()
      .withRegistryId(accountId)
      .withRepositoryName(repository);

    do {
      ListImagesResult result = ecr.listImages(request);
      result.getImageIds().stream()
        .filter(imageId -> imageFilter(imageId, identifier, isTag))
        .forEachOrdered(imageIdentifiers::add);

      token = result.getNextToken();
      if (token != null) {
        request.setNextToken(token);
      }
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
    return extractString(ACCOUNT_ID_PATTERN, imageUrl, 1,
      "The repository URI provided does not contain a proper account ID.");
  }

  private String extractEcrRepositoryName(String imageUrl) {
    return extractString(REPOSITORY_NAME_PATTERN, imageUrl, 1,
      "The repository URI provided does not contain a proper repository name.");
  }

  private String extractAwsRegion(String imageUrl) {
    return extractString(REGION_PATTERN, imageUrl, 0,
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
      throw new IllegalArgumentException("The repository URI provided does not contain a proper tag or sha256 digest.");
    }
    return matcher.group(1).startsWith(":") ?
      matcher.group(2) :
      matcher.group(3);
  }

  private String buildFullDockerImageUrl(String imageDigest, String registryId, String repositoryName, String region) {
    return registryId + ".dkr.ecr." + region + ".amazonaws.com/" + repositoryName + "@" + imageDigest;
  }
}
