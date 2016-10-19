/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.netflix.spinnaker.halyard.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.model.v1.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.model.v1.Halconfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/config/deployments")
public class DeploymentController {
  @Autowired
  HalconfigParser halyardConfig;

  @RequestMapping(value = "/{deployment:.+}", method = RequestMethod.GET)
  DeploymentConfiguration deploymentConfigurations(@PathVariable String deployment) throws UnrecognizedPropertyException {
    Halconfig halconfig = halyardConfig.getConfig();
    if (halconfig != null) {
      List<DeploymentConfiguration> matching = halconfig.getDeploymentConfigurations()
          .stream()
          .filter(d -> d.getName().equals(deployment))
          .collect(Collectors.toList());

      if (matching.size() == 0) {
        throw new DeploymentNotFoundExecption(deployment);
      } else if (matching.size() > 1) {
        throw new DuplicateDeploymentException(deployment);
      } else {
        return matching.get(0);
      }
    } else {
      return null;
    }
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  List<DeploymentConfiguration> deploymentConfigurations() throws UnrecognizedPropertyException {
    Halconfig halconfig = halyardConfig.getConfig();
    if (halconfig != null) {
      return halconfig.getDeploymentConfigurations();
    } else {
      return null;
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  @ResponseStatus(value = HttpStatus.NOT_FOUND)
  static public class DeploymentNotFoundExecption extends RuntimeException {
    String deploymentName;

    DeploymentNotFoundExecption(String deploymentName) {
      this.deploymentName = deploymentName;
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  @ResponseStatus(value = HttpStatus.CONFLICT)
  static public class DuplicateDeploymentException extends RuntimeException {
    String deploymentName;

    DuplicateDeploymentException(String deploymentName) {
      this.deploymentName = deploymentName;
    }
  }
}
