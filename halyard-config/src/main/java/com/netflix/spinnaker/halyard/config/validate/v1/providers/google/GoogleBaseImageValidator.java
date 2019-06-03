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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.google;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.ImageList;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.IOException;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

@EqualsAndHashCode(callSuper = false)
@Data
public class GoogleBaseImageValidator extends Validator<GoogleBaseImage> {
  private static final List<String> baseImageProjects =
      Lists.newArrayList(
          "centos-cloud",
          "coreos-cloud",
          "debian-cloud",
          "opensuse-cloud",
          "rhel-cloud",
          "suse-cloud",
          "ubuntu-os-cloud",
          "windows-cloud");
  private final List<GoogleNamedAccountCredentials> credentialsList;

  private final String halyardVersion;

  @Override
  public void validate(ConfigProblemSetBuilder p, GoogleBaseImage n) {
    String sourceImage = n.getVirtualizationSettings().getSourceImage();
    String sourceImageFamily = n.getVirtualizationSettings().getSourceImageFamily();

    if (StringUtils.isEmpty(sourceImage) && StringUtils.isEmpty(sourceImageFamily)) {
      p.addProblem(
          Problem.Severity.ERROR,
          "Either source image or source image family must be specified for "
              + n.getBaseImage().getId()
              + ".");
    }

    if (!StringUtils.isEmpty(sourceImage)) {
      int i = 0;
      boolean[] foundSourceImageHolder = new boolean[1];

      while (!foundSourceImageHolder[0] && i < credentialsList.size()) {
        GoogleNamedAccountCredentials credentials = credentialsList.get(i);
        List<String> imageProjects = Lists.newArrayList(credentials.getProject());

        imageProjects.addAll(credentials.getImageProjects());
        imageProjects.addAll(baseImageProjects);

        Compute compute = credentials.getCompute();
        BatchRequest imageListBatch = buildBatchRequest(compute);
        JsonBatchCallback<ImageList> imageListCallback =
            new JsonBatchCallback<ImageList>() {
              @Override
              public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders)
                  throws IOException {
                p.addProblem(
                    Problem.Severity.ERROR,
                    "Error locating "
                        + sourceImage
                        + " in these projects: "
                        + imageProjects
                        + ": "
                        + e.getMessage()
                        + ".");
              }

              @Override
              public void onSuccess(ImageList imageList, HttpHeaders responseHeaders)
                  throws IOException {
                // No need to look through these images if the requested image was already found.
                if (!foundSourceImageHolder[0]) {
                  if (imageList.getItems() != null) {
                    foundSourceImageHolder[0] =
                        imageList.getItems().stream()
                            .filter(image -> image.getName().equals(sourceImage))
                            .findFirst()
                            .isPresent();
                  }
                }
              }
            };

        try {
          for (String imageProject : imageProjects) {
            compute.images().list(imageProject).queue(imageListBatch, imageListCallback);
          }

          imageListBatch.execute();
        } catch (IOException e) {
          p.addProblem(
              Problem.Severity.ERROR,
              "Error locating "
                  + sourceImage
                  + " in these projects: "
                  + imageProjects
                  + ": "
                  + e.getMessage()
                  + ".");
        }

        i++;
      }

      if (!foundSourceImageHolder[0]) {
        p.addProblem(
            Problem.Severity.ERROR,
            "Image " + sourceImage + " not found via any configured google account.");
      }
    }

    if (!StringUtils.isEmpty(sourceImageFamily)) {
      int i = 0;
      boolean[] foundSourceImageFamilyHolder = new boolean[1];

      while (!foundSourceImageFamilyHolder[0] && i < credentialsList.size()) {
        GoogleNamedAccountCredentials credentials = credentialsList.get(i);
        List<String> imageProjects = Lists.newArrayList(credentials.getProject());

        imageProjects.addAll(credentials.getImageProjects());
        imageProjects.addAll(baseImageProjects);

        Compute compute = credentials.getCompute();
        BatchRequest imageListBatch = buildBatchRequest(compute);
        JsonBatchCallback<ImageList> imageListCallback =
            new JsonBatchCallback<ImageList>() {
              @Override
              public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders)
                  throws IOException {
                p.addProblem(
                    Problem.Severity.ERROR,
                    "Error locating "
                        + sourceImageFamily
                        + " in these projects: "
                        + imageProjects
                        + ": "
                        + e.getMessage()
                        + ".");
              }

              @Override
              public void onSuccess(ImageList imageList, HttpHeaders responseHeaders)
                  throws IOException {
                // No need to look through these images if the requested image family was already
                // found.
                if (!foundSourceImageFamilyHolder[0]) {
                  if (imageList.getItems() != null) {
                    foundSourceImageFamilyHolder[0] =
                        imageList.getItems().stream()
                            .filter(image -> sourceImageFamily.equals(image.getFamily()))
                            .findFirst()
                            .isPresent();
                  }
                }
              }
            };

        try {
          for (String imageProject : imageProjects) {
            compute.images().list(imageProject).queue(imageListBatch, imageListCallback);
          }

          imageListBatch.execute();
        } catch (IOException e) {
          p.addProblem(
              Problem.Severity.ERROR,
              "Error locating "
                  + sourceImageFamily
                  + " in these projects: "
                  + imageProjects
                  + ": "
                  + e.getMessage()
                  + ".");
        }

        i++;
      }

      if (!foundSourceImageFamilyHolder[0]) {
        p.addProblem(
            Problem.Severity.ERROR,
            "Image family " + sourceImageFamily + " not found via any configured google account.");
      }
    }

    if (StringUtils.isEmpty(n.getBaseImage().getPackageType())) {
      p.addProblem(
          Problem.Severity.ERROR,
          "Package type must be specified for " + n.getBaseImage().getId() + ".");
    }
  }

  private BatchRequest buildBatchRequest(Compute compute) {
    return compute.batch(
        (HttpRequest request) -> {
          request.getHeaders().setUserAgent("halyard " + halyardVersion);
        });
  }
}
