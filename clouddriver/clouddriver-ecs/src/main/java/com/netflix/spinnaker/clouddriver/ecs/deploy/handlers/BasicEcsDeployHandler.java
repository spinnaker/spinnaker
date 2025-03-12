/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.handlers;

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.BasicEcsDeployDescription;
import java.util.List;

public class BasicEcsDeployHandler implements DeployHandler<BasicEcsDeployDescription> {

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof BasicEcsDeployDescription;
  }

  @Override
  public DeploymentResult handle(BasicEcsDeployDescription description, List priorOutputs) {

    // TODO - Implement this stub

    return new DeploymentResult();
  }
}
