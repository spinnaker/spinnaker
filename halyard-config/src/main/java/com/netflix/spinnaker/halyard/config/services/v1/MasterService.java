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
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import com.netflix.spinnaker.halyard.config.model.v1.node.Master;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfig's
 * masters.
 */
@Component
public class MasterService {
  @Autowired
  private LookupService lookupService;

  @Autowired
  private CiService ciService;

  @Autowired
  private ValidateService validateService;

  public List<Master> getAllMasters(String deploymentName, String ciName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName).withAnyMaster();

    List<Master> matchingMasters = lookupService.getMatchingNodesOfType(filter, Master.class);

    if (matchingMasters.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No masters could be found").build());
    } else {
      return matchingMasters;
    }
  }

  private Master getMaster(NodeFilter filter, String masterName) {
    List<Master> matchingMasters = lookupService.getMatchingNodesOfType(filter, Master.class);

    switch (matchingMasters.size()) {
      case 0:
        throw new ConfigNotFoundException(new ConfigProblemBuilder(
            Severity.FATAL, "No master with name \"" + masterName + "\" was found")
            .setRemediation("Check if this master was defined in another Continuous Integration service, or create a new one").build());
      case 1:
        return matchingMasters.get(0);
      default:
        throw new IllegalConfigException(new ConfigProblemBuilder(
            Severity.FATAL, "More than one master named \"" + masterName + "\" was found")
            .setRemediation("Manually delete/rename duplicate masters with name \"" + masterName + "\" in your halconfig file").build());
    }
  }

  public Master getCiMaster(String deploymentName, String ciName, String masterName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName).setMaster(masterName);
    return getMaster(filter, masterName);
  }

  public Master getAnyCiMaster(String deploymentName, String masterName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyCi().setMaster(masterName);
    return getMaster(filter, masterName);
  }

  public void setMaster(String deploymentName, String ciName, String masterName, Master newMaster) {
    Ci ci = ciService.getCi(deploymentName, ciName);

    for (int i = 0; i < ci.getMasters().size(); i++) {
      Master master = (Master) ci.getMasters().get(i);
      if (master.getNodeName().equals(masterName)) {
        ci.getMasters().set(i, newMaster);
        return;
      }
    }

    throw new HalException(new ConfigProblemBuilder(Severity.FATAL, "Master \"" + masterName + "\" wasn't found").build());
  }

  public void deleteMaster(String deploymentName, String ciName, String masterName) {
    Ci ci = ciService.getCi(deploymentName, ciName);
    boolean removed = ci.getMasters().removeIf(master -> ((Master) master).getName().equals(masterName));

    if (!removed) {
      throw new HalException(
          new ConfigProblemBuilder(Severity.FATAL, "Master \"" + masterName + "\" wasn't found")
              .build());
    }
  }

  public void addMaster(String deploymentName, String ciName, Master newMaster) {
    Ci ci = ciService.getCi(deploymentName, ciName);
    ci.getMasters().add(newMaster);
  }

  public ProblemSet validateMaster(String deploymentName, String ciName, String masterName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName).setMaster(masterName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllMasters(String deploymentName, String ciName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setCi(ciName).withAnyMaster();
    return validateService.validateMatchingFilter(filter);
  }
}
