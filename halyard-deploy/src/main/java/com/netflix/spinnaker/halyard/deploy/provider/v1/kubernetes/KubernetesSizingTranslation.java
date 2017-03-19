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

package com.netflix.spinnaker.halyard.deploy.provider.v1.kubernetes;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment;
import com.netflix.spinnaker.halyard.deploy.provider.v1.SizingTranslation;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import org.springframework.stereotype.Component;

@Component
public class KubernetesSizingTranslation extends SizingTranslation {
  @Override
  public ServiceSize getServiceSize(DeploymentEnvironment.Size size, SpinnakerService service) {
    // todo(lwander) these are completely made up, we need sizing recommendations.
    String cpu;
    int ramMi;
    switch (size) {
      case LARGE:
        cpu = "2";
        ramMi = 2048;
        break;
      case MEDIUM:
        cpu = "1";
        ramMi = 1024;
        break;
      case SMALL:
        cpu = "250m";
        ramMi = 256;
        break;
      default:
        throw new RuntimeException("Unknown service size " + size);
    }

    if (service.getArtifact() == SpinnakerArtifact.REDIS) {
      ramMi *= 2;
    }

    return new ServiceSize().setCpu(cpu).setRam(ramMi + "Mi");
  }
}
