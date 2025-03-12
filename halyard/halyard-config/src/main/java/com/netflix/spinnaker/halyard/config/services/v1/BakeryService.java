/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.node.HasImageProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfig's deployments.
 */
@Component
public class BakeryService {
  @Autowired private LookupService lookupService;

  @Autowired private ProviderService providerService;

  @Autowired private ValidateService validateService;

  public List<BaseImage> getAllBaseImages(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setBakeryDefaults()
            .withAnyBaseImage();

    List<BaseImage> matchingBaseImages =
        lookupService.getMatchingNodesOfType(filter, BaseImage.class);

    if (matchingBaseImages.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No base images could be found").build());
    } else {
      return matchingBaseImages;
    }
  }

  private BaseImage getBaseImage(NodeFilter filter, String baseImageName) {
    List<BaseImage> matchingBaseImages =
        lookupService.getMatchingNodesOfType(filter, BaseImage.class);

    switch (matchingBaseImages.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No base image with name \"" + baseImageName + "\" was found")
                .setRemediation(
                    "Check if this base image was defined in another provider, or create a new one")
                .build());
      case 1:
        return matchingBaseImages.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "More than one base image named \"" + baseImageName + "\" was found")
                .setRemediation(
                    "Manually delete/rename duplicate base images with name \""
                        + baseImageName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public BakeryDefaults getBakeryDefaults(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setBakeryDefaults();

    List<BakeryDefaults> matching =
        lookupService.getMatchingNodesOfType(filter, BakeryDefaults.class);

    switch (matching.size()) {
      case 0:
        HasImageProvider provider =
            providerService.getHasImageProvider(deploymentName, providerName);
        BakeryDefaults bakeryDefaults = provider.emptyBakeryDefaults();
        setBakeryDefaults(deploymentName, providerName, bakeryDefaults);
        return bakeryDefaults;
      case 1:
        return matching.get(0);
      default:
        throw new RuntimeException(
            "It shouldn't be possible to have multiple bakeryDefaults nodes. This is a bug.");
    }
  }

  public void setBakeryDefaults(
      String deploymentName, String providerName, BakeryDefaults newBakeryDefaults) {
    HasImageProvider provider = providerService.getHasImageProvider(deploymentName, providerName);
    provider.setBakeryDefaults(newBakeryDefaults);
  }

  public BaseImage getProviderBaseImage(
      String deploymentName, String providerName, String baseImageName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setBaseImage(baseImageName);
    return getBaseImage(filter, baseImageName);
  }

  public BaseImage getAnyProviderBaseImage(String deploymentName, String baseImageName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .withAnyProvider()
            .setBaseImage(baseImageName);
    return getBaseImage(filter, baseImageName);
  }

  public void setBaseImage(
      String deploymentName, String providerName, String baseImageName, BaseImage newBaseImage) {
    BakeryDefaults bakeryDefaults = getBakeryDefaults(deploymentName, providerName);

    for (int i = 0; i < bakeryDefaults.getBaseImages().size(); i++) {
      BaseImage baseImage = (BaseImage) bakeryDefaults.getBaseImages().get(i);
      if (baseImage.getNodeName().equals(baseImageName)) {
        bakeryDefaults.getBaseImages().set(i, newBaseImage);
        return;
      }
    }

    throw new HalException(
        new ConfigProblemBuilder(Severity.FATAL, "BaseImage \"" + baseImageName + "\" wasn't found")
            .build());
  }

  public void deleteBaseImage(
      String deploymentName, String bakeryDefaultsName, String baseImageId) {
    BakeryDefaults bakeryDefaults = getBakeryDefaults(deploymentName, bakeryDefaultsName);
    boolean removed =
        bakeryDefaults
            .getBaseImages()
            .removeIf(
                baseImage -> ((BaseImage) baseImage).getBaseImage().getId().equals(baseImageId));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL, "BaseImage \"" + baseImageId + "\" wasn't found")
              .build());
    }
  }

  public void addBaseImage(
      String deploymentName, String bakeryDefaultsName, BaseImage newBaseImage) {
    BakeryDefaults bakeryDefaults = getBakeryDefaults(deploymentName, bakeryDefaultsName);
    bakeryDefaults.getBaseImages().add(newBaseImage);
  }

  public ProblemSet validateBakeryDefaults(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setBakeryDefaults();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateBaseImage(
      String deploymentName, String providerName, String baseImageName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .setBaseImage(baseImageName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllBaseImages(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setProvider(providerName).withAnyBaseImage();
    return validateService.validateMatchingFilter(filter);
  }
}
