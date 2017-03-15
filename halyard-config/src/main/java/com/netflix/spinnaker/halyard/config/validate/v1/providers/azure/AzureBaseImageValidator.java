/*
 * Copyright 2017 Microsoft, Inc.
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
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers.azure;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.compute.VirtualMachineImagesOperations;
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureBaseImage;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Data
public class AzureBaseImageValidator extends Validator<AzureBaseImage> {
  final private List<AzureCredentials> credentialsList;

  @Override
  public void validate(ConfigProblemSetBuilder p, AzureBaseImage n) {

    AzureCredentials credentials = credentialsList.get(0); // The first credentials should be fine since we're validating against public images
    VirtualMachineImagesOperations imagesOps = credentials.getComputeClient().getVmImageOps();
    AzureBaseImage.AzureOperatingSystemSettings osSettings = n.getBaseImage();
    String version = osSettings.getVersion();

    try {
      if (version != null && version.equals("latest")) {
        imagesOps.list("westus", osSettings.getPublisher(), osSettings.getOffer(), osSettings.getSku(), null, 1, "name");
      } else {
        imagesOps.get("westus", osSettings.getPublisher(), osSettings.getOffer(), osSettings.getSku(), version);
      }
    } catch (Exception e) {
      String message =  CloudException.class.isInstance(e) ? CloudException.class.cast(e).getBody().getMessage() : e.getMessage();
      p.addProblem(Problem.Severity.WARNING, "Error getting image '" + n.getNodeName() + "' in region 'westus': " + message)
        .setRemediation("If you are not targeting 'westus' and know the image is available in other regions, you can ignore this warning and select a different region when baking images. See available images here: https://aka.ms/azspinimage");
    }
  }
}
